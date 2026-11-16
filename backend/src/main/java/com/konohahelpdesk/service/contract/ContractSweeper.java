package com.konohahelpdesk.service.contract;

import com.konohahelpdesk.config.AppProperties;
import com.konohahelpdesk.mail.MailService;
import com.konohahelpdesk.model.contract.ContractStatus;
import com.konohahelpdesk.model.contract.ContractVisit;
import com.konohahelpdesk.model.contract.ServiceContract;
import com.konohahelpdesk.model.contract.VisitStatus;
import com.konohahelpdesk.model.notification.NotificationType;
import com.konohahelpdesk.model.user.Roles;
import com.konohahelpdesk.model.user.User;
import com.konohahelpdesk.model.user.UserStatus;
import com.konohahelpdesk.repository.contract.ContractVisitRepository;
import com.konohahelpdesk.repository.contract.ServiceContractRepository;
import com.konohahelpdesk.repository.user.UserRepository;
import com.konohahelpdesk.security.AuditLogger;
import com.konohahelpdesk.service.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The work behind the daily contract sweep: raise maintenance tickets for visits coming due, and
 * mail renewal reminders for contracts approaching their end date.
 *
 * Split from {@link ContractMonitor} for the same reason {@code SlaBreachRecorder} is split from
 * {@code SlaMonitor}: a {@code @Transactional} method called from another method of the same bean
 * bypasses the proxy, and with {@code open-in-view=false} that would break the lazy loads here
 * and {@code MailService}'s after-commit hook.
 *
 * Both passes are idempotent by construction, which is what makes a restart or a second run the
 * same day harmless: a visit with a ticket linked is never picked up again, and a reminder is
 * only sent when {@code renewalReminderSentFor} doesn't already match the contract's end date.
 */
@Service
public class ContractSweeper {

    private static final Logger log = LoggerFactory.getLogger(ContractSweeper.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy");

    private final ServiceContractRepository contracts;
    private final ContractVisitRepository visits;
    private final UserRepository users;
    private final ContractVisitTicketRaiser ticketRaiser;
    private final NotificationService notifications;
    private final MailService mail;
    private final AuditLogger audit;
    private final AppProperties.Contracts cfg;

    public ContractSweeper(ServiceContractRepository contracts, ContractVisitRepository visits,
                           UserRepository users, ContractVisitTicketRaiser ticketRaiser, NotificationService notifications,
                           MailService mail, AuditLogger audit, AppProperties props) {
        this.contracts = contracts;
        this.visits = visits;
        this.users = users;
        this.ticketRaiser = ticketRaiser;
        this.notifications = notifications;
        this.mail = mail;
        this.audit = audit;
        this.cfg = props.getContracts();
    }

    /** Runs both passes. Returns how many tickets were raised, for the log line. */
    @Transactional
    public int runOnce() {
        LocalDate today = LocalDate.now();
        int raised = raiseDueTickets(today);
        sendRenewalReminders(today);
        return raised;
    }

    // ── Pass 1: maintenance tickets ──────────────────────────────────────────

    private int raiseDueTickets(LocalDate today) {
        // Widest horizon any contract could ask for; each contract's own lead time is applied below.
        LocalDate horizon = today.plusDays(cfg.getMaxTicketLeadDays());
        List<ContractVisit> candidates = visits.findTicketCandidates(VisitStatus.SCHEDULED, ContractStatus.ACTIVE, horizon);

        int raised = 0;
        for (ContractVisit v : candidates) {
            if (raised >= cfg.getBatchSize()) {
                log.warn("Contract sweep hit its batch size of {} — the rest will be picked up next run", cfg.getBatchSize());
                break;
            }
            ServiceContract c = v.getContract();
            int lead = Math.min(c.getTicketLeadDays(), cfg.getMaxTicketLeadDays());
            if (v.getScheduledDate().isAfter(today.plusDays(lead))) continue;

            // Each visit is raised in its own transaction (ContractVisitTicketRaiser). Calling
            // TicketService here directly made its failure mark THIS transaction rollback-only:
            // the catch below swallowed the exception, but the commit then failed, and one
            // company that could not take a ticket cost every other company its ticket — and
            // the renewal reminders — on every run.
            try {
                if (ticketRaiser.raise(v.getId())) raised++;
            } catch (RuntimeException e) {
                log.error("Could not raise the ticket for contract {} visit {}: {}", c.getId(), v.getId(), e.getMessage());
                audit.warn("CONTRACT_TICKET_FAILED", "contractId=" + c.getId() + " visitId=" + v.getId() + " reason=" + e.getMessage());
            }
        }
        return raised;
    }

    // ── Pass 2: renewal reminders ────────────────────────────────────────────
    private void sendRenewalReminders(LocalDate today) {
        List<ServiceContract> candidates = contracts.findRenewalCandidates(ContractStatus.ACTIVE, today);
        for (ServiceContract c : candidates) {
            Integer days = c.getRenewalReminderDays();
            if (days == null) continue;
            // Not inside the reminder window yet.
            if (c.getEndDate().isAfter(today.plusDays(days))) continue;

            long left = ChronoUnit.DAYS.between(today, c.getEndDate());
            String title = "Contract renewal due — " + c.getCompany().getName();
            String body = "\"" + c.getName() + "\" for " + c.getCompany().getName()
                + " expires on " + c.getEndDate().format(DATE)
                + " (" + (left == 0 ? "today" : left + " day" + (left == 1 ? "" : "s") + " away") + ").";

            for (User u : reminderRecipients(c)) {
                // Opens the company (as the email does): the one page every recipient — owner, main
                // engineer, admin — can open.
                notifications.notifyLink(u, NotificationType.CONTRACT_RENEWAL_DUE, title, body,
                    "/companies/" + c.getCompany().getId());
                if (cfg.isEmailRenewalReminders() && u.isNotifyEmail() && u.getEmail() != null && !u.getEmail().isBlank()) {
                    mail.send(u.getEmail(),
                        "Contract renewal due — " + c.getCompany().getName() + " — " + mail.appName(),
                        "Hi " + u.getFirstName() + ",\n\n" + body + "\n\n"
                        + (c.getContractNumber() != null ? "Contract ref: " + c.getContractNumber() + "\n" : "")
                        + "Term: " + c.getStartDate().format(DATE) + " – " + c.getEndDate().format(DATE) + "\n\n"
                        + "Review the company here:\n" + mail.companyLink(c.getCompany().getId()) + "\n");
                }
            }

            // Stamped against the end date, so extending the contract re-arms the reminder while
            // a second sweep today does nothing.
            c.setRenewalReminderSentFor(c.getEndDate());
            contracts.save(c);
            audit.event("CONTRACT_RENEWAL_REMINDED", "contractId=" + c.getId() + " endDate=" + c.getEndDate());
        }
    }

    /** Owner + the company's main engineer + every active admin, de-duplicated. */
    private List<User> reminderRecipients(ServiceContract c) {
        Map<Long, User> byId = new LinkedHashMap<>();
        if (c.getOwner() != null) byId.put(c.getOwner().getId(), c.getOwner());
        if (c.getCompany().getMainEngineer() != null) {
            byId.putIfAbsent(c.getCompany().getMainEngineer().getId(), c.getCompany().getMainEngineer());
        }
        for (User admin : users.findByRoleAndStatus(Roles.ADMIN, UserStatus.ACTIVE)) {
            byId.putIfAbsent(admin.getId(), admin);
        }
        return byId.values().stream().filter(u -> u.getStatus() == UserStatus.ACTIVE).toList();
    }
}
