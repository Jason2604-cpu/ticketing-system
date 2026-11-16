package com.konohahelpdesk.service.company;

import com.konohahelpdesk.dto.user.UserResponse;
import com.konohahelpdesk.exception.BusinessRuleException;
import com.konohahelpdesk.exception.ResourceNotFoundException;
import com.konohahelpdesk.model.company.Company;
import com.konohahelpdesk.model.company.EngineerAssignment;
import com.konohahelpdesk.model.user.Roles;
import com.konohahelpdesk.model.user.User;
import com.konohahelpdesk.model.user.UserStatus;
import com.konohahelpdesk.repository.company.CompanyRepository;
import com.konohahelpdesk.repository.company.EngineerAssignmentRepository;
import com.konohahelpdesk.repository.user.UserRepository;
import com.konohahelpdesk.security.AuditLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class EngineerAssignmentService {

    private final EngineerAssignmentRepository repository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final AuditLogger audit;

    public EngineerAssignmentService(EngineerAssignmentRepository repository, UserRepository userRepository,
                                     CompanyRepository companyRepository, AuditLogger audit) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.audit = audit;
    }

    /** Replace an engineer's company list with exactly these ids. */
    @Transactional
    public List<UserResponse.CompanyRef> setCompaniesForEngineer(Long engineerId, Set<Long> companyIds) {
        User engineer = userRepository.findByIdAndStatusNot(engineerId, UserStatus.IS_DELETED)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + engineerId));
        if (!engineer.getRole().worksTickets()) {
            throw new BusinessRuleException("Only engineers and admins can be assigned to companies");
        }
        repository.deleteByEngineerId(engineerId);
        repository.flush();
        List<Company> companies = companyRepository.findAllById(companyIds);
        if (companies.size() != new HashSet<>(companyIds).size()) {
            throw new ResourceNotFoundException("One or more companies do not exist");
        }
        for (Company c : companies) {
            EngineerAssignment a = new EngineerAssignment();
            a.setEngineer(engineer);
            a.setCompany(c);
            repository.save(a);
        }
        audit.event("ENGINEER_ASSIGNED", "engineerId=" + engineerId + " companies=" + companyIds);
        return companies.stream().map(c -> new UserResponse.CompanyRef(c.getId(), c.getName())).toList();
    }

    public List<UserResponse.CompanyRef> companiesForEngineer(Long engineerId) {
        return repository.findByEngineerId(engineerId).stream()
            .map(a -> new UserResponse.CompanyRef(a.getCompany().getId(), a.getCompany().getName())).toList();
    }

    /** Drops every company assignment of this user (they no longer hold an engineer role). */
    @org.springframework.transaction.annotation.Transactional
    public void clearForEngineer(Long engineerId) {
        repository.deleteByEngineerId(engineerId);
    }

    /** The ids of the companies an engineer is assigned to (empty = unrestricted by convention). */
    public List<Long> companyIdsForEngineer(Long engineerId) {
        return repository.findCompanyIdsByEngineerId(engineerId);
    }

    public List<UserResponse> engineersForCompany(Long companyId) {
        return repository.findByCompanyId(companyId).stream()
            .map(a -> UserResponse.from(a.getEngineer())).toList();
    }

    /** engineerId → companies, for the whole staff list in one query. */
    public Map<Long, List<UserResponse.CompanyRef>> companiesByEngineer() {
        return repository.findAllWithEngineerAndCompany().stream().collect(Collectors.groupingBy(
            a -> a.getEngineer().getId(),
            Collectors.mapping(a -> new UserResponse.CompanyRef(a.getCompany().getId(), a.getCompany().getName()), Collectors.toList())));
    }

    /** companyId → engineers, for the company list in one query. */
    public Map<Long, List<UserResponse>> engineersByCompany() {
        return repository.findAllWithEngineerAndCompany().stream().collect(Collectors.groupingBy(
            a -> a.getCompany().getId(),
            Collectors.mapping(a -> UserResponse.from(a.getEngineer()), Collectors.toList())));
    }
}
