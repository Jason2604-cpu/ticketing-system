package com.konohahelpdesk.controller.user;

import com.konohahelpdesk.model.user.User;

import com.konohahelpdesk.dto.user.CreateUserRequest;
import com.konohahelpdesk.dto.user.UserResponse;
import com.konohahelpdesk.model.user.Roles;
import com.konohahelpdesk.model.user.UserStatus;
import com.konohahelpdesk.security.CurrentUser;
import com.konohahelpdesk.service.company.EngineerAssignmentService;
import com.konohahelpdesk.service.media.ProfileMediaService;
import com.konohahelpdesk.service.user.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** User administration. ADMIN, or anyone holding the MANAGE_USERS privilege — enforced per method, not just at the URL level. */
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('PRIV_MANAGE_USERS')")
public class UserController {

    private final UserService userService;
    private final ProfileMediaService profileMedia;
    private final EngineerAssignmentService assignments;
    private final CurrentUser currentUser;

    public UserController(UserService userService, ProfileMediaService profileMedia,
                          EngineerAssignmentService assignments, CurrentUser currentUser) {
        this.userService = userService;
        this.profileMedia = profileMedia;
        this.assignments = assignments;
        this.currentUser = currentUser;
    }

    /** Replace the set of companies an engineer/admin is assigned to. */
    @PutMapping("/{id}/companies")
    public ResponseEntity<List<UserResponse.CompanyRef>> setCompanies(@PathVariable Long id, @RequestBody java.util.Set<Long> companyIds) {
        return ResponseEntity.ok(assignments.setCompaniesForEngineer(id, companyIds == null ? java.util.Set.of() : companyIds));
    }

    /** Replace the additive roles granted to a user, on top of their primary role (e.g. grant an ENGINEER admin privileges too). */
    @PutMapping("/{id}/roles")
    public ResponseEntity<UserResponse> setRoles(@PathVariable Long id, @RequestBody java.util.Set<Roles> roles) {
        return ResponseEntity.ok(userService.setAdditionalRoles(id, roles == null ? java.util.Set.of() : roles));
    }

    /** Replace the custom (Role Management) roles granted to a user — staff only. */
    @PutMapping("/{id}/custom-roles")
    public ResponseEntity<UserResponse> setCustomRoles(@PathVariable Long id, @RequestBody java.util.Set<Long> roleIds) {
        return ResponseEntity.ok(userService.setCustomRoles(id, roleIds == null ? java.util.Set.of() : roleIds));
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse created = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> list(
        @RequestParam(required = false) Roles role,
        @RequestParam(required = false) UserStatus status
    ) {
        return ResponseEntity.ok(userService.listUsers(role, status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getById(id));
    }

    /** Re-sends the activation email (new link; the previous one stops working). */
    @PostMapping("/{id}/resend-invite")
    public ResponseEntity<Void> resendInvite(@PathVariable Long id) {
        userService.resendInvite(id);
        return ResponseEntity.accepted().build();
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<UserResponse> setStatus(@PathVariable Long id, @RequestParam UserStatus status) {
        return ResponseEntity.ok(userService.setStatus(id, status));
    }

    /** Admin sets/replaces a user's photo (users manage their own via /api/profile/photo). */
    @PutMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserResponse> uploadPhoto(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(profileMedia.setUserPhoto(id, file, currentUser.get().getId()));
    }

    @DeleteMapping("/{id}/photo")
    public ResponseEntity<UserResponse> deletePhoto(@PathVariable Long id) {
        return ResponseEntity.ok(profileMedia.removeUserPhoto(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        userService.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
