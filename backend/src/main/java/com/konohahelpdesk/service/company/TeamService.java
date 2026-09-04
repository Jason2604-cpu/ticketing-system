package com.konohahelpdesk.service.company;

import com.konohahelpdesk.dto.user.InviteTeamMemberRequest;
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
import com.konohahelpdesk.service.auth.AccountService;
import com.konohahelpdesk.service.subscription.CompanySubscriptionService;
import com.konohahelpdesk.util.generator.PasswordGenerator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Company-scoped user management: what a CUSTOMER_ADMIN can do for their own
 * company (and an ADMIN for any company). Never crosses company boundaries —
 * every method takes the company id and verifies the target user belongs to it.
 */
@Service
public class TeamService {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final CompanySubscriptionService subscriptionService;
    private final AccountService accountService;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogger audit;

    public TeamService(UserRepository userRepository, CompanyRepository companyRepository,
                       CompanySubscriptionService subscriptionService, AccountService accountService,
                       PasswordEncoder passwordEncoder, AuditLogger audit) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.subscriptionService = subscriptionService;
        this.accountService = accountService;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    public List<UserResponse> members(Long companyId) {
        return userRepository.findByCompanyIdAndStatusNot(companyId, UserStatus.IS_DELETED).stream()
            .map(UserResponse::from).toList();
    }

    @Transactional
    public UserResponse invite(Long companyId, InviteTeamMemberRequest req, User actor) {
        Company company = companyRepository.findById(companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Company not found: " + companyId));
        String email = req.getEmail().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCaseAndStatusNot(email, UserStatus.IS_DELETED)) {
            throw new DuplicateResourceException("A user with this email already exists");
        }
        subscriptionService.assertUserSeatAvailable(companyId);

        User user = new User();
        user.setFirstName(req.getFirstName().trim());
        user.setLastName(req.getLastName().trim());
        user.setEmail(email);
        user.setPhone(req.getPhone().trim());
        user.setRole(req.isAdmin() ? Roles.CUSTOMER_ADMIN : Roles.CUSTOMER);
        user.setCompany(company);
        user.setUserCode(nextCode(user.getRole()));
        user.setPassword(passwordEncoder.encode(PasswordGenerator.generate(32)));
        user.setStatus(UserStatus.INVITED);
        User saved = userRepository.save(user);

        audit.event("TEAM_MEMBER_INVITED", "companyId=" + companyId + " userId=" + saved.getId() + " by=" + actor.getId());
        accountService.sendActivation(saved);
        return UserResponse.from(saved);
    }

    @Transactional
    public void resendInvite(Long companyId, Long userId) {
        loadMember(companyId, userId);
        accountService.resendActivation(userId);
    }

    @Transactional
    public UserResponse setStatus(Long companyId, Long userId, UserStatus status, User actor) {
        if (status != UserStatus.ACTIVE && status != UserStatus.INACTIVE) {
            throw new BusinessRuleException("Status must be ACTIVE or INACTIVE");
        }
        if (userId.equals(actor.getId())) {
            throw new BusinessRuleException("You cannot change your own status");
        }
        User user = loadMember(companyId, userId);
        if (user.getStatus() == UserStatus.INVITED) {
            throw new BusinessRuleException("The user must activate their account through the emailed link first");
        }
        user.setStatus(status);
        audit.event("TEAM_MEMBER_STATUS", "companyId=" + companyId + " userId=" + userId + " status=" + status);
        return UserResponse.from(userRepository.save(user));
    }

    private User loadMember(Long companyId, Long userId) {
        User user = userRepository.findByIdAndStatusNot(userId, UserStatus.IS_DELETED)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        if (user.getCompany() == null || !user.getCompany().getId().equals(companyId)) {
            throw new ResourceNotFoundException("User not found: " + userId); // don't reveal it exists elsewhere
        }
        return user;
    }

    /** CUSTOMER and CUSTOMER_ADMIN share the CUSxxxxxx prefix (see UserService.nextUserCode). */
    private String nextCode(Roles role) {
        long n = userRepository.count() + 1;
        String code;
        do { code = String.format("CUS%06d", n++); } while (userRepository.existsByUserCode(code));
        return code;
    }
}
