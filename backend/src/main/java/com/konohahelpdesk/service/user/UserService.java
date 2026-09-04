package com.konohahelpdesk.service.user;

import com.konohahelpdesk.service.auth.AccountService;
import com.konohahelpdesk.service.subscription.CompanySubscriptionService;

import com.konohahelpdesk.dto.user.CreateUserRequest;
import com.konohahelpdesk.dto.user.UserResponse;
import com.konohahelpdesk.exception.BusinessRuleException;
import com.konohahelpdesk.exception.DuplicateResourceException;
import com.konohahelpdesk.exception.ResourceNotFoundException;
import com.konohahelpdesk.model.company.Company;
import com.konohahelpdesk.model.user.Roles;
import com.konohahelpdesk.model.user.User;
import com.konohahelpdesk.model.user.UserStatus;
import com.konohahelpdesk.repository.company.CompanyRepository;
import com.konohahelpdesk.repository.user.UserRepository;
import com.konohahelpdesk.security.AuditLogger;
import com.konohahelpdesk.service.company.EngineerAssignmentService;
import com.konohahelpdesk.service.rbac.RoleService;
import com.konohahelpdesk.util.generator.PasswordGenerator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;
    private final CompanySubscriptionService subscriptionService;
    private final AccountService accountService;
    private final EngineerAssignmentService assignments;
    private final RoleService roleService;
    private final AuditLogger audit;

    public UserService(UserRepository userRepository,
                       CompanyRepository companyRepository,
                       PasswordEncoder passwordEncoder,
                       CompanySubscriptionService subscriptionService,
                       AccountService accountService,
                       EngineerAssignmentService assignments,
                       RoleService roleService,
                       AuditLogger audit) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.passwordEncoder = passwordEncoder;
        this.subscriptionService = subscriptionService;
        this.accountService = accountService;
        this.assignments = assignments;
        this.roleService = roleService;
        this.audit = audit;
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmailIgnoreCaseAndStatusNot(email, UserStatus.IS_DELETED)) {
            throw new DuplicateResourceException("A user with this email already exists");
        }

        User user = new User();
        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());
        user.setEmail(email);
        user.setPhone(request.getPhone().trim());
        user.setRole(request.getRole());

        // Customer-facing roles belong to a company; internal roles (ADMIN/ENGINEER) don't.
        boolean requiresCompany = request.getRole() == Roles.CUSTOMER || request.getRole() == Roles.CUSTOMER_ADMIN;
        if (requiresCompany) {
            if (request.getCompanyId() == null) {
                throw new BusinessRuleException("companyId is required for role " + request.getRole());
            }
            Company company = companyRepository.findById(request.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Company not found: " + request.getCompanyId()));

            // Subscription quota: max_users from the company's plan / custom config.
            subscriptionService.assertUserSeatAvailable(company.getId());
            user.setCompany(company);
        } else if (request.getCompanyId() != null) {
            throw new BusinessRuleException("companyId must not be set for role " + request.getRole());
        }

        user.setUserCode(nextUserCode(request.getRole()));

        // Nobody — not even the admin — knows this value. The user sets their real password
        // through the emailed activation link; until then the account is INVITED and cannot log in.
        user.setPassword(passwordEncoder.encode(PasswordGenerator.generate(32)));
        user.setStatus(UserStatus.INVITED);

        User saved = userRepository.save(user);
        audit.event("USER_CREATED", "userId=" + saved.getId() + " role=" + saved.getRole()
            + (saved.getCompany() != null ? " companyId=" + saved.getCompany().getId() : ""));

        accountService.sendActivation(saved);
        return UserResponse.from(saved);
    }

    public List<UserResponse> listUsers(Roles role, UserStatus status) {
        List<User> users;
        if (role != null && status != null) {
            users = userRepository.findByRoleAndStatus(role, status);
        } else if (role != null) {
            users = userRepository.findByRoleAndStatusNot(role, UserStatus.IS_DELETED);
        } else if (status != null) {
            users = userRepository.findByStatus(status);
        } else {
            users = userRepository.findByStatusNot(UserStatus.IS_DELETED);
        }
        var byEngineer = assignments.companiesByEngineer();
        var additionalByUser = roleService.additionalRolesByUser(users.stream().map(User::getId).toList());
        return users.stream().map(u -> {
            UserResponse r = UserResponse.from(u);
            if (u.getRole() == Roles.ENGINEER || u.getRole() == Roles.ADMIN) {
                r.setAssignedCompanies(byEngineer.getOrDefault(u.getId(), List.of()));
            }
            r.setAdditionalRoles(additionalByUser.getOrDefault(u.getId(), List.of()));
            return r;
        }).toList();
    }

    public UserResponse getById(Long id) {
        UserResponse r = UserResponse.from(loadActiveOrThrow(id));
        r.setAdditionalRoles(roleService.additionalRoles(id));
        return r;
    }

    /** ADMIN-only: replace the additive roles granted to a user (their primary role is untouched). */
    @Transactional
    public UserResponse setAdditionalRoles(Long id, Set<Roles> roles) {
        loadActiveOrThrow(id);
        roleService.setAdditionalRoles(id, roles);
        return getById(id);
    }

    @Transactional
    public void softDelete(Long id) {
        loadActiveOrThrow(id);
        userRepository.softDeleteById(id);
        audit.event("USER_DELETED", "userId=" + id);
    }

    @Transactional
    public void resendInvite(Long id) {
        accountService.resendActivation(id);
    }

    @Transactional
    public UserResponse setStatus(Long id, UserStatus status) {
        if (status == UserStatus.IS_DELETED) {
            throw new BusinessRuleException("Use DELETE to remove a user");
        }
        if (status == UserStatus.INVITED) {
            throw new BusinessRuleException("INVITED is set automatically on creation; use resend-invite instead");
        }
        User user = loadActiveOrThrow(id);
        if (user.getStatus() == UserStatus.INVITED && status == UserStatus.ACTIVE) {
            throw new BusinessRuleException("The user must activate their account through the emailed link");
        }
        user.setStatus(status);
        User saved = userRepository.save(user);
        audit.event("USER_STATUS_CHANGED", "userId=" + id + " status=" + status);
        return UserResponse.from(saved);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private User loadActiveOrThrow(Long id) {
        return userRepository.findByIdAndStatusNot(id, UserStatus.IS_DELETED)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    /**
     * Staff (ADMIN/ENGINEER) share the EMPxxxxx prefix; customer-side (CUSTOMER/CUSTOMER_ADMIN)
     * share CUSxxxxxx -- merged so a user's code doesn't change if their role changes within the
     * same side. Skips codes still held by soft-deleted users.
     */
    private String nextUserCode(Roles role) {
        boolean staff = role == Roles.ADMIN || role == Roles.ENGINEER;
        String prefix = staff ? "EMP" : "CUS";
        int digits = staff ? 5 : 6;
        long n = userRepository.count() + 1;
        String code;
        do {
            code = String.format("%s%0" + digits + "d", prefix, n++);
        } while (userRepository.existsByUserCode(code));
        return code;
    }
}
