package com.konohahelpdesk.service.subscription;

import com.konohahelpdesk.mail.MailService;
import com.konohahelpdesk.model.company.Company;
import com.konohahelpdesk.model.company.CompanySubscription;
import com.konohahelpdesk.model.user.Roles;
import com.konohahelpdesk.model.user.User;
import com.konohahelpdesk.model.user.UserStatus;
import com.konohahelpdesk.repository.company.CompanySubscriptionRepository;
import com.konohahelpdesk.repository.user.UserRepository;
import com.konohahelpdesk.security.AuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Emails staff when a company runs out of monthly ticket quota.
 *
 * Runs in its own transaction (REQUIRES_NEW) because the caller is about to throw
 * {@link com.konohahelpdesk.exception.LimitExceededException} and roll back — the
 * "already alerted" marker and MailService's after-commit delivery hook would both be lost with it.
 * The caller's persistence context is suspended while this runs, so everything here is re-loaded
 * from ids rather than read off entities the caller passed in.
 *
 * One email per company per calendar month: a customer hammering the New Ticket button shouldn't
 * turn into a mailbox full of identical warnings.
 */
@Service
public class QuotaAlertService {

    private static final Logger log = LoggerFactory.getLogger(QuotaAlertService.class);

    private final CompanySubscriptionRepository subscriptions;
    private final UserRepository users;
    private final MailService mail;
    private final AuditLogger audit;

    public QuotaAlertService(CompanySubscriptionRepository subscriptions, UserRepository users,
                             MailService mail, AuditLogger audit) {
        this.subscriptions = subscriptions;
        this.users = users;
        this.mail = mail;
        this.audit = audit;
    }

    /**
     * @param companyId    the company that just hit its monthly ticket limit
     * @param monthlyLimit the limit it hit, for the email body
     * @param attemptedBy  who tried to raise the ticket ("Jane Doe <jane@acme.com>"), may be null
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ticketQuotaReached(Long companyId, int monthlyLimit, String attemptedBy) {
        if (companyId == null) return;
        String month = YearMonth.now().toString();

        CompanySubscription sub = subscriptions.findByCompanyId(companyId).orElse(null);
        if (sub == null) return;
        if (month.equals(sub.getQuotaAlertMonth())) return; // already warned this month
        sub.setQuotaAlertMonth(month);
        subscriptions.save(sub);

        Company company = sub.getCompany();
        List<User> recipients = alertRecipients(company);
        if (recipients.isEmpty()) {
            log.info("Company {} hit its ticket quota but no staff have email notifications enabled", companyId);
            return;
        }

        String subject = "Monthly ticket limit reached — " + company.getName() + " — " + mail.appName();
        String body = "Heads up,\n\n"
            + company.getName() + " has reached its monthly ticket limit of " + monthlyLimit
            + " tickets for " + month + ".\n\n"
            + "New tickets from this company are being rejected until the limit resets next month, "
            + "or until the subscription is upgraded.\n\n"
            + (attemptedBy == null ? "" : "Most recent attempt: " + attemptedBy + "\n\n")
            + "Review the company's subscription here:\n" + mail.companyLink(company.getId()) + "\n";

        for (User recipient : recipients) {
            mail.send(recipient.getEmail(), subject, body);
        }
        audit.event("TICKET_QUOTA_ALERT", "companyId=" + companyId + " month=" + month + " limit=" + monthlyLimit);
        log.info("Ticket quota alert sent for company {} ({}) to {} recipient(s)", companyId, month, recipients.size());
    }

    /** The company's main engineer plus every active admin, de-duplicated, honouring email preferences. */
    private List<User> alertRecipients(Company company) {
        Map<Long, User> byId = new LinkedHashMap<>();
        User mainEngineer = company.getMainEngineer();
        if (mainEngineer != null) byId.put(mainEngineer.getId(), mainEngineer);
        for (User admin : users.findByRoleAndStatus(Roles.ADMIN, UserStatus.ACTIVE)) {
            byId.putIfAbsent(admin.getId(), admin);
        }
        List<User> out = new ArrayList<>();
        for (User u : byId.values()) {
            if (u.getStatus() != UserStatus.ACTIVE) continue;
            if (!u.isNotifyEmail()) continue;
            if (u.getEmail() == null || u.getEmail().isBlank()) continue;
            out.add(u);
        }
        return out;
    }
}
