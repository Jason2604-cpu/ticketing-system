package com.konohahelpdesk.service.contract;

import com.konohahelpdesk.config.AppProperties;
import com.konohahelpdesk.mail.MailService;
import com.konohahelpdesk.model.company.Company;
import com.konohahelpdesk.model.contract.ContractStatus;
import com.konohahelpdesk.model.contract.ContractVisit;
import com.konohahelpdesk.model.contract.ServiceContract;
import com.konohahelpdesk.model.contract.VisitStatus;
import com.konohahelpdesk.model.notification.NotificationType;
import com.konohahelpdesk.model.ticket.Ticket;
import com.konohahelpdesk.model.user.Roles;
import com.konohahelpdesk.model.user.User;
import com.konohahelpdesk.model.user.UserStatus;
import com.konohahelpdesk.repository.contract.ContractVisitRepository;
import com.konohahelpdesk.repository.contract.ServiceContractRepository;
import com.konohahelpdesk.repository.user.UserRepository;
import com.konohahelpdesk.security.AuditLogger;
import com.konohahelpdesk.service.notification.NotificationService;
import com.konohahelpdesk.service.ticket.TicketService;
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
    private final TicketService tickets;
    private final NotificationService notifications;
    private final MailService mail;
    private final AuditLogger audit;
    private final AppProperties.Contracts cfg;

    public ContractSweeper(ServiceContractRepository contracts, ContractVisitRepository visits,
                           UserRepository users, TicketService tickets, NotificationService notifications,
                           MailService mail, AuditLogger audit, AppProperties props) {
        this.contracts = contracts;
        this.visits = visits;
        this.users = users;
        this.tickets = tickets;
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

            try {
                Ticket t = tickets.createForContract(
                    c.getCompany(),
                    raiser(c),
                    c.getName() + " — scheduled visit " + v.getSequence() + " (" + v.getScheduledDate().format(DATE) + ")",
                    describe(c, v),
                    c.getTicketCategory(),
                    c.getTicketPriority());
                v.setTicket(t);
                visits.save(v);
                raised++;
            } catch (RuntimeException e) {
                // One company without the ticketing feature, or one mail failure, must not stop
                // every other contract's visit from being raised.
                log.error("Could not raise the ticket for contract {} visit {}: {}", c.getId(), v.getId(), e.getMessage());
                audit.warn("CONTRACT_TICKET_FAILED", "contractId=" + c.getId() + " visitId=" + v.getId() + " reason=" + e.getMessage());
            }
        }
        return raised;
    }

    private String describe(ServiceContract c, ContractVisit v) {
        StringBuilder b = new StringBuilder();
        b.append("Scheduled work under service contract \"").append(c.getName()).append("\".\n\n");
        if (c.getContractNumber() != null) b.append("Contract ref: ").append(c.getContractNumber()).append("\n");
        b.append("Company: ").append(c.getCompany().getName()).append("\n");
        b.append("Visit: ").append(v.getSequence()).append(" of ").append(c.getOccurrences())
            .append(" (").append(c.getFrequency().getLabel().toLowerCase(java.util.Locale.ROOT)).append(")\n");
        b.append("Scheduled for: ").append(v.getScheduledDate().format(DATE)).append("\n");
        b.append("Contract term: ").append(c.getStartDate().format(DATE)).append(" – ").append(c.getEndDate().format(DATE)).append("\n");
        if (c.getCoveredAssets() != null) b.append("\nCovered equipment:\n").append(c.getCoveredAssets()).append("\n");
        if (c.getDescription() != null) b.append("\n").append(c.getDescription()).append("\n");
        return b.toString();
    }

    /**
     * Whose name the auto-raised ticket goes out under. The contract owner if one is named,
     * otherwise the company's main engineer, otherwise any active admin — {@code Ticket.createdBy}
     * is non-null, so there has to be someone, and picking an admin is honest about the fact that
     * the provider raised it rather than the customer.
     */
    private User raiser(ServiceContract c) {
        if (c.getOwner() != null && c.getOwner().getStatus() == UserStatus.ACTIVE) return c.getOwner();
        Company company = c.getCompany();
        if (company.getMainEngineer() != null && company.getMainEngineer().getStatus() == UserStatus.ACTIVE) {
            return company.getMainEngineer();
        }
        return users.findByRoleAndStatus(Roles.ADMIN, UserStatus.ACTIVE).stream().findFirst()
            .orElseThrow(() -> new IllegalStateException("No active admin to raise the contract ticket as"));
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
                notifications.notify(u, NotificationType.CONTRACT_RENEWAL_DUE, title, body, null);
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
