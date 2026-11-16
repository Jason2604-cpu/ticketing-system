package com.konohahelpdesk.config;

import com.konohahelpdesk.repository.rbac.UserRoleRepository;
import com.konohahelpdesk.repository.user.UserRepository;
import com.konohahelpdesk.security.AuditLogger;
import com.konohahelpdesk.security.JwtCookieFilter;
import com.konohahelpdesk.security.JwtService;
import com.konohahelpdesk.security.RateLimitFilter;
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

    public SecurityConfig(AppProperties props, JwtService jwtService, UserRepository userRepository,
                          UserRoleRepository userRoleRepository, AuditLogger auditLogger) {
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

            .addFilterBefore(new RateLimitFilter(props, auditLogger), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new JwtCookieFilter(jwtService, userRepository, userRoleRepository, props), UsernamePasswordAuthenticationFilter.class)

            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // Anonymous: login + the email-link flows (all POST, all rate-limited).
                .requestMatchers(HttpMethod.POST,
                    "/api/auth/login", "/api/auth/activate",
                    "/api/auth/forgot-password", "/api/auth/reset-password").permitAll()
                // Every other route needs an ACTIVE user with a real role; fine-grained checks are @PreAuthorize on controllers.
                .requestMatchers("/api/**").hasAnyRole("ADMIN", "ENGINEER", "CUSTOMER_ADMIN", "CUSTOMER")
                .anyRequest().denyAll()
            )

            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, ex) ->
                    sendJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"))
                .accessDeniedHandler((request, response, ex) ->
                    sendJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden"))
            );

        return http.build();
    }

    private void sendJsonError(HttpServletResponse response, int status, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(
            "{\"timestamp\":\"" + Instant.now() + "\",\"status\":" + status + ",\"error\":\"" + message + "\"}"
        );
    }
}
