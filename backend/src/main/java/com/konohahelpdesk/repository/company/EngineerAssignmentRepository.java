package com.konohahelpdesk.repository.company;

import com.konohahelpdesk.model.company.EngineerAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EngineerAssignmentRepository extends JpaRepository<EngineerAssignment, Long> {
    List<EngineerAssignment> findByEngineerId(Long engineerId);
    List<EngineerAssignment> findByCompanyId(Long companyId);
    void deleteByEngineerId(Long engineerId);
    void deleteByCompanyId(Long companyId);

    /**
     * Just the ids — the scope check behind every engineer request (ticket list, counts, reports,
     * company scope). Loading full assignment rows here dragged in the engineer and company graphs.
     */
    @org.springframework.data.jpa.repository.Query("select a.company.id from EngineerAssignment a where a.engineer.id = :engineerId")
    List<Long> findCompanyIdsByEngineerId(@org.springframework.data.repository.query.Param("engineerId") Long engineerId);

    /** Every assignment with both ends in one query (staff list, companies list). */
    @org.springframework.data.jpa.repository.Query("select a from EngineerAssignment a join fetch a.company join fetch a.engineer")
    List<EngineerAssignment> findAllWithEngineerAndCompany();
}
