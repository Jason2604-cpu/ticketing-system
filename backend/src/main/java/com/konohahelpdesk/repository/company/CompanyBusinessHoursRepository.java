package com.konohahelpdesk.repository.company;

import com.konohahelpdesk.model.company.CompanyBusinessHours;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CompanyBusinessHoursRepository extends JpaRepository<CompanyBusinessHours, Long> {

    List<CompanyBusinessHours> findByCompanyId(Long companyId);

    void deleteByCompanyId(Long companyId);
}
