package com.konohahelpdesk.controller.company;

import com.konohahelpdesk.dto.user.InviteTeamMemberRequest;
import com.konohahelpdesk.dto.user.UserResponse;
import com.konohahelpdesk.model.user.Roles;
import com.konohahelpdesk.model.user.User;
import com.konohahelpdesk.model.user.UserStatus;
import com.konohahelpdesk.security.CurrentUser;
import com.konohahelpdesk.service.company.EngineerAssignmentService;
import com.konohahelpdesk.service.company.TeamService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Company-scoped people: the "Team Members" page for a CUSTOMER_ADMIN, the
 * "Contacts" card for staff, and the engineers assigned to a company.
 *
 * Read: anyone with access to the company. Write: ADMIN, or a CUSTOMER_ADMIN of THAT company.
 */
@RestController
@RequestMapping("/api/companies/{companyId}")
public class TeamController {

    private final TeamService teamService;
    private final EngineerAssignmentService assignments;
    private final CurrentUser currentUser;

    public TeamController(TeamService teamService, EngineerAssignmentService assignments, CurrentUser currentUser) {
        this.teamService = teamService;
        this.assignments = assignments;
        this.currentUser = currentUser;
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> members(@PathVariable Long companyId) {
        currentUser.requireCompanyAccess(companyId);
        return ResponseEntity.ok(teamService.members(companyId));
    }

    @PostMapping("/users")
    public ResponseEntity<UserResponse> invite(@PathVariable Long companyId, @Valid @RequestBody InviteTeamMemberRequest request) {
        User actor = requireTeamManager(companyId);
        return ResponseEntity.status(HttpStatus.CREATED).body(teamService.invite(companyId, request, actor));
    }

    @PostMapping("/users/{userId}/resend-invite")
    public ResponseEntity<Void> resend(@PathVariable Long companyId, @PathVariable Long userId) {
        requireTeamManager(companyId);
        teamService.resendInvite(companyId, userId);
        return ResponseEntity.accepted().build();
    }

    @PatchMapping("/users/{userId}/status")
    public ResponseEntity<UserResponse> setStatus(@PathVariable Long companyId, @PathVariable Long userId, @RequestParam UserStatus status) {
        User actor = requireTeamManager(companyId);
        return ResponseEntity.ok(teamService.setStatus(companyId, userId, status, actor));
    }

    /** Toggle a member between Customer and Company Admin — kept in sync across the admin and customer portals alike. */
    @PatchMapping("/users/{userId}/admin")
    public ResponseEntity<UserResponse> setAdmin(@PathVariable Long companyId, @PathVariable Long userId, @RequestParam boolean admin) {
        User actor = requireTeamManager(companyId);
        return ResponseEntity.ok(teamService.setAdmin(companyId, userId, admin, actor));
    }

    @GetMapping("/engineers")
    public ResponseEntity<List<UserResponse>> engineers(@PathVariable Long companyId) {
        currentUser.requireCompanyAccess(companyId);
        return ResponseEntity.ok(assignments.engineersForCompany(companyId));
    }

    private User requireTeamManager(Long companyId) {
        User user = currentUser.get();
        if (user.getRole() == Roles.ADMIN) return user;
        if (user.getRole() == Roles.CUSTOMER_ADMIN && user.getCompany() != null && user.getCompany().getId().equals(companyId)) return user;
        throw new AccessDeniedException("Forbidden");
    }
}
