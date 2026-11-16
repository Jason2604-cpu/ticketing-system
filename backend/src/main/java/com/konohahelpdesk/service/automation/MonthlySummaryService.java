package com.konohahelpdesk.service.automation;

import com.konohahelpdesk.dto.automation.MonthlySummaryPreview;
import com.konohahelpdesk.exception.BusinessRuleException;
import com.konohahelpdesk.mail.MailService;
import com.konohahelpdesk.model.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;

/** On-demand views of the monthly summary: see what it looks like, or mail a test copy to yourself. */
@Service
public class MonthlySummaryService {

    private final AutomationSettingsService settingsService;
    private final MonthlySummaryBuilder builder;
    private final AutomationClock clock;
    private final MailService mail;

    public MonthlySummaryService(AutomationSettingsService settingsService, MonthlySummaryBuilder builder,
                                 AutomationClock clock, MailService mail) {
        this.settingsService = settingsService;
        this.builder = builder;
        this.clock = clock;
        this.mail = mail;
    }

    /** The email for the month that just ended, and who the real one would go to. Sends nothing. */
    @Transactional(readOnly = true)
    public MonthlySummaryPreview preview(User actor, Long companyId) {
        settingsService.requireView(actor, companyId);
        YearMonth month = clock.lastCompletedMonth(clock.now());
        MonthlySummaryBuilder.Built built = builder.build(companyId, month);
        return new MonthlySummaryPreview(built.subject(), built.html(), built.periodLabel(),
            settingsService.recipientsFor(companyId, settingsService.peek(companyId)));
    }

    /** Mails the same email to the requesting user only — never to the company's recipients. */
    @Transactional(readOnly = true)
    public void sendTest(User actor, Long companyId) {
        settingsService.requireView(actor, companyId);
        if (actor.getEmail() == null || actor.getEmail().isBlank()) {
            throw new BusinessRuleException("Your account has no email address to send the test to");
        }
        YearMonth month = clock.lastCompletedMonth(clock.now());
        MonthlySummaryBuilder.Built built = builder.build(companyId, month);
        mail.sendHtml(actor.getEmail(), "[Test] " + built.subject(), built.html(), built.text());
    }
}
