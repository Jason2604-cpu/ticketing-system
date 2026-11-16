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
import com.konohahelpdesk.model.ticket.Ticket;
import com.konohahelpdesk.model.ticket.TicketComment;
import com.konohahelpdesk.repository.ticket.TicketCommentRepository;
import com.konohahelpdesk.repository.ticket.TicketRepository;
import com.konohahelpdesk.repository.user.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import com.konohahelpdesk.security.AuditLogger;
import com.konohahelpdesk.service.company.EngineerAssignmentService;
import com.konohahelpdesk.service.rbac.RoleService;
import com.konohahelpdesk.util.generator.PasswordGenerator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
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
    private final TicketRepository ticketRepository;
    private final TicketCommentRepository ticketCommentRepository;

    public UserService(UserRepository userRepository,
                       CompanyRepository companyRepository,
                       PasswordEncoder passwordEncoder,
                       CompanySubscriptionService subscriptionService,
                       AccountService accountService,
                       EngineerAssignmentService assignments,
                       RoleService roleService,
                       AuditLogger audit,
                       TicketRepository ticketRepository,
                       TicketCommentRepository ticketCommentRepository) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.passwordEncoder = passwordEncoder;
        this.subscriptionService = subscriptionService;
        this.accountService = accountService;
        this.assignments = assignments;
        this.roleService = roleService;
        this.audit = audit;
        this.ticketRepository = ticketRepository;
        this.ticketCommentRepository = ticketCommentRepository;
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request, User actor) {
        // Inviting an Admin is granting the Admin role. The roles endpoint is Admin-only for that
        // reason; without the same rule here a MANAGE_USERS holder could invite an Admin account
        // to an address they control and sign in as it.
        if (request.getRole() == Roles.ADMIN && (actor == null || actor.getRole() != Roles.ADMIN)) {
            throw new AccessDeniedException("Only an admin can create an admin account");
        }
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

        // Customer-facing roles belong to a company; employee roles (Admin/Engineer/Default) don't.
        boolean requiresCompany = request.getRole().isCustomerSide();
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
        audit.event("USER_CREATED", AuditLogger.describe(saved) + " · role=" + saved.getRole()
            + (saved.getCompany() != null ? " · company=" + saved.getCompany().getName() : ""));

        accountService.sendActivation(saved);
        return UserResponse.from(saved);
    }

    @Transactional(readOnly = true)
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
        // Only Engineer / Default employees can hold custom roles — don't ask about anyone else.
        var customByUser = roleService.customRolesByUser(users.stream()
            .filter(u -> u.getRole().canHoldCustomRoles()).map(User::getId).toList());
        return users.stream().map(u -> {
            UserResponse r = UserResponse.from(u);
            if (u.getRole().worksTickets()) {
                r.setAssignedCompanies(byEngineer.getOrDefault(u.getId(), List.of()));
            }
            r.setCustomRoles(customByUser.getOrDefault(u.getId(), List.of()));
            return r;
        }).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        User user = loadActiveOrThrow(id);
        UserResponse r = UserResponse.from(user);
        // Also behind every page load (/auth/me): skip the lookup for roles that can't hold any.
        r.setCustomRoles(user.getRole().canHoldCustomRoles() ? roleService.customRoles(id) : List.of());
        return r;
    }

    /**
     * ADMIN-only: what the Users page "Roles" checkboxes save — Admin / Engineer (nothing = Default)
     * plus the custom roles. Moving someone to Default also takes away what only engineers hold:
     * their company assignments and any "main engineer" slot, so they stop being notified about
     * tickets they can no longer open.
     */
    @Transactional
    public UserResponse setRoles(Long id, Set<Roles> roles, Set<Long> customRoleIds) {
        User user = loadActiveOrThrow(id);
        Roles previous = roleService.setStaffRoles(id, roles, customRoleIds);
        if (user.getRole() == Roles.STAFF && previous != Roles.STAFF) {
            assignments.clearForEngineer(id);
            int released = companyRepository.clearMainEngineer(id);
            if (released > 0) {
                audit.event("MAIN_ENGINEER_RELEASED", AuditLogger.describe(user) + " · companies=" + released + " · moved to Default");
            }
        }
        return getById(id);
    }

    @Transactional
    public void softDelete(Long id, User actor) {
        User user = loadActiveOrThrow(id);
        assertNotSelf(user, actor, "delete your own account");
        assertMayManage(user, actor);
        assertNotLastAdmin(user, "Deleting");
        releaseWork(user, true);
        userRepository.softDeleteById(id);
        audit.event("USER_DELETED", AuditLogger.describe(user) + " · role=" + user.getRole());
    }

    /**
     * Refuses an action against the acting user's own account. Separate from the last-admin check
     * because it applies even when other admins exist: an admin locking themselves out by accident
     * is still an incident, and the fix ("ask another admin") is worse than the prevention.
     */
    private void assertNotSelf(User target, User actor, String what) {
        if (actor != null && target.getId().equals(actor.getId())) {
            throw new BusinessRuleException("You can't " + what + " — ask another admin to do it.");
        }
    }

    /**
     * Refuses removing the only way back in. Counts ACTIVE admins. (Changing an admin's roles can't
     * strand the system either: nobody may change their own roles, so whoever demotes an admin is
     * themselves an admin who stays one.)
     */
    /** Admin accounts are only deleted, deactivated or reactivated by another admin. */
    private void assertMayManage(User target, User actor) {
        if (target.getRole() == Roles.ADMIN && (actor == null || actor.getRole() != Roles.ADMIN)) {
            throw new AccessDeniedException("Only an admin can change an admin account");
        }
    }

    /**
     * Takes an engineer/admin who is leaving (deleted or deactivated) out of the places new work
     * is routed through: main-engineer slots, and the tickets they were still working, which go
     * back to the unassigned queue with a note. Otherwise new tickets keep notifying, and
     * scheduled-visit tickets keep being assigned to, an account nobody can sign in to.
     */
    private void releaseWork(User user, boolean alsoCompanyAssignments) {
        if (!user.getRole().worksTickets()) return;
        int released = companyRepository.clearMainEngineer(user.getId());
        List<Ticket> open = ticketRepository.findActiveByAssigneeId(user.getId());
        for (Ticket t : open) {
            t.setAssignee(null);
            ticketRepository.save(t);
            TicketComment note = new TicketComment();
            note.setTicket(t);
            note.setSystem(true);
            note.setBody("Unassigned — " + user.getFirstName() + " " + user.getLastName() + "'s account is no longer active.");
            ticketCommentRepository.save(note);
        }
        if (alsoCompanyAssignments) assignments.clearForEngineer(user.getId());
        if (released > 0 || !open.isEmpty()) {
            audit.event("ENGINEER_WORK_RELEASED", AuditLogger.describe(user)
                + " · mainEngineerOf=" + released + " · ticketsUnassigned=" + open.size());
        }
    }

    private void assertNotLastAdmin(User target, String verb) {
        if (target.getRole() != Roles.ADMIN || target.getStatus() != UserStatus.ACTIVE) return;
        long remaining = userRepository.findByRoleAndStatus(Roles.ADMIN, UserStatus.ACTIVE).stream()
            .filter(u -> !u.getId().equals(target.getId()))
            .count();
        if (remaining == 0) {
            throw new BusinessRuleException(verb + " the last active admin would lock everyone out of "
                + "the admin area. Promote another admin first.");
        }
    }

    @Transactional
    public void resendInvite(Long id) {
        accountService.resendActivation(id);
    }

    /**
     * The two guards below are why this takes the actor.
     *
     * Nothing stopped the last remaining admin from deactivating or deleting themselves, which
     * locks EVERY admin surface in the product — role management, plans, users — behind an account
     * that no longer exists. The only recovery would be editing the database or a restart after
     * the last admin is deleted (AdminBootstrap then re-creates admin@admin.com), so this is
     * refused rather than warned about.
     */
    @Transactional
    public UserResponse setStatus(Long id, UserStatus status, User actor) {
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
        if (status != UserStatus.ACTIVE) {
            assertNotSelf(user, actor, "deactivate your own account");
            assertNotLastAdmin(user, "Deactivating");
        }
        assertMayManage(user, actor);
        // Company assignments are kept, so reactivating the account restores its scope.
        if (status != UserStatus.ACTIVE) releaseWork(user, false);
        UserStatus previous = user.getStatus();
        user.setStatus(status);
        User saved = userRepository.save(user);
        audit.event("USER_STATUS_CHANGED", AuditLogger.describe(saved) + " · " + previous + " -> " + status);
        return UserResponse.from(saved);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private User loadActiveOrThrow(Long id) {
        return userRepository.findByIdAndStatusNot(id, UserStatus.IS_DELETED)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    /**
     * Staff (ADMIN/ENGINEER) share the EMPxxxxx prefix; customer-side (CUSTOMER/CUSTOMER_ADMIN)
     * share CUSxxxxxx — merged so a user's code doesn't change if their role changes within the
     * same side. Skips codes still held by soft-deleted users.
     */
    private String nextUserCode(Roles role) {
        boolean staff = role.isStaffSide();
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
