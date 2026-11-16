package com.konohahelpdesk.controller.auth;

import com.konohahelpdesk.config.AppProperties;
import com.konohahelpdesk.dto.auth.ChangePasswordRequest;
import com.konohahelpdesk.dto.auth.ForgotPasswordRequest;
import com.konohahelpdesk.dto.auth.LoginRequest;
import com.konohahelpdesk.dto.auth.TokenPasswordRequest;
import com.konohahelpdesk.dto.subscription.FeatureResponse;
import com.konohahelpdesk.dto.user.UserResponse;
import com.konohahelpdesk.model.user.User;
import com.konohahelpdesk.security.AuditLogger;
import com.konohahelpdesk.security.CurrentUser;
import com.konohahelpdesk.service.subscription.CompanySubscriptionService;
import com.konohahelpdesk.security.JwtService;
import com.konohahelpdesk.service.auth.AccountService;
import com.konohahelpdesk.service.auth.AuthService;
import com.konohahelpdesk.service.user.UserService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AccountService accountService;
    private final JwtService jwtService;
    private final CurrentUser currentUser;
    private final UserService userService;
    private final AuditLogger audit;
    private final AppProperties.Cookie cookieCfg;
    private final CompanySubscriptionService subscriptions;
    private final com.konohahelpdesk.security.TokenRevocationService tokenRevocations;

    public AuthController(AuthService authService, AccountService accountService, JwtService jwtService,
                          CurrentUser currentUser, UserService userService, AuditLogger audit, AppProperties props,
                          CompanySubscriptionService subscriptions,
                          com.konohahelpdesk.security.TokenRevocationService tokenRevocations) {
        this.tokenRevocations = tokenRevocations;
        this.authService = authService;
        this.accountService = accountService;
        this.jwtService = jwtService;
        this.currentUser = currentUser;
        this.userService = userService;
        this.audit = audit;
        this.cookieCfg = props.getSecurity().getCookie();
        this.subscriptions = subscriptions;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        String token = authService.login(request.getEmail(), request.getPassword());

        ResponseCookie cookie = buildCookie(token, Duration.ofSeconds(jwtService.getExpirationSeconds()));
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.ok(Map.of("message", "Login successful"));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(jakarta.servlet.http.HttpServletRequest request, HttpServletResponse response) {
        audit.event("LOGOUT", null);
        // Clearing the cookie only stops THIS browser sending the token; revoking it stops a copy
        // of the token from being used for the rest of its lifetime.
        if (request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie c : request.getCookies()) {
                if (cookieCfg.getName().equals(c.getName())) tokenRevocations.revoke(c.getValue());
            }
        }
        ResponseCookie cookie = buildCookie("", Duration.ZERO);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.ok(Map.of("message", "Logged out"));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me() {
        User user = currentUser.get();
        // Goes through UserService (not a bare UserResponse.from) so customRoles —
        // and the privileges they carry — are populated; the frontend needs them to decide what
        // this user can see, same as the admin user listing does.
        UserResponse me = userService.getById(user.getId());
        // Customers additionally carry their plan's feature codes, so the portal can hide the
        // features their company doesn't have. Staff aren't plan-gated, so they get nothing here.
        if (user.getCompany() != null) {
            me.setCompanyFeatures(subscriptions.findResolved(user.getCompany().getId())
                .map(sub -> sub.getFeatures().stream()
                    .filter(f -> Boolean.TRUE.equals(f.getEnabled()))
                    .map(FeatureResponse::getCode)
                    .toList())
                .orElseGet(java.util.List::of));
        }
        return ResponseEntity.ok(me);
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                                              HttpServletResponse response) {
        String token = authService.changePassword(currentUser.get(), request.getCurrentPassword(), request.getNewPassword());
        // The old cookie is now void (issued before the change); keep THIS browser signed in.
        response.addHeader(HttpHeaders.SET_COOKIE,
            buildCookie(token, Duration.ofSeconds(jwtService.getExpirationSeconds())).toString());
        return ResponseEntity.ok(Map.of("message", "Password changed"));
    }

    // ── Anonymous account flows (links arrive by email) ──────────────────────

    /** Confirms the email and sets the user's own password. Token comes from the activation email. */
    @PostMapping("/activate")
    public ResponseEntity<Map<String, Object>> activate(@Valid @RequestBody TokenPasswordRequest request) {
        accountService.activate(request.getToken(), request.getPassword());
        return ResponseEntity.ok(Map.of("message", "Account activated. You can now log in."));
    }

    /** Always returns 200 so the response can't be used to discover which emails exist. */
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, Object>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        accountService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok(Map.of("message", "If an account exists for that email, a reset link has been sent."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(@Valid @RequestBody TokenPasswordRequest request) {
        accountService.resetPassword(request.getToken(), request.getPassword());
        return ResponseEntity.ok(Map.of("message", "Password updated. You can now log in."));
    }

    private ResponseCookie buildCookie(String value, Duration maxAge) {
        return ResponseCookie.from(cookieCfg.getName(), value)
            .httpOnly(true)
            .secure(cookieCfg.isSecure())
            .sameSite(cookieCfg.getSameSite())
            .path("/")
            .maxAge(maxAge)
            .build();
    }
}
