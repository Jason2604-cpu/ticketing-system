package com.konohahelpdesk.security;

import com.konohahelpdesk.config.AppProperties;
import com.konohahelpdesk.model.rbac.Privilege;
import com.konohahelpdesk.model.rbac.Role;
import com.konohahelpdesk.model.user.User;
import com.konohahelpdesk.model.user.UserStatus;
import com.konohahelpdesk.repository.rbac.UserRoleRepository;
import com.konohahelpdesk.repository.user.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Reads the auth cookie on every request and, if valid, authenticates it.
 *
 * Every request re-loads the user from the database so a deactivated / deleted
 * account loses access immediately, regardless of the token's remaining lifetime.
 *
 * Only ACTIVE users are authenticated. INVITED users must first activate via the
 * emailed link; INACTIVE / deleted users are refused outright.
 *
 * Registered in {@link com.konohahelpdesk.config.SecurityConfig} (not a @Component,
 * so the servlet container doesn't register it a second time).
 */
public class JwtCookieFilter extends OncePerRequestFilter {

    /** The only authority a user with {@code mustChangePassword} gets. */
    public static final String PASSWORD_CHANGE_REQUIRED = "PASSWORD_CHANGE_REQUIRED";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final String cookieName;
    private final TokenRevocationService revocations;

    public JwtCookieFilter(JwtService jwtService, UserRepository userRepository, UserRoleRepository userRoleRepository,
                           TokenRevocationService revocations, AppProperties props) {
        this.revocations = revocations;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.cookieName = props.getSecurity().getCookie().getName();
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {

        String token = readCookie(request, cookieName);

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            Claims claims = jwtService.parseClaims(token);

            // A token that was signed out (POST /api/auth/logout) is refused even though its
            // signature and expiry are still good.
            if (claims != null && !revocations.isRevoked(token)) {
                Long userId = parseUserId(claims.getSubject());
                Optional<User> userOpt = userId == null
                    ? Optional.empty()
                    : userRepository.findByIdAndStatusNot(userId, UserStatus.IS_DELETED);

                if (userOpt.isPresent() && !issuedBeforePasswordChange(claims, userOpt.get())) {
                    User user = userOpt.get();
                    List<GrantedAuthority> authorities = authoritiesFor(user);

                    if (authorities != null) {
                        var authToken = new UsernamePasswordAuthenticationToken(user, null, authorities);
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Null means "do not authenticate". One ROLE_* for the user's role (ADMIN, ENGINEER, STAFF =
     * Default, CUSTOMER_ADMIN, CUSTOMER) plus, for every custom (Role Management) role granted, a
     * PRIV_* authority per privilege it carries. Real enforcement: a custom role's ticked
     * privileges only ever add access, never a label.
     */
    private List<GrantedAuthority> authoritiesFor(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            return null; // INVITED (not yet activated), INACTIVE, IS_DELETED
        }
        if (user.isMustChangePassword()) {
            // Signed in, but with a password they didn't choose (the default admin). No role at all,
            // so every /api route except /auth/me, /auth/change-password and /auth/logout is 403
            // until the password is replaced — see SecurityConfig.
            return List.of(new SimpleGrantedAuthority(PASSWORD_CHANGE_REQUIRED));
        }
        java.util.LinkedHashSet<GrantedAuthority> authorities = new java.util.LinkedHashSet<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        // Admins already pass every privilege check and customers never hold custom roles, so only
        // Engineer / Default employees pay for the privilege lookup — one query fewer on most requests.
        if (!user.getRole().canHoldCustomRoles()) return List.copyOf(authorities);
        for (Role role : userRoleRepository.findRolesWithPrivilegesByUserId(user.getId())) {
            // Only custom roles carry privileges. (System-role rows were never meant to be granted
            // since V3; ignoring any that slip in keeps a stray row from escalating anyone.)
            if (!role.isCustom()) continue;
            for (Privilege p : role.getPrivileges()) {
                authorities.add(new SimpleGrantedAuthority("PRIV_" + p.name()));
            }
        }
        return List.copyOf(authorities);
    }

    /**
     * True when the token predates the user's last password change / reset — e.g. a session opened
     * with the default admin/admin password before it was replaced. Compared at whole seconds,
     * the precision of the JWT "iat" claim.
     */
    private boolean issuedBeforePasswordChange(Claims claims, User user) {
        if (user.getPasswordChangedAt() == null) return false;
        java.util.Date issuedAt = claims.getIssuedAt();
        if (issuedAt == null) return true;
        long changed = user.getPasswordChangedAt().atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
        return issuedAt.toInstant().getEpochSecond() < changed;
    }

    private Long parseUserId(String subject) {
        try {
            return subject == null ? null : Long.valueOf(subject);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
