package com.konohahelpdesk.repository.company;

import com.konohahelpdesk.model.company.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    /** Ids only — "every company" report scope, without loading each company's graph. */
    @org.springframework.data.jpa.repository.Query("select c.id from Company c")
    java.util.List<Long> findAllIds();

    /** Frees every company whose main engineer is this user (they moved to the Default role). */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update Company c set c.mainEngineer = null where c.mainEngineer.id = :userId")
    int clearMainEngineer(@org.springframework.data.repository.query.Param("userId") Long userId);
}
