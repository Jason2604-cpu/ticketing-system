package com.konohahelpdesk.config;

import com.konohahelpdesk.dto.company.BusinessHoursDto;
import com.konohahelpdesk.model.company.Company;
import com.konohahelpdesk.model.company.CompanyBusinessHours;
import com.konohahelpdesk.repository.company.CompanyBusinessHoursRepository;
import com.konohahelpdesk.repository.company.CompanyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gives every company a full week of business hours, defaulting to Mon–Fri 09:00–18:00.
 *
 * Companies created before the SLA business-hours feature have no rows at all. Rather than
 * scattering "if there are no rows, assume…" fallbacks through BusinessCalendar and the company
 * API, this writes the defaults once so the database is the single source of truth and what the
 * SLA clock uses is exactly what the edit form shows.
 *
 * Idempotent: only touches companies with zero rows, so it is a no-op on every later startup and
 * never overwrites hours an admin has set.
 */
@Component
@Order(2)
public class BusinessHoursBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BusinessHoursBackfill.class);

    private final CompanyRepository companies;
    private final CompanyBusinessHoursRepository hours;

    public BusinessHoursBackfill(CompanyRepository companies, CompanyBusinessHoursRepository hours) {
        this.companies = companies;
        this.hours = hours;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int filled = 0;
        for (Company company : companies.findAll()) {
            if (!hours.findByCompanyId(company.getId()).isEmpty()) continue;
            for (BusinessHoursDto d : BusinessHoursDto.defaults()) {
                CompanyBusinessHours row = new CompanyBusinessHours();
                row.setCompany(company);
                row.setDayOfWeek(d.getDayOfWeek());
                row.setOpenTime(d.getOpenTime());
                row.setCloseTime(d.getCloseTime());
                row.setClosed(d.isClosed());
                hours.save(row);
            }
            filled++;
        }
        if (filled > 0) log.info("Business hours backfilled with Mon–Fri 09:00–18:00 for {} company/companies", filled);
    }
}
