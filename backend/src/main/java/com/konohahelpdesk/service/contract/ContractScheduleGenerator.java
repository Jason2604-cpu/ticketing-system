package com.konohahelpdesk.service.contract;

import com.konohahelpdesk.model.contract.ContractFrequency;
import com.konohahelpdesk.model.contract.ContractVisit;
import com.konohahelpdesk.model.contract.ServiceContract;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a contract's start date + frequency + occurrence count into dated visit rows.
 *
 * Month arithmetic, not day arithmetic: {@code plusMonths} keeps a quarterly visit on the same
 * day of the month (15 Jan → 15 Apr → 15 Jul) and clamps the impossible ones (31 Jan + 1 month
 * → 28/29 Feb), which is exactly how a contract written "the 31st of each month" is honoured in
 * practice. Adding 90 days instead would drift the date every cycle.
 *
 * Deliberately a separate bean from {@link ContractService}: generation is pure — dates in,
 * dates out, no repository, no clock — so it can be reasoned about and tested on its own.
 */
@Component
public class ContractScheduleGenerator {

    /**
     * Generates the visit dates for a contract. Occurrences that would fall past the contract's
     * end date are dropped rather than clamped onto it: a quarterly contract that only runs 7
     * months has two visits, not four bunched on the last day.
     *
     * The first date is always produced, even when it is already past — a contract backdated to
     * cover work that has already happened should show that work as overdue, not vanish.
     */
    public List<LocalDate> dates(LocalDate firstVisit, ContractFrequency frequency, int occurrences, LocalDate endDate) {
        List<LocalDate> out = new ArrayList<>();
        if (firstVisit == null || frequency == null || occurrences < 1) return out;

        int months = frequency.getMonths();
        int wanted = frequency == ContractFrequency.ONE_OFF ? 1 : occurrences;

        for (int i = 0; i < wanted; i++) {
            LocalDate d = months == 0 ? firstVisit : firstVisit.plusMonths((long) months * i);
            if (i > 0 && endDate != null && d.isAfter(endDate)) break;
            out.add(d);
        }
        return out;
    }

    /**
     * Builds the visit rows for a contract. Callers own the persistence side; this only shapes
     * the objects and links them back to their contract.
     */
    public List<ContractVisit> build(ServiceContract contract, int startingSequence) {
        List<ContractVisit> visits = new ArrayList<>();
        // A ticket-based contract has no schedule by definition — generating one would put
        // fictional dates on the timeline and fictional overdue visits on the dashboard.
        if (!contract.hasSchedule()) return visits;
        int seq = startingSequence;
        for (LocalDate d : dates(contract.getFirstVisitDate(), contract.getFrequency(), contract.getOccurrences(), contract.getEndDate())) {
            ContractVisit v = new ContractVisit();
            v.setContract(contract);
            v.setSequence(seq++);
            v.setScheduledDate(d);
            visits.add(v);
        }
        return visits;
    }
}
