package com.konohahelpdesk.service.ticket;

import com.konohahelpdesk.config.AppProperties;
import com.konohahelpdesk.mail.MailService;
import com.konohahelpdesk.model.company.Company;
import com.konohahelpdesk.model.notification.NotificationType;
import com.konohahelpdesk.model.ticket.Ticket;
import com.konohahelpdesk.model.ticket.TicketComment;
import com.konohahelpdesk.model.ticket.TicketStatus;
import com.konohahelpdesk.model.user.Roles;
import com.konohahelpdesk.model.user.User;
import com.konohahelpdesk.model.user.UserStatus;
import com.konohahelpdesk.repository.ticket.TicketCommentRepository;
import com.konohahelpdesk.repository.ticket.TicketRepository;
import com.konohahelpdesk.repository.user.UserRepository;
import com.konohahelpdesk.security.AuditLogger;
import com.konohahelpdesk.service.notification.NotificationService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Records and escalates SLA breaches, in a real transaction.
 *
 * Deliberately a separate bean from {@link SlaMonitor}: a @Transactional method called from
 * another method of the same class bypasses the Spring proxy and silently runs with no
 * transaction at all. That would matter here — spring.jpa.open-in-view is false, so reading
 * {@code ticket.getCompany().getMainEngineer()} outside a session throws, and MailService's
 * after-commit hook would never fire. Crossing a bean boundary makes the annotation real.
 */
@Service
public class SlaBreachRecorder {

    private static final List<TicketStatus> ACTIVE = List.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm");

    private final TicketRepository tickets;
    private final TicketCommentRepository comments;
    private final UserRepository users;
    private final NotificationService notifications;
    private final MailService mail;
    private final AuditLogger audit;
    private final AppProperties.SlaMonitoring cfg;

    public SlaBreachRecorder(TicketRepository tickets, TicketCommentRepository comments, UserRepository users,
                             NotificationService notifications, MailService mail, AuditLogger audit, AppProperties props) {
        this.tickets = tickets;
        this.comments = comments;
        this.users = users;
        this.notifications = notifications;
        this.mail = mail;
        this.audit = audit;
        this.cfg = props.getSla();
    }

    /** Visible for manual triggering/testing. Returns how many tickets were newly marked. */
    @Transactional
    public int runOnce() {
        LocalDateTime now = LocalDateTime.now();
        List<Ticket> candidates = tickets.findSlaBreachCandidates(ACTIVE, now, PageRequest.of(0, cfg.getBatchSize()));
        int marked = 0;

        for (Ticket t : candidates) {
            boolean responseBreach = t.getResponseDueAt() != null
                && t.getFirstResponseAt() == null
                && t.getResponseBreachedAt() == null
                && now.isAfter(t.getResponseDueAt());
            boolean resolutionBreach = t.getResolutionDueAt() != null
                && t.getResolutionBreachedAt() == null
                && now.isAfter(t.getResolutionDueAt());
            if (!responseBreach && !resolutionBreach) continue;

            if (responseBreach) t.setResponseBreachedAt(now);
            if (resolutionBreach) t.setResolutionBreachedAt(now);
            tickets.save(t);
            marked++;

            String kind = responseBreach && resolutionBreach ? "response and resolution"
                : responseBreach ? "first response" : "resolution";
            LocalDateTime due = responseBreach ? t.getResponseDueAt() : t.getResolutionDueAt();

            systemNote(t, "SLA breached — the " + kind + " target (" + STAMP.format(due) + ") passed.");
            audit.event("SLA_BREACH", "ticketId=" + t.getId() + " kind=" + kind
                + " companyId=" + t.getCompany().getId() + " priority=" + t.getPriority());
            escalate(t, kind, due, now);
        }
        return marked;
    }

    /** Notifies the assignee, the company's main engineer, and — for the configured priorities — admins. */
    private void escalate(Ticket t, String kind, LocalDateTime due, LocalDateTime now) {
        String overdueBy = humanDuration(Duration.between(due, now));
        String title = "SLA breached on ticket #" + t.getId();
        String body = "Ticket #" + t.getId() + " \"" + t.getTitle() + "\" has missed its " + kind
            + " target by " + overdueBy + ".";

        String subject = "[SLA BREACH] #" + t.getId() + " — " + t.getCompany().getName() + " — " + mail.appName();
        String mailBody = "This ticket has missed its " + kind + " target.\n\n"
            + "#" + t.getId() + " — " + t.getTitle() + "\n"
            + "Company: " + t.getCompany().getName() + "\n"
            + "Priority: " + t.getPriority() + "\n"
            + "Status: " + t.getStatus() + "\n"
            + "Target was: " + STAMP.format(due) + "\n"
            + "Overdue by: " + overdueBy + "\n"
            + "Assigned to: " + (t.getAssignee() == null
                ? "nobody — this ticket is still unassigned"
                : t.getAssignee().getFirstName() + " " + t.getAssignee().getLastName()) + "\n\n"
            + "Open the ticket here:\n" + mail.ticketLink(t.getId()) + "\n";

        for (User recipient : recipients(t)) {
            notifications.notify(recipient, NotificationType.SLA_BREACH, title, body, t.getId());
            if (recipient.isNotifyEmail() && recipient.getEmail() != null && !recipient.getEmail().isBlank()) {
                mail.send(recipient.getEmail(), subject, mailBody);
            }
        }
    }

    /**
     * Assignee + main engineer + (for the configured priorities) all active admins, de-duplicated
     * so someone who is both the assignee and an admin is told once, not twice.
     */
    private List<User> recipients(Ticket t) {
        Map<Long, User> byId = new LinkedHashMap<>();
        if (cfg.isEmailAssignee() && t.getAssignee() != null) {
            byId.put(t.getAssignee().getId(), t.getAssignee());
        }
        Company company = t.getCompany();
        if (cfg.isEmailMainEngineer() && company.getMainEngineer() != null) {
            byId.putIfAbsent(company.getMainEngineer().getId(), company.getMainEngineer());
        }
        Set<String> escalate = cfg.getEscalateToAdminsForPriorities().stream()
            .map(p -> p.trim().toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
        if (escalate.contains(t.getPriority().name())) {
            for (User admin : users.findByRoleAndStatus(Roles.ADMIN, UserStatus.ACTIVE)) {
                byId.putIfAbsent(admin.getId(), admin);
            }
        }
        List<User> out = new ArrayList<>();
        for (User u : byId.values()) {
            if (u.getStatus() == UserStatus.ACTIVE) out.add(u);
        }
        return out;
    }

    /** Leaves a visible entry on the ticket timeline, the same way status changes do. */
    private void systemNote(Ticket t, String text) {
        TicketComment c = new TicketComment();
        c.setTicket(t);
        c.setBody(text);
        c.setSystem(true);
        comments.save(c);
    }

    private static String humanDuration(Duration d) {
        long minutes = Math.max(0, d.toMinutes());
        if (minutes < 60) return minutes + "m";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h " + (minutes % 60) + "m";
        return (hours / 24) + "d " + (hours % 24) + "h";
    }
}
