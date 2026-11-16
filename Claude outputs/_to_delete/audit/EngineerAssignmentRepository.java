package com.konohahelpdesk.repository.company;

import com.konohahelpdesk.model.company.EngineerAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EngineerAssignmentRepository extends JpaRepository<EngineerAssignment, Long> {
    List<EngineerAssignment> findByEngineerId(Long engineerId);
    List<EngineerAssignment> findByCompanyId(Long companyId);    void deleteByEngineerId(Long engineerId);
    void deleteByCompanyId(Long companyId);
}
