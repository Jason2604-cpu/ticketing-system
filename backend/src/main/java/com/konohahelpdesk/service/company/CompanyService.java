package com.konohahelpdesk.service.company;

import com.konohahelpdesk.service.subscription.CompanySubscriptionService;

import com.konohahelpdesk.dto.company.CompanyResponse;
import com.konohahelpdesk.dto.company.CreateCompanyRequest;
import com.konohahelpdesk.dto.company.UpdateCompanyRequest;
import com.konohahelpdesk.exception.BusinessRuleException;
import com.konohahelpdesk.exception.DuplicateResourceException;
import com.konohahelpdesk.exception.ResourceNotFoundException;
import com.konohahelpdesk.model.company.Company;
import com.konohahelpdesk.dto.subscription.CompanySubscriptionResponse;
import com.konohahelpdesk.dto.user.UserResponse;
import com.konohahelpdesk.model.user.Roles;
import com.konohahelpdesk.model.user.User;
import com.konohahelpdesk.model.user.UserStatus;
import com.konohahelpdesk.repository.company.CompanyRepository;
import com.konohahelpdesk.repository.user.UserRepository;
import com.konohahelpdesk.security.AuditLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final CompanySubscriptionService subscriptionService;
    private final EngineerAssignmentService assignments;
    private final UserRepository userRepository;
    private final AuditLogger audit;

    public CompanyService(CompanyRepository companyRepository,
                          CompanySubscriptionService subscriptionService,
                          EngineerAssignmentService assignments,
                          UserRepository userRepository,
                          AuditLogger audit) {
        this.companyRepository = companyRepository;
        this.subscriptionService = subscriptionService;
        this.assignments = assignments;
        this.userRepository = userRepository;
        this.audit = audit;
    }

    /** Creates the company AND its subscription in one transaction — neither exists without the other. */
    @Transactional
    public CompanyResponse create(CreateCompanyRequest req) {
        String name = req.getName().trim();
        if (companyRepository.existsByNameIgnoreCase(name)) {
            throw new DuplicateResourceException("A company named '" + name + "' already exists");
        }
        Company company = new Company();
        company.setName(name);
        applyDetails(company, req.getAddress(), req.getContactEmail(), req.getContactPhone());
        applyMainEngineer(company, req.getMainEngineerId());
        Company saved = companyRepository.save(company);

        audit.event("COMPANY_CREATED", "companyId=" + saved.getId() + " name=" + name);
        CompanyResponse response = CompanyResponse.from(saved);
        response.setSubscription(subscriptionService.assign(saved.getId(), req.getSubscription()));
        return response;
    }

    @Transactional
    public CompanyResponse update(Long id, UpdateCompanyRequest req) {
        Company company = load(id);
        String name = req.getName().trim();
        if (companyRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new DuplicateResourceException("A company named '" + name + "' already exists");
        }
        company.setName(name);
        applyDetails(company, req.getAddress(), req.getContactEmail(), req.getContactPhone());
        applyMainEngineer(company, req.getMainEngineerId());
        Company saved = companyRepository.save(company);
        audit.event("COMPANY_UPDATED", "companyId=" + id);
        return withSubscription(saved);
    }

    /**
     * Company table for the admin/engineer portals: plan, seat usage, assigned engineers, company admin.
     * An ENGINEER only sees the companies they're assigned to; if they have no assignments, they see all
     * (same "no company chosen = show everything" rule the ticket list uses).
     */
    public List<CompanyResponse> list(User actor) {
        Map<Long, List<UserResponse>> engineersByCompany = assignments.engineersByCompany();
        List<User> customerUsers = userRepository.findByStatusNot(UserStatus.IS_DELETED).stream()
            .filter(u -> u.getCompany() != null).toList();

        List<Long> engineerScope = actor.getRole() == Roles.ENGINEER
            ? assignments.companiesForEngineer(actor.getId()).stream().map(UserResponse.CompanyRef::id).toList()
            : List.of();

        return companyRepository.findAll().stream()
            .filter(c -> engineerScope.isEmpty() || engineerScope.contains(c.getId()))
            .sorted(Comparator.comparing(Company::getName, String.CASE_INSENSITIVE_ORDER))
            .map(c -> {
                CompanyResponse r = CompanyResponse.from(c);
                Optional<CompanySubscriptionResponse> sub = subscriptionService.findResolved(c.getId());
                sub.ifPresent(s -> {
                    r.setPlanName(s.getSubscriptionType() == com.konohahelpdesk.model.plan.PlanType.CUSTOM ? "Custom" : s.getPlanName());
                    r.setSubscriptionType(s.getSubscriptionType().name());
                    r.setMaxUsers(s.getLimits().getMaxUsers());
                    r.setActiveUsers(s.getUsage().getActiveUsers());
                });
                r.setEngineers(engineersByCompany.getOrDefault(c.getId(), List.of()));
                customerUsers.stream()
                    .filter(u -> u.getCompany().getId().equals(c.getId()) && u.getRole() == Roles.CUSTOMER_ADMIN)
                    .findFirst().ifPresent(a -> r.setCompanyAdmin(UserResponse.from(a)));
                return r;
            })
            .toList();
    }

    public CompanyResponse get(Long id) {
        return withSubscription(load(id));
    }

    public Company load(Long id) {
        return companyRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Company not found: " + id));
    }

    private CompanyResponse withSubscription(Company company) {
        CompanyResponse r = CompanyResponse.from(company);
        subscriptionService.findResolved(company.getId()).ifPresent(r::setSubscription);
        return r;
    }

    private void applyDetails(Company c, String address, String contactEmail, String contactPhone) {
        c.setAddress(blankToNull(address));
        c.setContactEmail(contactEmail == null || contactEmail.isBlank() ? null : contactEmail.trim().toLowerCase(Locale.ROOT));
        c.setContactPhone(blankToNull(contactPhone));
    }

    /** Resolves and validates the main-engineer selection; null clears it. */
    private void applyMainEngineer(Company c, Long mainEngineerId) {
        if (mainEngineerId == null) {
            c.setMainEngineer(null);
            return;
        }
        User engineer = userRepository.findById(mainEngineerId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + mainEngineerId));
        if (engineer.getRole() != Roles.ENGINEER && engineer.getRole() != Roles.ADMIN) {
            throw new BusinessRuleException("Only engineers and admins can be set as the main engineer");
        }
        c.setMainEngineer(engineer);
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
