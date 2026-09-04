package com.konohahelpdesk.service.ticket;

import com.konohahelpdesk.config.AppProperties;
import com.konohahelpdesk.dto.subscription.SlaDto;
import com.konohahelpdesk.dto.ticket.CommentRequest;
import com.konohahelpdesk.dto.ticket.CreateTicketRequest;
import com.konohahelpdesk.dto.ticket.TicketResponse;
import com.konohahelpdesk.dto.user.UserResponse;
import com.konohahelpdesk.exception.BusinessRuleException;
import com.konohahelpdesk.exception.ResourceNotFoundException;
import com.konohahelpdesk.mail.MailService;
import com.konohahelpdesk.model.company.Company;
import com.konohahelpdesk.model.media.MediaFile;
import com.konohahelpdesk.model.media.MediaKind;
import com.konohahelpdesk.model.notification.NotificationType;
import com.konohahelpdesk.model.ticket.Ticket;
import com.konohahelpdesk.model.ticket.TicketComment;
import com.konohahelpdesk.model.ticket.TicketPriority;
import com.konohahelpdesk.model.ticket.TicketStatus;
import com.konohahelpdesk.model.user.Roles;
import com.konohahelpdesk.model.user.User;
import com.konohahelpdesk.model.user.UserStatus;
import com.konohahelpdesk.repository.company.CompanyRepository;
import com.konohahelpdesk.repository.ticket.TicketCommentRepository;
import com.konohahelpdesk.repository.ticket.TicketRepository;
import com.konohahelpdesk.repository.user.UserRepository;
import com.konohahelpdesk.security.AuditLogger;
import com.konohahelpdesk.service.company.EngineerAssignmentService;
import com.konohahelpdesk.service.media.MediaStorageService;
import com.konohahelpdesk.service.notification.NotificationService;
import com.konohahelpdesk.service.subscription.CompanySubscriptionService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Ticket lifecycle. Every public method takes the acting user and enforces:
 *  - customers only see / act on their own company's tickets
 *  - internal notes never reach customers
 *  - status transitions follow OPEN → IN_PROGRESS → RESOLVED → CLOSED (customers may only close a resolved ticket or reopen it)
 *  - the company's TICKET_MANAGEMENT feature and monthly ticket quota are enforced on creation
 *  - SLA due dates are stamped from the company's subscription at creation
 */
@Service
public class TicketService {

