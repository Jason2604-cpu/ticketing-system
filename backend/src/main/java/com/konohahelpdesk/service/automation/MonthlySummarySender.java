package com.konohahelpdesk.service.automation;

import com.konohahelpdesk.config.AppProperties;
import com.konohahelpdesk.mail.MailService;
import com.konohahelpdesk.model.automation.*;
import com.konohahelpdesk.model.company.Company;
import com.konohahelpdesk.repository.automation.AutomationRunRepository;
import com.konohahelpdesk.repository.company.CompanyRepository;
import com.konohahelpdesk.service.subscription.CompanySubscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Sends one company's monthly summary if, and only if, it is due and hasn't been handled yet.
 *
 * One transaction per company, in a bean of its own: the sweep calls this for every company, and a
 * failure for one must roll back only that company's run row — and the sweep can't be @Transactional
 * itself, because the mail is queued after commit and one bad company shouldn't hold up the rest.
 *
 * IDEMPOTENCY: the run row is unique per (company, MONTHLY_SUMMARY, "2026-10"). A SENT or SKIPPED
 * row ends the matter; a FAILED row is retried by later sweeps up to app.automation.max-attempts. A
 * second backend replica racing the first hits the unique key, its transaction rolls back, and since
 * mail is only queued after commit, nothing extra goes out.
 */
@Service
public class MonthlySummarySender {

    private static final Logger log = LoggerFactory.getLogger(MonthlySummarySender.class);

    public enum Outcome { NOT_DUE, ALREADY_DONE, SENT, SKIPPED, FAILED }

    private final CompanyRepository companies;
    private final AutomationRunRepository runs;
    private final AutomationSettingsService settingsService;
    private final MonthlySummaryBuilder builder;
    private final CompanySubscriptionService subscriptions;
    private final MailService mail;
    private final AutomationClock clock;
    private final AppProperties.Automation cfg;

    public MonthlySummarySender(CompanyRepository companies, AutomationRunRepository runs,
                                AutomationSettingsService settingsService, MonthlySummaryBuilder builder,
                                CompanySubscriptionService subscriptions, MailService mail,
                                AutomationClock clock, AppProperties props) {
        this.companies = companies;
        this.runs = runs;
        this.settingsService = settingsService;
        this.builder = builder;
        this.subscriptions = subscriptions;
        this.mail = mail;
        this.clock = clock;
        this.cfg = props.getAutomation();
    }

    @Transactional
    public Outcome processIfDue(Long companyId, ZonedDateTime now, YearMonth period) {
        if (!subscriptions.hasFeature(companyId, AutomationSettingsService.FEATURE)) return Outcome.NOT_DUE;

        AutomationSettings s = settingsService.peek(companyId);
        if (!s.isMonthlySummaryEnabled()) return Outcome.NOT_DUE;
        if (now.isBefore(clock.dueAt(YearMonth.from(now), s.getMonthlySummaryDay()))) return Outcome.NOT_DUE;

        String key = period.toString();   // "2026-10"
        Optional<AutomationRun> existing = runs.findByCompanyIdAndAutomationTypeAndPeriodKey(companyId, AutomationType.MONTHLY_SUMMARY, key);
        AutomationRun run;
        if (existing.isPresent()) {
            run = existing.get();
            if (run.getStatus() != RunStatus.FAILED) return Outcome.ALREADY_DONE;
            if (run.getAttempts() >= cfg.getMaxAttempts()) return Outcome.ALREADY_DONE;
            run.countRetry();
        } else {
            run = new AutomationRun(companyId, AutomationType.MONTHLY_SUMMARY, key);
        }

        Company company = companies.findById(companyId).orElse(null);
        if (company == null) return Outcome.NOT_DUE;

        // A company created after the month ended has no history to report — skip, don't mail zeros.
        LocalDateTime periodEnd = clock.startOf(period.plusMonths(1));
        if (company.getCreatedAt() != null && !company.getCreatedAt().isBefore(periodEnd)) {
            run.record(RunStatus.SKIPPED, 0, "Company was created after this period");
            runs.save(run);
            return Outcome.SKIPPED;
        }

        List<String> recipients = settingsService.recipientsFor(companyId, s);
        if (recipients.isEmpty()) {
            run.record(RunStatus.SKIPPED, 0, "No recipients: no active Customer Admin with email notifications on, and no extra addresses");
            runs.save(run);
            return Outcome.SKIPPED;
        }

        try {
            MonthlySummaryBuilder.Built built = builder.build(companyId, period);
            for (String to : recipients) mail.sendHtml(to, built.subject(), built.html(), built.text());
            run.record(RunStatus.SENT, recipients.size(), "Summary for " + built.periodLabel() + " queued for delivery");
            runs.save(run);
            log.info("Monthly summary for company {} ({}) queued to {} recipient(s)", companyId, key, recipients.size());
            return Outcome.SENT;
        } catch (RuntimeException e) {
            log.error("Monthly summary for company {} ({}) failed: {}", companyId, key, e.getMessage(), e);
            run.record(RunStatus.FAILED, 0, "Failed: " + e.getMessage());
            runs.save(run);
            return Outcome.FAILED;
        }
    }
}
