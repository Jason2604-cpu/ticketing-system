package com.konohahelpdesk.config;

import com.konohahelpdesk.repository.rbac.UserRoleRepository;
import com.konohahelpdesk.repository.user.UserRepository;
import com.konohahelpdesk.security.AuditLogger;
import com.konohahelpdesk.security.JwtCookieFilter;
import com.konohahelpdesk.security.JwtService;
import com.konohahelpdesk.security.RateLimitFilter;
import com.konohahelpdesk.store.RateLimitStore;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Instant;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final AppProperties props;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuditLogger auditLogger;
    private final RateLimitStore rateLimitStore;
    private final com.konohahelpdesk.security.TokenRevocationService tokenRevocations;

    public SecurityConfig(AppProperties props, JwtService jwtService, UserRepository userRepository,
                          UserRoleRepository userRoleRepository, AuditLogger auditLogger,
                          RateLimitStore rateLimitStore,
                          com.konohahelpdesk.security.TokenRevocationService tokenRevocations) {
        this.tokenRevocations = tokenRevocations;
        this.rateLimitStore = rateLimitStore;
        this.props = props;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.auditLogger = auditLogger;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        AppProperties.Cors cors = props.getSecurity().getCors();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(cors.getAllowedOrigins());
        config.setAllowedMethods(cors.getAllowedMethods());
        config.setAllowedHeaders(cors.getAllowedHeaders());
        config.setAllowCredentials(cors.isAllowCredentials());
        config.setMaxAge(cors.getMaxAgeSeconds());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        AppProperties.Headers hdr = props.getSecurity().getHeaders();

        http
            // Stateless JWT-in-HttpOnly-cookie auth. CSRF is mitigated by SameSite=Lax/Strict on the
            // cookie (browsers don't attach it to cross-site POST/PUT/DELETE) plus a strict CORS origin list.
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Security headers (Replit "basic security headers" / vibe-security §9).
            .headers(headers -> headers
                .frameOptions(frame -> frame.deny())
                .contentTypeOptions(Customizer.withDefaults())
                .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .contentSecurityPolicy(csp -> csp.policyDirectives(hdr.getContentSecurityPolicy()))
                .httpStrictTransportSecurity(hsts -> {
                    if (hdr.isHstsEnabled()) {
                        hsts.maxAgeInSeconds(hdr.getHstsMaxAgeSeconds())
                            .includeSubDomains(hdr.isHstsIncludeSubdomains());
                    } else {
                        hsts.disable();
                    }
                })
            )

            .addFilterBefore(new RateLimitFilter(props, auditLogger, rateLimitStore, jwtService), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new JwtCookieFilter(jwtService, userRepository, userRoleRepository, tokenRevocations, props), UsernamePasswordAuthenticationFilter.class)

            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // Container healthcheck (docker compose). Health only — nginx doesn't route /actuator.
                .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                // Anonymous: login + the email-link flows (all POST, all rate-limited).
                .requestMatchers(HttpMethod.POST,
                    "/api/auth/login", "/api/auth/activate",
                    "/api/auth/forgot-password", "/api/auth/reset-password").permitAll()
                // Reachable by any signed-in user — including one who must still replace the default
                // password (JwtCookieFilter gives them no role, so everything below is closed to them).
                .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/auth/change-password", "/api/auth/logout").authenticated()
                // Every other route needs an ACTIVE user with a role; fine-grained checks are @PreAuthorize on
                // controllers. STAFF (the Default employee role) passes this gate but every module controller
                // shuts it out unless a custom role grants the matching PRIV_* (tickets/reports: never).
                .requestMatchers("/api/**").hasAnyRole("ADMIN", "ENGINEER", "CUSTOMER_ADMIN", "CUSTOMER", "STAFF")
                .anyRequest().denyAll()
            )

            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, ex) ->
                    sendJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"))
                .accessDeniedHandler((request, response, ex) ->
                    sendJsonError(response, HttpServletResponse.SC_FORBIDDEN,
                        mustChangePassword() ? "Password change required" : "Forbidden"))
            );

        return http.build();
    }

    private static boolean mustChangePassword() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
            .anyMatch(a -> JwtCookieFilter.PASSWORD_CHANGE_REQUIRED.equals(a.getAuthority()));
    }

    private void sendJsonError(HttpServletResponse response, int status, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(
            "{\"timestamp\":\"" + Instant.now() + "\",\"status\":" + status + ",\"error\":\"" + message + "\"}"
        );
    }
}