    private static final int LIST_CAP = 500;
    private static final List<TicketStatus> ACTIVE = List.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS);

    private final TicketRepository tickets;
    private final TicketCommentRepository comments;
    private final CompanyRepository companies;
    private final UserRepository users;
    private final CompanySubscriptionService subscriptions;
    private final MediaStorageService media;
    private final EngineerAssignmentService assignments;
    private final NotificationService notifications;
    private final MailService mail;
    private final AuditLogger audit;
    private final AppProperties.Media mediaCfg;

    public TicketService(TicketRepository tickets, TicketCommentRepository comments, CompanyRepository companies,
                         UserRepository users, CompanySubscriptionService subscriptions, MediaStorageService media,
                         EngineerAssignmentService assignments, NotificationService notifications, MailService mail,
                         AuditLogger audit, AppProperties props) {
        this.tickets = tickets;
        this.comments = comments;
        this.companies = companies;
        this.users = users;
        this.subscriptions = subscriptions;
        this.media = media;
        this.assignments = assignments;
        this.notifications = notifications;
        this.mail = mail;
        this.audit = audit;
        this.mediaCfg = props.getMedia();
    }

    /**
     * An engineer's assigned company ids, or null if they're unrestricted (ADMIN,
     * or an ENGINEER with no assignments — "no company chosen" means see everything).
     */
    private List<Long> engineerScope(User actor) {
        if (actor.getRole() != Roles.ENGINEER) return null;
        List<Long> ids = assignments.companiesForEngineer(actor.getId()).stream().map(UserResponse.CompanyRef::id).toList();
        return ids.isEmpty() ? null : ids;
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    public List<TicketResponse> list(User actor, Long companyId, TicketStatus status, TicketPriority priority, Long assigneeId, String q) {
        Long scopedCompany = isStaff(actor) ? companyId : actor.getCompany().getId();
        List<Long> engineerScope = engineerScope(actor);
        String query = (q == null || q.isBlank()) ? null : q.trim().replaceFirst("^#", "").toLowerCase();

        Specification<Ticket> spec = (root, cq, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (scopedCompany != null) {
                predicates.add(cb.equal(root.get("company").get("id"), scopedCompany));
                // An engineer restricted to specific companies can't widen it by passing a foreign companyId.
                if (engineerScope != null && !engineerScope.contains(scopedCompany)) predicates.add(cb.equal(root.get("id"), -1L));
            } else if (engineerScope != null) {
                predicates.add(root.get("company").get("id").in(engineerScope));
            }
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (priority != null) predicates.add(cb.equal(root.get("priority"), priority));
            if (assigneeId != null) predicates.add(cb.equal(root.get("assignee").get("id"), assigneeId));
            if (query != null) {
                var byTitle = cb.like(cb.lower(root.get("title")), "%" + query + "%");
                predicates.add(query.matches("\\d+") ? cb.or(byTitle, cb.equal(root.get("id"), Long.parseLong(query))) : byTitle);
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return tickets.findAll(spec, PageRequest.of(0, LIST_CAP, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent().stream()
            .map(t -> TicketResponse.summary(t, comments.countByTicketIdAndInternalFalseAndSystemFalse(t.getId())))
            .toList();
    }

    public TicketResponse get(User actor, Long id) {
        Ticket t = loadAccessible(actor, id);
        List<TicketComment> visible = isStaff(actor)
            ? comments.findByTicketIdOrderByCreatedAtAsc(id)
            : comments.findByTicketIdAndInternalFalseOrderByCreatedAtAsc(id);
        return TicketResponse.full(t, visible, media.listFor(MediaKind.TICKET_ATTACHMENT, id));
    }

    /** Used by MediaController to gate attachment downloads. */
    public boolean canAccess(User actor, Long ticketId) {
        return tickets.findById(ticketId).map(t -> hasAccess(actor, t)).orElse(false);
    }

    /** Staff who can be assigned tickets (engineers + admins). */
    public List<UserResponse> assignees() {
        return Stream.concat(
                users.findByRoleAndStatus(Roles.ENGINEER, UserStatus.ACTIVE).stream(),
                users.findByRoleAndStatus(Roles.ADMIN, UserStatus.ACTIVE).stream())
            .map(UserResponse::from).toList();
    }

    // ── Create ───────────────────────────────────────────────────────────────

    @Transactional
    public TicketResponse create(User actor, CreateTicketRequest req) {
        Company company;
        if (isStaff(actor)) {
            if (req.getCompanyId() == null) throw new BusinessRuleException("companyId is required");
            company = companies.findById(req.getCompanyId()).orElseThrow(() -> new ResourceNotFoundException("Company not found: " + req.getCompanyId()));
        } else {
            company = actor.getCompany();
            if (company == null) throw new BusinessRuleException("Your account is not linked to a company");
        }

        subscriptions.requireFeature(company.getId(), "TICKET_MANAGEMENT");
        LocalDateTime monthStart = LocalDateTime.now().with(TemporalAdjusters.firstDayOfMonth()).toLocalDate().atStartOfDay();
        subscriptions.assertTicketQuotaAvailable(company.getId(), tickets.countByCompanyIdAndCreatedAtGreaterThanEqual(company.getId(), monthStart));

        Ticket t = new Ticket();
        t.setTitle(req.getTitle().trim());
        t.setDescription(req.getDescription().trim());
        t.setStepsToReproduce(req.getStepsToReproduce() == null || req.getStepsToReproduce().isBlank() ? null : req.getStepsToReproduce().trim());
        t.setCategory(req.getCategory());
        t.setPriority(req.getPriority());
        t.setCompany(company);
        t.setCreatedBy(actor);
        applySla(t);
        Ticket saved = tickets.save(t);

        system(saved, "Ticket created by " + actor.getFirstName() + " " + actor.getLastName() + ".");
        audit.event("TICKET_CREATED", "ticketId=" + saved.getId() + " companyId=" + company.getId() + " by=" + actor.getId());

        if (company.getMainEngineer() != null) {
            notifications.notifyUnlessSelf(company.getMainEngineer(), actor, NotificationType.TICKET_CREATED,
                "New ticket for " + company.getName(),
                "A new ticket was created for " + company.getName() + ": #" + saved.getId() + " \"" + saved.getTitle() + "\".",
                saved.getId());
        }

        // Only when the customer submitted it themselves — a staff member creating one on a
        // company's behalf isn't the customer confirming their own action.
        if (!isStaff(actor)) {
            mail.send(actor.getEmail(),
                "Ticket #" + saved.getId() + " created — " + mail.appName(),
                "Hi " + actor.getFirstName() + ",\n\n"
                + "Your ticket has been created successfully:\n\n"
                + "#" + saved.getId() + " — " + saved.getTitle() + "\n\n"
                + "You can track its progress here:\n" + mail.ticketLink(saved.getId()) + "\n\n"
                + "We'll notify you as soon as there's an update.\n");
        }
        return get(actor, saved.getId());
    }

    private void applySla(Ticket t) {
        Optional<SlaDto> sla = subscriptions.getResolvedSla(t.getCompany().getId()).stream()
            .filter(s -> s.getPriority() == t.getPriority().slaPriority()).findFirst();
        LocalDateTime now = LocalDateTime.now();
        sla.ifPresent(s -> {
            t.setResponseDueAt(now.plusMinutes(s.getResponseTimeMinutes()));
            t.setResolutionDueAt(now.plusMinutes(s.getResolutionTimeMinutes()));
        });
    }

    // ── Update ───────────────────────────────────────────────────────────────

    @Transactional
    public TicketResponse setStatus(User actor, Long id, TicketStatus next) {
        Ticket t = loadAccessible(actor, id);
        TicketStatus cur = t.getStatus();
        if (cur == next) return get(actor, id);

        if (!isStaff(actor)) {
            boolean close = cur == TicketStatus.RESOLVED && next == TicketStatus.CLOSED;
            boolean reopen = (cur == TicketStatus.RESOLVED || cur == TicketStatus.CLOSED) && next == TicketStatus.OPEN;
            if (!close && !reopen) throw new AccessDeniedException("Customers can only close a resolved ticket or reopen it");
        }
        if (cur == TicketStatus.CLOSED && next != TicketStatus.OPEN) {
            throw new BusinessRuleException("A closed ticket can only be reopened");
        }

        t.setStatus(next);
        if (next == TicketStatus.RESOLVED) t.setResolvedAt(LocalDateTime.now());
        if (next == TicketStatus.CLOSED) t.setClosedAt(LocalDateTime.now());
        if (next == TicketStatus.OPEN || next == TicketStatus.IN_PROGRESS) { t.setResolvedAt(null); t.setClosedAt(null); }
        tickets.save(t);

        system(t, actor.getFirstName() + " changed status from " + label(cur) + " to " + label(next) + ".");
        audit.event("TICKET_STATUS", "ticketId=" + id + " " + cur + "->" + next + " by=" + actor.getId());

        if (next == TicketStatus.RESOLVED) {
            notifications.notifyUnlessSelf(t.getCreatedBy(), actor, NotificationType.TICKET_RESOLVED,
                "Ticket resolved", "Ticket #" + t.getId() + " \"" + t.getTitle() + "\" has been marked resolved.", t.getId());
        } else if (next == TicketStatus.CLOSED) {
            notifications.notifyUnlessSelf(t.getCreatedBy(), actor, NotificationType.TICKET_CLOSED,
                "Ticket closed", "Ticket #" + t.getId() + " \"" + t.getTitle() + "\" has been closed.", t.getId());
        }
        return get(actor, id);
    }

    @Transactional
    public TicketResponse assign(User actor, Long id, Long assigneeId) {
        requireStaff(actor);
        Ticket t = load(id);
        if (assigneeId == null) {
            t.setAssignee(null);
            system(t, actor.getFirstName() + " removed the assignee.");
        } else {
            User engineer = users.findByIdAndStatusNot(assigneeId, UserStatus.IS_DELETED)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + assigneeId));
            if (engineer.getRole() != Roles.ENGINEER && engineer.getRole() != Roles.ADMIN) {
                throw new BusinessRuleException("Only engineers and admins can be assigned tickets");
            }
            t.setAssignee(engineer);
            if (t.getStatus() == TicketStatus.OPEN) t.setStatus(TicketStatus.IN_PROGRESS);
            system(t, "Assigned to " + engineer.getFirstName() + " " + engineer.getLastName() + ".");
        }
        tickets.save(t);
        audit.event("TICKET_ASSIGNED", "ticketId=" + id + " assignee=" + assigneeId + " by=" + actor.getId());

        if (assigneeId != null) {
            notifications.notifyUnlessSelf(t.getAssignee(), actor, NotificationType.TICKET_ASSIGNED_TO_YOU,
                "New ticket assigned", "You've been assigned ticket #" + t.getId() + " \"" + t.getTitle() + "\".", t.getId());
            notifications.notifyUnlessSelf(t.getCreatedBy(), actor, NotificationType.TICKET_ASSIGNED_NOTICE,
                "Engineer assigned", t.getAssignee().getFirstName() + " " + t.getAssignee().getLastName()
                    + " has been assigned to ticket #" + t.getId() + " \"" + t.getTitle() + "\".", t.getId());

            if (t.getCreatedBy() != null && !isStaff(t.getCreatedBy())) {
                mail.send(t.getCreatedBy().getEmail(),
                    "An engineer has been assigned to your ticket — " + mail.appName(),
                    "Hi " + t.getCreatedBy().getFirstName() + ",\n\n"
                    + t.getAssignee().getFirstName() + " " + t.getAssignee().getLastName()
                    + " has been assigned to your ticket:\n\n"
                    + "#" + t.getId() + " — " + t.getTitle() + "\n\n"
                    + "View it here:\n" + mail.ticketLink(t.getId()) + "\n");
            }
        }
        return get(actor, id);
    }

    @Transactional
    public TicketResponse comment(User actor, Long id, CommentRequest req) {
        Ticket t = loadAccessible(actor, id);
        if (t.getStatus() == TicketStatus.CLOSED) throw new BusinessRuleException("This ticket is closed. Reopen it to comment.");

        TicketComment c = new TicketComment();
        c.setTicket(t);
        c.setAuthor(actor);
        c.setBody(req.getBody().trim());
        c.setInternal(isStaff(actor) && req.isInternal());
        comments.save(c);

        // First public reply from staff counts as the SLA "first response".
        if (isStaff(actor) && !c.isInternal() && t.getFirstResponseAt() == null) {
            t.setFirstResponseAt(LocalDateTime.now());
            tickets.save(t);
        }

        if (!c.isInternal()) {
            User recipient = isStaff(actor) ? t.getCreatedBy() : t.getAssignee();
            notifications.notifyUnlessSelf(recipient, actor, NotificationType.TICKET_REPLY,
                "New reply", actor.getFirstName() + " replied to ticket #" + t.getId() + " \"" + t.getTitle() + "\".", t.getId());
        }
        return get(actor, id);
    }

    @Transactional
    public TicketResponse attach(User actor, Long id, MultipartFile file) {
        Ticket t = loadAccessible(actor, id);
        if (t.getStatus() == TicketStatus.CLOSED) throw new BusinessRuleException("This ticket is closed.");
        if (media.listFor(MediaKind.TICKET_ATTACHMENT, id).size() >= mediaCfg.getMaxAttachmentsPerTicket()) {
            throw new BusinessRuleException("This ticket already has the maximum of " + mediaCfg.getMaxAttachmentsPerTicket() + " attachments");
        }
        MediaFile stored = media.storeAttachment(file, id, actor.getId());
        system(t, actor.getFirstName() + " attached " + stored.getOriginalName() + ".");
        return get(actor, id);
    }

    @Transactional
    public void removeAttachment(User actor, Long id, String mediaId) {
        Ticket t = loadAccessible(actor, id);
        MediaFile m = media.get(mediaId);
        if (m.getKind() != MediaKind.TICKET_ATTACHMENT || !m.getOwnerId().equals(t.getId())) {
            throw new ResourceNotFoundException("File not found");
        }
        if (!isStaff(actor) && !m.getUploadedBy().equals(actor.getId())) {
            throw new AccessDeniedException("Forbidden");
        }
        media.delete(mediaId);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void system(Ticket t, String text) {
        TicketComment c = new TicketComment();
        c.setTicket(t);
        c.setBody(text);
        c.setSystem(true);
        comments.save(c);
    }

    private static String label(TicketStatus s) {
        return switch (s) { case OPEN -> "Open"; case IN_PROGRESS -> "In Progress"; case RESOLVED -> "Resolved"; case CLOSED -> "Closed"; };
    }

    private static boolean isStaff(User u) {
        return u.getRole() == Roles.ADMIN || u.getRole() == Roles.ENGINEER;
    }

    private static void requireStaff(User u) {
        if (!isStaff(u)) throw new AccessDeniedException("Forbidden");
    }

    private boolean hasAccess(User actor, Ticket t) {
        if (actor.getRole() == Roles.ADMIN) return true;
        if (actor.getRole() == Roles.ENGINEER) {
            List<Long> scope = engineerScope(actor);
            return scope == null || scope.contains(t.getCompany().getId());
        }
        return actor.getCompany() != null && actor.getCompany().getId().equals(t.getCompany().getId());
    }

    private Ticket load(Long id) {
        return tickets.findById(id).orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + id));
    }

    private Ticket loadAccessible(User actor, Long id) {
        Ticket t = load(id);
        if (!hasAccess(actor, t)) throw new ResourceNotFoundException("Ticket not found: " + id); // don't reveal other companies' ids
        return t;
    }

    public long activeCountFor(Long assigneeId) {
        return tickets.countByAssigneeIdAndStatusIn(assigneeId, ACTIVE);
    }
}
