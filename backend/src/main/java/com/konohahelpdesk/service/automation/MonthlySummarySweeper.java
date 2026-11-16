package com.konohahelpdesk.service.automation;

import com.konohahelpdesk.repository.company.CompanyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.time.ZonedDateTime;

/**
 * Walks every company and lets {@link MonthlySummarySender} decide whether its summary is due.
 *
 * Deliberately not @Transactional: each company is its own transaction inside the sender, so one
 * company's failure is logged and the loop carries on.
 */
@Service
public class MonthlySummarySweeper {

    private static final Logger log = LoggerFactory.getLogger(MonthlySummarySweeper.class);

    private final CompanyRepository companies;
    private final MonthlySummarySender sender;
    private final AutomationClock clock;

    public MonthlySummarySweeper(CompanyRepository companies, MonthlySummarySender sender, AutomationClock clock) {
        this.companies = companies;
        this.sender = sender;
        this.clock = clock;
    }

    /** @return how many companies had a summary queued this pass */
    public int runOnce() {
        ZonedDateTime now = clock.now();
        YearMonth period = clock.lastCompletedMonth(now);
        int sent = 0;
        for (Long companyId : companies.findAllIds()) {
            try {
                if (sender.processIfDue(companyId, now, period) == MonthlySummarySender.Outcome.SENT) sent++;
            } catch (RuntimeException e) {
                // e.g. the unique key rejecting a racing replica, or a company deleted mid-sweep.
                log.warn("Monthly summary sweep skipped company {}: {}", companyId, e.getMessage());
            }
        }
        return sent;
    }
}
