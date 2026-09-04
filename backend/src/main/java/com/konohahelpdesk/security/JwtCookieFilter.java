package com.konohahelpdesk.security;

import com.konohahelpdesk.config.AppProperties;
import com.konohahelpdesk.model.rbac.UserRole;
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

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final String cookieName;

    public JwtCookieFilter(JwtService jwtService, UserRepository userRepository, UserRoleRepository userRoleRepository, AppProperties props) {
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

            if (claims != null) {
                Long userId = parseUserId(claims.getSubject());
                Optional<User> userOpt = userId == null
                    ? Optional.empty()
                    : userRepository.findByIdAndStatusNot(userId, UserStatus.IS_DELETED);

                if (userOpt.isPresent()) {
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
     * Null means "do not authenticate". Includes the user's primary role plus any additive roles
     * granted via the Roles/UserRoles tables (see RoleService) -- an ENGINEER granted ADMIN gets
     * both ROLE_ENGINEER and ROLE_ADMIN, satisfying @PreAuthorize checks for either.
     */
    private List<GrantedAuthority> authoritiesFor(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            return null; // INVITED (not yet activated), INACTIVE, IS_DELETED
        }
        java.util.LinkedHashSet<String> roleNames = new java.util.LinkedHashSet<>();
        roleNames.add(user.getRole().name());
        for (UserRole ur : userRoleRepository.findByUserId(user.getId())) {
            roleNames.add(ur.getRole().getName());
        }
        return roleNames.stream()
            .map(name -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + name))
            .toList();
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
