package com.konohahelpdesk.service.ticket;

import com.konohahelpdesk.config.AppProperties;
import com.konohahelpdesk.dto.subscription.SlaDto;
import com.konohahelpdesk.dto.ticket.CommentRequest;
import com.konohahelpdesk.dto.ticket.CreateTicketRequest;
import com.konohahelpdesk.dto.ticket.TicketResponse;
import com.konohahelpdesk.dto.user.UserResponse;
import com.konohahelpdesk.exception.BusinessRuleException;
import com.konohahelpdesk.exception.LimitExceededException;
import com.konohahelpdesk.exception.ResourceNotFoundException;
import com.konohahelpdesk.mail.MailService;
import com.konohahelpdesk.model.company.Company;
import com.konohahelpdesk.model.media.MediaFile;
import com.konohahelpdesk.model.media.MediaKind;
import com.konohahelpdesk.model.notification.NotificationType;
import com.konohahelpdesk.model.ticket.Ticket;
import com.konohahelpdesk.model.ticket.TicketCategory;
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
import com.konohahelpdesk.service.company.BusinessCalendar;
import com.konohahelpdesk.service.company.EngineerAssignmentService;
import com.konohahelpdesk.service.media.MediaStorageService;
import com.konohahelpdesk.service.notification.NotificationService;
import com.konohahelpdesk.service.subscription.CompanySubscriptionService;
import com.konohahelpdesk.service.subscription.QuotaAlertService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
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

    private final TicketRepository tickets;
    private final TicketCommentRepository comments;
    private final CompanyRepository companies;
    private final UserRepository users;
    private final CompanySubscriptionService subscriptions;
    private final QuotaAlertService quotaAlerts;
    private final SlaCalculator slaCalculator;
    private final BusinessCalendar calendar;
    private final MediaStorageService media;
    private final EngineerAssignmentService assignments;
    private final NotificationService notifications;
    private final MailService mail;
    private final AuditLogger audit;
    private final AppProperties.Media mediaCfg;

    public TicketService(TicketRepository tickets, TicketCommentRepository comments, CompanyRepository companies,
                         UserRepository users, CompanySubscriptionService subscriptions, QuotaAlertService quotaAlerts,
                         SlaCalculator slaCalculator, BusinessCalendar calendar, MediaStorageService media,
                         EngineerAssignmentService assignments, NotificationService notifications, MailService mail,
                         AuditLogger audit, AppProperties props) {
        this.tickets = tickets;
        this.comments = comments;
        this.companies = companies;
        this.users = users;
        this.subscriptions = subscriptions;
        this.quotaAlerts = quotaAlerts;
        this.slaCalculator = slaCalculator;
        this.calendar = calendar;
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
        List<Long> ids = assignments.companyIdsForEngineer(actor.getId());
        return ids.isEmpty() ? null : ids;
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TicketResponse> list(User actor, Long companyId, TicketStatus status, TicketPriority priority, Long assigneeId, String q) {
        // A customer account with no company has no tickets to see (and used to be a 500 here).
        if (!isStaff(actor) && actor.getCompany() == null) return List.of();
        Long scopedCompany = isStaff(actor) ? companyId : actor.getCompany().getId();
        List<Long> engineerScope = engineerScope(actor);
        String query = (q == null || q.isBlank()) ? null : q.trim().replaceFirst("^#", "").toLowerCase();

        Specification<Ticket> spec = (root, cq, cb) -> {
            // Company, creator and assignee are named on every row: fetch them in the same SELECT
            // instead of one query per distinct company/user. Not on the paging count query.
            if (cq != null && cq.getResultType() == Ticket.class) {
                root.fetch("company", jakarta.persistence.criteria.JoinType.INNER);
                root.fetch("createdBy", jakarta.persistence.criteria.JoinType.INNER);
                root.fetch("assignee", jakarta.persistence.criteria.JoinType.LEFT);
            }
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
        List<Ticket> page = tickets.findAll(spec, PageRequest.of(0, LIST_CAP, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
        if (page.isEmpty()) return List.of();

        // One grouped query for the whole page instead of one per row — see countPublicByTicketIds.
        Map<Long, Long> commentCounts = new java.util.HashMap<>();
        for (Object[] row : comments.countPublicByTicketIds(page.stream().map(Ticket::getId).toList())) {
            commentCounts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }

        return page.stream()
            .map(t -> withSla(t, TicketResponse.summary(t, commentCounts.getOrDefault(t.getId(), 0L))))
            .toList();
    }

    /**
     * Open-ticket counts for the sidebar badges, scoped exactly like {@link #list}.
     *
     * A customer only ever sees their own company, so their map has at most one entry; an engineer
     * restricted to specific companies sees only those; an admin (and an unrestricted engineer)
     * sees every company. The total is derived from the same map, so the badge and the per-company
     * numbers can never disagree.
     */
    @Transactional(readOnly = true)
    public Map<Long, Long> openCountsByCompany(User actor) {
        List<Object[]> rows;
        if (!isStaff(actor)) {
            Company own = actor.getCompany();
            if (own == null) return Map.of();
            rows = tickets.countOpenByCompany(List.of(own.getId()));
        } else {
            List<Long> scope = engineerScope(actor);
            rows = scope == null ? tickets.countOpenByCompanyAll() : tickets.countOpenByCompany(scope);
        }
        Map<Long, Long> out = new java.util.LinkedHashMap<>();
        for (Object[] row : rows) out.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        return out;
    }

    @Transactional(readOnly = true)
    public TicketResponse get(User actor, Long id) {
        Ticket t = loadAccessible(actor, id);
        List<TicketComment> visible = isStaff(actor)
            ? comments.findByTicketIdOrderByCreatedAtAsc(id)
            : comments.findByTicketIdAndInternalFalseOrderByCreatedAtAsc(id);
        return withSla(t, TicketResponse.full(t, visible, media.listFor(MediaKind.TICKET_ATTACHMENT, id)));
    }

    /** Used by MediaController to gate attachment downloads. */
    public boolean canAccess(User actor, Long ticketId) {
        return tickets.findById(ticketId).map(t -> hasAccess(actor, t)).orElse(false);
    }

    /** Staff who can be assigned tickets (engineers + admins). */
    @Transactional(readOnly = true)
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
        try {
            subscriptions.assertTicketQuotaAvailable(company.getId(), tickets.countByCompanyIdAndCreatedAtGreaterThanEqual(company.getId(), monthStart));
        } catch (LimitExceededException e) {
            // Warn staff before the rejection propagates — this transaction is about to roll back,
            // so the alert has to be committed and sent from its own (see QuotaAlertService).
            quotaAlerts.ticketQuotaReached(company.getId(),
                subscriptions.getResolvedLimits(company.getId()).getMonthlyTicketLimit(),
                actor.getFirstName() + " " + actor.getLastName() + " <" + actor.getEmail() + ">");
            throw e;
        }

        Ticket t = new Ticket();
        t.setTitle(req.getTitle().trim());
        t.setDescription(req.getDescription().trim());
        t.setStepsToReproduce(req.getStepsToReproduce() == null || req.getStepsToReproduce().isBlank() ? null : req.getStepsToReproduce().trim());
        t.setCategory(req.getCategory());
        t.setPriority(req.getPriority());
        t.setCompany(company);
        t.setCreatedBy(actor);
        startSlaClock(t);
        Ticket saved = tickets.save(t);

        system(saved, "Ticket created by " + actor.getFirstName() + " " + actor.getLastName() + ".");
        audit.event("TICKET_CREATED", "#" + saved.getId() + " · " + company.getName() + " · " + saved.getPriority());

        User mainEngineer = activeOrNull(company.getMainEngineer());
        if (mainEngineer != null) {
            notifications.notifyUnlessSelf(mainEngineer, actor, NotificationType.TICKET_CREATED,
                "New ticket for " + company.getName(),
                "A new ticket was created for " + company.getName() + ": #" + saved.getId() + " \"" + saved.getTitle() + "\".",
                saved.getId());
            mailUnlessSelf(mainEngineer, actor, true,
                "New ticket #" + saved.getId() + " — " + company.getName() + " — " + mail.appName(),
                "Hi " + mainEngineer.getFirstName() + ",\n\n"
                + "A new ticket has been raised for " + company.getName() + ", where you are the main engineer.\n\n"
                + "#" + saved.getId() + " — " + saved.getTitle() + "\n"
                + "Priority: " + label(saved.getPriority()) + "\n"
                + "Category: " + saved.getCategory() + "\n"
                + "Raised by: " + actor.getFirstName() + " " + actor.getLastName() + "\n"
                + due("First response due", saved.getResponseDueAt())
                + due("Resolution due", saved.getResolutionDueAt())
                + "\n" + saved.getDescription() + "\n\n"
                + "Open the ticket here:\n" + mail.ticketLink(saved.getId()) + "\n");
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

    /**
     * Raises the maintenance ticket for a scheduled contract visit. Called only by the contract
     * sweep, never from a request.
     *
     * Two deliberate differences from {@link #create}:
     *  - The monthly ticket quota is NOT applied. The quota exists to price customer demand; a
     *    preventive-maintenance visit is work the provider already sold and committed to, and
     *    silently not raising it because the customer filed a lot of tickets that month would
     *    turn a billing limit into a missed site visit. The TICKET_MANAGEMENT feature gate still
     *    applies — a company not on the ticketing product has nowhere for the ticket to live.
     *  - It returns the entity, not a response DTO: the caller links it to the visit row.
     *
     * SLA deadlines are stamped through the same {@code applySla} as any other ticket, so the
     * breach sweep treats a maintenance ticket exactly like a reported one.
     */
    @Transactional
    public Ticket createForContract(Company company, User raisedBy, String title, String description,
                                    TicketCategory category, TicketPriority priority) {
        subscriptions.requireFeature(company.getId(), "TICKET_MANAGEMENT");

        Ticket t = new Ticket();
        t.setTitle(title);
        t.setDescription(description);
        t.setCategory(category);
        t.setPriority(priority);
        t.setCompany(company);
        t.setCreatedBy(raisedBy);
        // Pre-assigned to the company's main engineer when there is one — a scheduled visit that
        // lands in an unassigned queue is the thing this feature exists to prevent.
        User mainEngineer = activeOrNull(company.getMainEngineer());
        if (mainEngineer != null) t.setAssignee(mainEngineer);
        startSlaClock(t);
        Ticket saved = tickets.save(t);

        system(saved, "Raised automatically from a scheduled service contract visit.");
        audit.event("TICKET_CREATED_FROM_CONTRACT", "ticketId=" + saved.getId() + " companyId=" + company.getId());

        if (mainEngineer != null) {
            notifications.notify(mainEngineer, NotificationType.TICKET_CREATED,
                "Scheduled maintenance due — " + company.getName(),
                "#" + saved.getId() + " \"" + saved.getTitle() + "\" was raised from a service contract.",
                saved.getId());
            mailUnlessSelf(mainEngineer, raisedBy, true,
                "Scheduled maintenance #" + saved.getId() + " — " + company.getName() + " — " + mail.appName(),
                "Hi " + mainEngineer.getFirstName() + ",\n\n"
                + "A scheduled service contract visit is coming up and a ticket has been raised for it.\n\n"
                + "#" + saved.getId() + " — " + saved.getTitle() + "\n"
                + "Company: " + company.getName() + "\n"
                + "Priority: " + label(saved.getPriority()) + "\n"
                + due("Resolution due", saved.getResolutionDueAt())
                + "\n" + saved.getDescription() + "\n\n"
                + "Open the ticket here:\n" + mail.ticketLink(saved.getId()) + "\n");
        }
        return saved;
    }

    /** Starts (or, on a reopen, restarts) the SLA clock now and stamps both deadlines from it. */
    private void startSlaClock(Ticket t) {
        LocalDateTime now = LocalDateTime.now();
        t.setSlaStartedAt(now);
        applySla(t, now);
    }

    /**
     * Stamps the response / resolution deadlines for the ticket's current priority, counted from
     * {@code start}. A plan with no SLA row for that priority leaves the ticket without deadlines,
     * rather than with the ones of whatever priority it had before.
     */
    private void applySla(Ticket t, LocalDateTime start) {
        Optional<SlaDto> sla = subscriptions.getResolvedSla(t.getCompany().getId()).stream()
            .filter(s -> s.getPriority() == t.getPriority().slaPriority()).findFirst();
        Long companyId = t.getCompany().getId();
        if (sla.isEmpty()) {
            t.setResponseDueAt(null);
            t.setResolutionDueAt(null);
            return;
        }
        // Working minutes, not wall-clock: a Friday-evening ticket is due on Monday morning.
        t.setResponseDueAt(calendar.addWorkingMinutes(companyId, start, sla.get().getResponseTimeMinutes()));
        t.setResolutionDueAt(calendar.addWorkingMinutes(companyId, start, sla.get().getResolutionTimeMinutes()));
    }

    private static User activeOrNull(User u) {
        return u != null && u.getStatus() == UserStatus.ACTIVE ? u : null;
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
        // Reopen window: once a ticket has sat resolved/closed for more than 7 days, it's locked —
        // applies to staff and customers alike, so old tickets don't get resurrected indefinitely.
        boolean reopening = (cur == TicketStatus.RESOLVED || cur == TicketStatus.CLOSED)
            && (next == TicketStatus.OPEN || next == TicketStatus.IN_PROGRESS);
        if (reopening) {
            LocalDateTime terminalAt = cur == TicketStatus.CLOSED ? t.getClosedAt() : t.getResolvedAt();
            if (terminalAt != null && terminalAt.isBefore(LocalDateTime.now().minusDays(7))) {
                throw new BusinessRuleException("This ticket was " + (cur == TicketStatus.CLOSED ? "closed" : "resolved")
                    + " more than 7 days ago and can no longer be reopened");
            }
        }

        t.setStatus(next);
        if (next == TicketStatus.RESOLVED) t.setResolvedAt(LocalDateTime.now());
        if (next == TicketStatus.CLOSED) t.setClosedAt(LocalDateTime.now());
        if (next == TicketStatus.OPEN || next == TicketStatus.IN_PROGRESS) { t.setResolvedAt(null); t.setClosedAt(null); }

        if (reopening) {
            // Reopening starts a NEW clock, and it has to, because the old one is already spent:
            // clearing resolvedAt without re-stamping left the ticket permanently past a deadline
            // set weeks ago, so the SLA badge read BREACHED for ever no matter how fast it was
            // then handled. The breach stamps go with it — they exist only to escalate a breach
            // once, and this is a new engagement, not the old one still running.
            //
            // The trade-off is explicit: a reopen does NOT count as having missed the original
            // resolution SLA. If you want the opposite, drop this block and the ticket keeps its
            // old deadlines — but then re-stamp them somewhere, or every reopen reads as breached.
            t.setResponseBreachedAt(null);
            t.setResolutionBreachedAt(null);
            t.setFirstResponseAt(null);
            startSlaClock(t);
        }
        tickets.save(t);

        system(t, actor.getFirstName() + " changed status from " + label(cur) + " to " + label(next) + ".");
        if (reopening) system(t, "Reopened — SLA deadlines restarted from now.");
        audit.event("TICKET_STATUS", "#" + id + " · " + cur + " -> " + next
            + (reopening ? " · SLA restarted" : ""));

        if (next == TicketStatus.RESOLVED) {
            notifications.notifyUnlessSelf(t.getCreatedBy(), actor, NotificationType.TICKET_RESOLVED,
                "Ticket resolved", "Ticket #" + t.getId() + " \"" + t.getTitle() + "\" has been marked resolved.", t.getId());
        } else if (next == TicketStatus.CLOSED) {
            notifications.notifyUnlessSelf(t.getCreatedBy(), actor, NotificationType.TICKET_CLOSED,
                "Ticket closed", "Ticket #" + t.getId() + " \"" + t.getTitle() + "\" has been closed.", t.getId());
        }
        return get(actor, id);
    }

    /**
     * Staff-only priority change. Re-stamps the SLA due dates from the new priority, because a
     * ticket escalated to Critical should be judged against the Critical window, not the one it
     * was created under. Any breach already recorded stays — history isn't rewritten — but a
     * ticket that is now inside its new window stops being counted as currently breaching.
     */
    @Transactional
    public TicketResponse setPriority(User actor, Long id, TicketPriority next) {
        requireStaff(actor);
        Ticket t = loadAccessible(actor, id);
        TicketPriority cur = t.getPriority();
        if (cur == next) return get(actor, id);

        t.setPriority(next);
        // Same clock, new window: the deadlines are re-counted from when this SLA clock started
        // (creation, or the last reopen) — not from now, which handed every priority change a
        // fresh window and made the two checks below clear any recorded breach unconditionally.
        applySla(t, SlaCalculator.slaStart(t));
        if (t.getResponseDueAt() != null && t.getFirstResponseAt() == null
            && !LocalDateTime.now().isAfter(t.getResponseDueAt())) {
            t.setResponseBreachedAt(null);
        }
        if (t.getResolutionDueAt() != null && SlaCalculator.isActive(t)
            && !LocalDateTime.now().isAfter(t.getResolutionDueAt())) {
            t.setResolutionBreachedAt(null);
        }
        tickets.save(t);

        system(t, actor.getFirstName() + " changed priority from " + label(cur) + " to " + label(next) + ".");
        audit.event("TICKET_PRIORITY", "#" + id + " · " + cur + " -> " + next);
        return get(actor, id);
    }

    @Transactional
    public TicketResponse assign(User actor, Long id, Long assigneeId) {
        requireStaff(actor);
        Ticket t = loadAccessible(actor, id);
        if (assigneeId == null) {
            t.setAssignee(null);
            system(t, actor.getFirstName() + " removed the assignee.");
        } else {
            User engineer = users.findByIdAndStatusNot(assigneeId, UserStatus.IS_DELETED)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + assigneeId));
            if (!engineer.getRole().worksTickets() || engineer.getStatus() != UserStatus.ACTIVE) {
                throw new BusinessRuleException("Only active engineers and admins can be assigned tickets");
            }
            if (!SlaCalculator.isActive(t)) {
                throw new BusinessRuleException("This ticket is " + label(t.getStatus()).toLowerCase(java.util.Locale.ROOT)
                    + ". Reopen it before assigning it to someone.");
            }
            // An engineer restricted to specific companies couldn't even open a ticket outside them.
            if (!hasAccess(engineer, t)) {
                throw new BusinessRuleException(engineer.getFirstName() + " " + engineer.getLastName()
                    + " is not assigned to " + t.getCompany().getName() + " and could not open this ticket");
            }
            t.setAssignee(engineer);
            if (t.getStatus() == TicketStatus.OPEN) t.setStatus(TicketStatus.IN_PROGRESS);
            system(t, "Assigned to " + engineer.getFirstName() + " " + engineer.getLastName() + ".");
        }
        tickets.save(t);
        audit.event("TICKET_ASSIGNED", "#" + id + " · to "
            + (t.getAssignee() == null ? "nobody (unassigned)" : AuditLogger.describe(t.getAssignee())));

        if (assigneeId != null) {
            notifications.notifyUnlessSelf(t.getAssignee(), actor, NotificationType.TICKET_ASSIGNED_TO_YOU,
                "New ticket assigned", "You've been assigned ticket #" + t.getId() + " \"" + t.getTitle() + "\".", t.getId());
            notifications.notifyUnlessSelf(t.getCreatedBy(), actor, NotificationType.TICKET_ASSIGNED_NOTICE,
                "Engineer assigned", t.getAssignee().getFirstName() + " " + t.getAssignee().getLastName()
                    + " has been assigned to ticket #" + t.getId() + " \"" + t.getTitle() + "\".", t.getId());

            mailUnlessSelf(t.getAssignee(), actor, t.getAssignee().isNotifyOnAssign(),
                "Ticket #" + t.getId() + " assigned to you — " + mail.appName(),
                "Hi " + t.getAssignee().getFirstName() + ",\n\n"
                + "You've been assigned a ticket for " + t.getCompany().getName() + ".\n\n"
                + "#" + t.getId() + " — " + t.getTitle() + "\n"
                + "Priority: " + label(t.getPriority()) + "\n"
                + "Status: " + label(t.getStatus()) + "\n"
                + due("First response due", t.getResponseDueAt())
                + due("Resolution due", t.getResolutionDueAt())
                + "\nOpen the ticket here:\n" + mail.ticketLink(t.getId()) + "\n");

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
            for (User recipient : replyRecipients(actor, t)) {
                notifications.notifyUnlessSelf(recipient, actor, NotificationType.TICKET_REPLY,
                    "New reply", actor.getFirstName() + " replied to ticket #" + t.getId() + " \"" + t.getTitle() + "\".", t.getId());
            }

            // A customer replying is the case an engineer can't afford to miss — mail it.
            // (Staff replies already reach the customer through the assignment/resolution mails.)
            if (!isStaff(actor)) {
                User engineer = t.getAssignee() != null ? t.getAssignee() : activeOrNull(t.getCompany().getMainEngineer());
                mailUnlessSelf(engineer, actor, engineer != null && engineer.isNotifyOnReply(),
                    "New reply on ticket #" + t.getId() + " — " + mail.appName(),
                    "Hi " + (engineer == null ? "" : engineer.getFirstName()) + ",\n\n"
                    + actor.getFirstName() + " " + actor.getLastName() + " (" + t.getCompany().getName()
                    + ") replied to:\n\n"
                    + "#" + t.getId() + " — " + t.getTitle() + "\n\n"
                    + c.getBody() + "\n\n"
                    + "Reply here:\n" + mail.ticketLink(t.getId()) + "\n");
            }
        }
        return get(actor, id);
    }

    /**
     * Who gets the bell for a public reply.
     *
     * A customer's reply goes to the assignee — or, while nobody is assigned yet, to the company's
     * main engineer, the same person the email already went to (the bell used to go to nobody).
     *
     * A staff reply goes to whoever raised the ticket. When staff raised it on the company's
     * behalf there is no customer in that seat, so the company's admins are told instead;
     * otherwise a public reply on such a ticket reached no customer at all.
     */
    private List<User> replyRecipients(User actor, Ticket t) {
        if (!isStaff(actor)) {
            User engineer = t.getAssignee() != null ? t.getAssignee() : activeOrNull(t.getCompany().getMainEngineer());
            return engineer == null ? List.of() : List.of(engineer);
        }
        User raisedBy = t.getCreatedBy();
        if (raisedBy != null && !isStaff(raisedBy)) return List.of(raisedBy);
        return users.findByRoleAndStatusNotAndCompanyIdIn(Roles.CUSTOMER_ADMIN, UserStatus.IS_DELETED, List.of(t.getCompany().getId()))
            .stream().filter(u -> u.getStatus() == UserStatus.ACTIVE).toList();
    }

    @Transactional
    public TicketResponse attach(User actor, Long id, MultipartFile file) {
        Ticket t = loadAccessible(actor, id);
        if (t.getStatus() == TicketStatus.CLOSED) throw new BusinessRuleException("This ticket is closed.");
        if (media.listFor(MediaKind.TICKET_ATTACHMENT, id).size() >= mediaCfg.getMaxAttachmentsPerTicket()) {
            throw new BusinessRuleException("This ticket already has the maximum of " + mediaCfg.getMaxAttachmentsPerTicket() + " attachments");
        }
        Long companyId = t.getCompany().getId();
        subscriptions.assertStorageAvailable(companyId, media.usedBytesForCompany(companyId), file.getSize());
        MediaFile stored = media.storeAttachment(file, id, actor.getId());
        system(t, actor.getFirstName() + " attached " + stored.getOriginalName() + ".");
        return get(actor, id);
    }

    @Transactional
    public void removeAttachment(User actor, Long id, String mediaId) {
        Ticket t = loadAccessible(actor, id);
        if (t.getStatus() == TicketStatus.CLOSED) throw new BusinessRuleException("This ticket is closed.");
        MediaFile m = media.get(mediaId);
        if (m.getKind() != MediaKind.TICKET_ATTACHMENT || !m.getOwnerId().equals(t.getId())) {
            throw new ResourceNotFoundException("File not found");
        }
        if (!isStaff(actor) && !actor.getId().equals(m.getUploadedBy())) {
            throw new AccessDeniedException("Forbidden");
        }
        media.delete(mediaId);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Every ticket leaving this service carries its SLA standing, so no caller can forget to ask. */
    private TicketResponse withSla(Ticket t, TicketResponse r) {
        r.applySla(slaCalculator.responseState(t), slaCalculator.resolutionState(t));
        return r;
    }

    private void system(Ticket t, String text) {
        TicketComment c = new TicketComment();
        c.setTicket(t);
        c.setBody(text);
        c.setSystem(true);
        comments.save(c);
    }

    /**
     * Email a recipient, unless they are the person who caused the event, have muted email, opted out
     * of this event type, or aren't an active account. Delivery itself is async and after-commit
     * (see MailService), so a failure here never breaks the request.
     */
    private void mailUnlessSelf(User to, User actor, boolean optedIn, String subject, String body) {
        if (to == null || actor == null || to.getId().equals(actor.getId())) return;
        if (!optedIn || !to.isNotifyEmail()) return;
        if (to.getStatus() != UserStatus.ACTIVE) return;
        if (to.getEmail() == null || to.getEmail().isBlank()) return;
        mail.send(to.getEmail(), subject, body);
    }

    private static final DateTimeFormatter DUE_FMT = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm");

    /** One optional "Label: date" line for an email body; empty when the plan sets no SLA for it. */
    private static String due(String labelText, LocalDateTime at) {
        return at == null ? "" : labelText + ": " + DUE_FMT.format(at) + "\n";
    }

    private static String label(TicketPriority p) {
        return switch (p) { case CRITICAL -> "Critical"; case HIGH -> "High"; case MEDIUM -> "Medium"; case LOW -> "Low"; };
    }

    private static String label(TicketStatus s) {
        return switch (s) { case OPEN -> "Open"; case IN_PROGRESS -> "In Progress"; case RESOLVED -> "Resolved"; case CLOSED -> "Closed"; };
    }

    private static boolean isStaff(User u) {
        return u.getRole().worksTickets();
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

}
