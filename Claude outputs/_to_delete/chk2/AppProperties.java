package com.konohahelpdesk.config;

import com.konohahelpdesk.model.plan.Feature;
import com.konohahelpdesk.model.plan.Plan;

import com.konohahelpdesk.model.plan.SlaPriority;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * All tunable application parameters, bound from {@code app-config.properties}
 * (imported by application.properties). Change values there — not in code.
 *
 * Every property is validated at startup; a bad value fails fast with a clear message.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    @Valid @NotNull private Security security = new Security();
    @Valid @NotNull private Subscription subscription = new Subscription();
    @Valid @NotNull private Mail mail = new Mail();
    @Valid @NotNull private Media media = new Media();
    @Valid @NotNull private Bootstrap bootstrap = new Bootstrap();
    @Valid @NotNull private SlaMonitoring sla = new SlaMonitoring();
    @Valid @NotNull private Contracts contracts = new Contracts();
    @Valid @NotNull private Audit audit = new Audit();

    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }

    public Subscription getSubscription() { return subscription; }
    public void setSubscription(Subscription subscription) { this.subscription = subscription; }

    public Mail getMail() { return mail; }
    public void setMail(Mail mail) { this.mail = mail; }

    public Media getMedia() { return media; }
    public void setMedia(Media media) { this.media = media; }

    public Bootstrap getBootstrap() { return bootstrap; }
    public void setBootstrap(Bootstrap bootstrap) { this.bootstrap = bootstrap; }

    public SlaMonitoring getSla() { return sla; }
    public void setSla(SlaMonitoring sla) { this.sla = sla; }

    public Contracts getContracts() { return contracts; }
    public void setContracts(Contracts contracts) { this.contracts = contracts; }

    public Audit getAudit() { return audit; }
    public void setAudit(Audit audit) { this.audit = audit; }

    // ─────────────────────────────────────────────────────────────────────────
    // app.sla.*  — the background sweep that detects and escalates SLA breaches
    // ─────────────────────────────────────────────────────────────────────────
    /** Named SlaMonitoring, not Sla — {@code AppProperties.Sla} is already the per-plan SLA target. */
    public static class SlaMonitoring {
        /** Master switch for the background breach sweep. */
        private boolean monitorEnabled = true;
        /** How often the sweep runs, in milliseconds. */
        @Min(10_000) private long checkIntervalMs = 300_000L;   // 5 minutes
        /** Max tickets marked per sweep — a safety valve so one pass can't email hundreds of people. */
        @Min(1) private int batchSize = 200;
        /** A ticket is "at risk" once this much of its window has elapsed (percent). Display only. */
        @Min(1) private int atRiskPercent = 80;
        /** Email the assignee when their ticket breaches. */
        private boolean emailAssignee = true;
        /** Email the company's main engineer (and use them as the fallback when unassigned). */
        private boolean emailMainEngineer = true;
        /** Also email every active admin, but only for these priorities. Empty = never. */
        private List<String> escalateToAdminsForPriorities = new ArrayList<>(List.of("CRITICAL", "HIGH"));

        public boolean isMonitorEnabled() { return monitorEnabled; }
        public void setMonitorEnabled(boolean monitorEnabled) { this.monitorEnabled = monitorEnabled; }
        public long getCheckIntervalMs() { return checkIntervalMs; }
        public void setCheckIntervalMs(long checkIntervalMs) { this.checkIntervalMs = checkIntervalMs; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
        public int getAtRiskPercent() { return atRiskPercent; }
        public void setAtRiskPercent(int atRiskPercent) { this.atRiskPercent = atRiskPercent; }
        public boolean isEmailAssignee() { return emailAssignee; }
        public void setEmailAssignee(boolean emailAssignee) { this.emailAssignee = emailAssignee; }
        public boolean isEmailMainEngineer() { return emailMainEngineer; }
        public void setEmailMainEngineer(boolean emailMainEngineer) { this.emailMainEngineer = emailMainEngineer; }
        public List<String> getEscalateToAdminsForPriorities() { return escalateToAdminsForPriorities; }
        public void setEscalateToAdminsForPriorities(List<String> v) { this.escalateToAdminsForPriorities = v; }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // app.audit.*  — the persisted audit trail behind the admin viewer
    // ─────────────────────────────────────────────────────────────────────────
    public static class Audit {
        /**
         * Delete audit rows older than this many days. 0 keeps everything forever — pick that only
         * if something else is archiving the table, because it grows monotonically.
         */
        @Min(0) private int retentionDays = 365;
        /** How often the retention prune runs. Daily; there is nothing to gain from more. */
        @Min(60_000) private long pruneIntervalMs = 86_400_000L;
        /** Rows the viewer may return in one page. Also caps a hand-typed size= query param. */
        @Min(1) private int maxPageSize = 200;
        /** Rows one CSV export may contain, so a wide date range can't stream the whole table. */
        @Min(1) private int maxExportRows = 20_000;

        public int getRetentionDays() { return retentionDays; }
        public void setRetentionDays(int retentionDays) { this.retentionDays = retentionDays; }
        public long getPruneIntervalMs() { return pruneIntervalMs; }
        public void setPruneIntervalMs(long pruneIntervalMs) { this.pruneIntervalMs = pruneIntervalMs; }
        public int getMaxPageSize() { return maxPageSize; }
        public void setMaxPageSize(int maxPageSize) { this.maxPageSize = maxPageSize; }
        public int getMaxExportRows() { return maxExportRows; }
        public void setMaxExportRows(int maxExportRows) { this.maxExportRows = maxExportRows; }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // app.contracts.*  — recurring service contracts: the daily sweep that raises
    // maintenance tickets ahead of each scheduled visit and mails renewal reminders
    // ─────────────────────────────────────────────────────────────────────────
    public static class Contracts {
        /** Master switch for the daily contract sweep (auto-tickets + renewal reminders). */
        private boolean monitorEnabled = true;
        /** How often the sweep runs, in milliseconds. Daily is plenty — visits are dated, not timed. */
        @Min(60_000) private long checkIntervalMs = 86_400_000L;   // 24 hours
        /** Delay before the first sweep after start-up, so it doesn't fight the boot seeders. */
        @Min(1_000) private long initialDelayMs = 120_000L;         // 2 minutes
        /** A contract inside this many days of its end date reads as EXPIRING_SOON. Display only. */
        @Min(1) private int expiringSoonDays = 60;
        /**
         * Ceiling on a contract's own ticketLeadDays. Also the horizon the sweep queries, so a
         * typo'd 3000-day lead can't drag every future visit into today's batch.
         */
        @Min(1) private int maxTicketLeadDays = 90;
        /** Max visits ticketed per sweep — one pass must never be able to raise hundreds. */
        @Min(1) private int batchSize = 100;
        /** Email the contract owner (and the company's main engineer) about an upcoming renewal. */
        private boolean emailRenewalReminders = true;

        public boolean isMonitorEnabled() { return monitorEnabled; }
        public void setMonitorEnabled(boolean monitorEnabled) { this.monitorEnabled = monitorEnabled; }
        public long getCheckIntervalMs() { return checkIntervalMs; }
        public void setCheckIntervalMs(long checkIntervalMs) { this.checkIntervalMs = checkIntervalMs; }
        public long getInitialDelayMs() { return initialDelayMs; }
        public void setInitialDelayMs(long initialDelayMs) { this.initialDelayMs = initialDelayMs; }
        public int getExpiringSoonDays() { return expiringSoonDays; }
        public void setExpiringSoonDays(int expiringSoonDays) { this.expiringSoonDays = expiringSoonDays; }
        public int getMaxTicketLeadDays() { return maxTicketLeadDays; }
        public void setMaxTicketLeadDays(int maxTicketLeadDays) { this.maxTicketLeadDays = maxTicketLeadDays; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
        public boolean isEmailRenewalReminders() { return emailRenewalReminders; }
        public void setEmailRenewalReminders(boolean emailRenewalReminders) { this.emailRenewalReminders = emailRenewalReminders; }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // app.bootstrap.*  — first admin account (only used while NO admin exists)
    // ─────────────────────────────────────────────────────────────────────────
    public static class Bootstrap {
        /** Email of the initial admin. Empty = skip bootstrap. */
        private String adminEmail = "";
        /** Initial password (must satisfy the password policy). Change it after first login. */
        private String adminPassword = "";
        private String adminFirstName = "System";
        private String adminLastName = "Admin";
        private String adminPhone = "0000000000";

        public String getAdminEmail() { return adminEmail; }
        public void setAdminEmail(String adminEmail) { this.adminEmail = adminEmail; }
        public String getAdminPassword() { return adminPassword; }
        public void setAdminPassword(String adminPassword) { this.adminPassword = adminPassword; }
        public String getAdminFirstName() { return adminFirstName; }
        public void setAdminFirstName(String adminFirstName) { this.adminFirstName = adminFirstName; }
        public String getAdminLastName() { return adminLastName; }
        public void setAdminLastName(String adminLastName) { this.adminLastName = adminLastName; }
        public String getAdminPhone() { return adminPhone; }
        public void setAdminPhone(String adminPhone) { this.adminPhone = adminPhone; }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // app.security.*
    // ─────────────────────────────────────────────────────────────────────────
    public static class Security {
        @Valid @NotNull private Jwt jwt = new Jwt();
        @Valid @NotNull private Cookie cookie = new Cookie();
        @Valid @NotNull private Cors cors = new Cors();
        @Valid @NotNull private RateLimit rateLimit = new RateLimit();
        @Valid @NotNull private LoginLockout loginLockout = new LoginLockout();
        @Valid @NotNull private Password password = new Password();
        @Valid @NotNull private Headers headers = new Headers();
        @Valid @NotNull private Tokens tokens = new Tokens();

        public Jwt getJwt() { return jwt; }
        public void setJwt(Jwt jwt) { this.jwt = jwt; }
        public Cookie getCookie() { return cookie; }
        public void setCookie(Cookie cookie) { this.cookie = cookie; }
        public Cors getCors() { return cors; }
        public void setCors(Cors cors) { this.cors = cors; }
        public RateLimit getRateLimit() { return rateLimit; }
        public void setRateLimit(RateLimit rateLimit) { this.rateLimit = rateLimit; }
        public LoginLockout getLoginLockout() { return loginLockout; }
        public void setLoginLockout(LoginLockout loginLockout) { this.loginLockout = loginLockout; }
        public Password getPassword() { return password; }
        public void setPassword(Password password) { this.password = password; }
        public Headers getHeaders() { return headers; }
        public void setHeaders(Headers headers) { this.headers = headers; }
        public Tokens getTokens() { return tokens; }
        public void setTokens(Tokens tokens) { this.tokens = tokens; }
    }

    public static class Jwt {
        /** HMAC signing secret. Must be at least 32 bytes. Override with JWT_SECRET env var. */
        @NotBlank private String secret;
        /** Token lifetime in milliseconds. */
        @Min(60_000) private long expirationMs = 86_400_000L;

        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public long getExpirationMs() { return expirationMs; }
        public void setExpirationMs(long expirationMs) { this.expirationMs = expirationMs; }
    }

    public static class Cookie {
        @NotBlank private String name = "jwt";
        /** Secure flag — must be true anywhere HTTPS is used (i.e. everything except localhost dev). */
        private boolean secure = true;
        /** Lax blocks cross-site POST cookie sending (CSRF); Strict is stricter but breaks external links. */
        @NotBlank private String sameSite = "Lax";

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public boolean isSecure() { return secure; }
        public void setSecure(boolean secure) { this.secure = secure; }
        public String getSameSite() { return sameSite; }
        public void setSameSite(String sameSite) { this.sameSite = sameSite; }
    }

    public static class Cors {
        @NotEmpty private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5173"));
        private List<String> allowedMethods = new ArrayList<>(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        private List<String> allowedHeaders = new ArrayList<>(List.of("Content-Type", "Accept", "X-Requested-With"));
        private boolean allowCredentials = true;
        @Min(0) private long maxAgeSeconds = 3600;

        public List<String> getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(List<String> allowedOrigins) { this.allowedOrigins = allowedOrigins; }
        public List<String> getAllowedMethods() { return allowedMethods; }
        public void setAllowedMethods(List<String> allowedMethods) { this.allowedMethods = allowedMethods; }
        public List<String> getAllowedHeaders() { return allowedHeaders; }
        public void setAllowedHeaders(List<String> allowedHeaders) { this.allowedHeaders = allowedHeaders; }
        public boolean isAllowCredentials() { return allowCredentials; }
        public void setAllowCredentials(boolean allowCredentials) { this.allowCredentials = allowCredentials; }
        public long getMaxAgeSeconds() { return maxAgeSeconds; }
        public void setMaxAgeSeconds(long maxAgeSeconds) { this.maxAgeSeconds = maxAgeSeconds; }
    }

    public static class RateLimit {
        private boolean enabled = true;
        /** General cap per client IP across all /api/** routes, per minute. */
        @Min(1) private int requestsPerMinute = 120;
        /** Stricter cap per client IP on /api/auth/login, per minute. */
        @Min(1) private int loginAttemptsPerMinute = 10;
        /** Trust X-Forwarded-For for the client IP. Enable ONLY behind a reverse proxy you control. */
        private boolean trustForwardedFor = false;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getRequestsPerMinute() { return requestsPerMinute; }
        public void setRequestsPerMinute(int requestsPerMinute) { this.requestsPerMinute = requestsPerMinute; }
        public int getLoginAttemptsPerMinute() { return loginAttemptsPerMinute; }
        public void setLoginAttemptsPerMinute(int loginAttemptsPerMinute) { this.loginAttemptsPerMinute = loginAttemptsPerMinute; }
        public boolean isTrustForwardedFor() { return trustForwardedFor; }
        public void setTrustForwardedFor(boolean trustForwardedFor) { this.trustForwardedFor = trustForwardedFor; }
    }

    public static class LoginLockout {
        private boolean enabled = true;
        /** Consecutive failed logins for one account before it is temporarily locked. */
        @Min(1) private int maxFailedAttempts = 5;
        /** How long the account stays locked. */
        @Min(1) private int lockMinutes = 15;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getMaxFailedAttempts() { return maxFailedAttempts; }
        public void setMaxFailedAttempts(int maxFailedAttempts) { this.maxFailedAttempts = maxFailedAttempts; }
        public int getLockMinutes() { return lockMinutes; }
        public void setLockMinutes(int lockMinutes) { this.lockMinutes = lockMinutes; }
    }

    public static class Password {
        @Min(8) private int minLength = 10;
        @Min(8) private int maxLength = 128;
        private boolean requireUppercase = true;
        private boolean requireLowercase = true;
        private boolean requireDigit = true;
        private boolean requireSpecial = true;

        public int getMinLength() { return minLength; }
        public void setMinLength(int minLength) { this.minLength = minLength; }
        public int getMaxLength() { return maxLength; }
        public void setMaxLength(int maxLength) { this.maxLength = maxLength; }
        public boolean isRequireUppercase() { return requireUppercase; }
        public void setRequireUppercase(boolean requireUppercase) { this.requireUppercase = requireUppercase; }
        public boolean isRequireLowercase() { return requireLowercase; }
        public void setRequireLowercase(boolean requireLowercase) { this.requireLowercase = requireLowercase; }
        public boolean isRequireDigit() { return requireDigit; }
        public void setRequireDigit(boolean requireDigit) { this.requireDigit = requireDigit; }
        public boolean isRequireSpecial() { return requireSpecial; }
        public void setRequireSpecial(boolean requireSpecial) { this.requireSpecial = requireSpecial; }
    }

    public static class Headers {
        /** HSTS is only emitted on HTTPS responses; harmless to leave on in dev. */
        private boolean hstsEnabled = true;
        @Min(0) private long hstsMaxAgeSeconds = 31_536_000L;
        private boolean hstsIncludeSubdomains = true;
        /** API-only backend: a locked-down CSP stops any accidental HTML from executing scripts. */
        @NotBlank private String contentSecurityPolicy = "default-src 'none'; frame-ancestors 'none'";

        public boolean isHstsEnabled() { return hstsEnabled; }
        public void setHstsEnabled(boolean hstsEnabled) { this.hstsEnabled = hstsEnabled; }
        public long getHstsMaxAgeSeconds() { return hstsMaxAgeSeconds; }
        public void setHstsMaxAgeSeconds(long hstsMaxAgeSeconds) { this.hstsMaxAgeSeconds = hstsMaxAgeSeconds; }
        public boolean isHstsIncludeSubdomains() { return hstsIncludeSubdomains; }
        public void setHstsIncludeSubdomains(boolean hstsIncludeSubdomains) { this.hstsIncludeSubdomains = hstsIncludeSubdomains; }
        public String getContentSecurityPolicy() { return contentSecurityPolicy; }
        public void setContentSecurityPolicy(String contentSecurityPolicy) { this.contentSecurityPolicy = contentSecurityPolicy; }
    }

    /** One-time account tokens sent by email (activation + password reset). */
    public static class Tokens {
        /** How long an invitation / activation link stays valid. */
        @Min(1) private int inviteTtlHours = 72;
        /** How long a password-reset link stays valid. */
        @Min(1) private int resetTtlMinutes = 30;

        public int getInviteTtlHours() { return inviteTtlHours; }
        public void setInviteTtlHours(int inviteTtlHours) { this.inviteTtlHours = inviteTtlHours; }
        public int getResetTtlMinutes() { return resetTtlMinutes; }
        public void setResetTtlMinutes(int resetTtlMinutes) { this.resetTtlMinutes = resetTtlMinutes; }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // app.mail.*
    // ─────────────────────────────────────────────────────────────────────────
    public static class Mail {
        /** false = never send; emails are written to the log instead (safe default for dev). */
        private boolean enabled = false;
        @NotBlank private String from = "Konoha HelpDesk <no-reply@localhost>";
        /** Where activation / reset links point (the React app). No trailing slash. */
        @NotBlank private String frontendBaseUrl = "http://localhost:5173";
        @NotBlank private String activatePath = "/activate";
        @NotBlank private String resetPasswordPath = "/reset-password";
        @NotBlank private String appName = "Konoha HelpDesk";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getFrom() { return from; }
        public void setFrom(String from) { this.from = from; }
        public String getFrontendBaseUrl() { return frontendBaseUrl; }
        public void setFrontendBaseUrl(String frontendBaseUrl) { this.frontendBaseUrl = frontendBaseUrl; }
        public String getActivatePath() { return activatePath; }
        public void setActivatePath(String activatePath) { this.activatePath = activatePath; }
        public String getResetPasswordPath() { return resetPasswordPath; }
        public void setResetPasswordPath(String resetPasswordPath) { this.resetPasswordPath = resetPasswordPath; }
        public String getAppName() { return appName; }
        public void setAppName(String appName) { this.appName = appName; }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // app.media.*
    // ─────────────────────────────────────────────────────────────────────────
    public static class Media {
        /** Directory where uploads are stored. Created on startup if missing. Absolute or relative to the working dir. */
        @NotBlank private String basePath = "./media";
        @Min(1) private long maxFileSizeMb = 5;
        /** Allowed image types (validated by magic bytes, not by the client-supplied name). */
        @NotEmpty private List<String> allowedImageTypes = new ArrayList<>(List.of("image/jpeg", "image/png", "image/webp"));
        /** Ticket attachments: larger cap and more types (images + PDF + CSV/plain text). */
        @Min(1) private long maxAttachmentSizeMb = 20;
        @NotEmpty private List<String> allowedAttachmentTypes = new ArrayList<>(List.of("image/jpeg", "image/png", "image/webp", "application/pdf", "text/csv", "text/plain"));
        @Min(1) private int maxAttachmentsPerTicket = 10;

        public String getBasePath() { return basePath; }
        public void setBasePath(String basePath) { this.basePath = basePath; }
        public long getMaxFileSizeMb() { return maxFileSizeMb; }
        public void setMaxFileSizeMb(long maxFileSizeMb) { this.maxFileSizeMb = maxFileSizeMb; }
        public List<String> getAllowedImageTypes() { return allowedImageTypes; }
        public void setAllowedImageTypes(List<String> allowedImageTypes) { this.allowedImageTypes = allowedImageTypes; }
        public long getMaxAttachmentSizeMb() { return maxAttachmentSizeMb; }
        public void setMaxAttachmentSizeMb(long v) { this.maxAttachmentSizeMb = v; }
        public List<String> getAllowedAttachmentTypes() { return allowedAttachmentTypes; }
        public void setAllowedAttachmentTypes(List<String> v) { this.allowedAttachmentTypes = v; }
        public int getMaxAttachmentsPerTicket() { return maxAttachmentsPerTicket; }
        public void setMaxAttachmentsPerTicket(int v) { this.maxAttachmentsPerTicket = v; }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // app.subscription.*
    // ─────────────────────────────────────────────────────────────────────────
    public static class Subscription {
        /** Create the configured features / plans on startup if they don't already exist. */
        private boolean seedOnStartup = true;
        /** When true, existing seeded plans are updated to match this file on every startup. */
        private boolean overwriteSeededPlans = false;
        @Valid private List<FeatureSeed> features = new ArrayList<>();
        @Valid private List<PlanSeed> plans = new ArrayList<>();
        @Valid @NotNull private Limits customDefaults = new Limits();
        @Valid @NotNull private Map<SlaPriority, Sla> customDefaultSla = new EnumMap<>(SlaPriority.class);
        @Valid @NotNull private Caps caps = new Caps();

        public boolean isSeedOnStartup() { return seedOnStartup; }
        public void setSeedOnStartup(boolean seedOnStartup) { this.seedOnStartup = seedOnStartup; }
        public boolean isOverwriteSeededPlans() { return overwriteSeededPlans; }
        public void setOverwriteSeededPlans(boolean overwriteSeededPlans) { this.overwriteSeededPlans = overwriteSeededPlans; }
        public List<FeatureSeed> getFeatures() { return features; }
        public void setFeatures(List<FeatureSeed> features) { this.features = features; }
        public List<PlanSeed> getPlans() { return plans; }
        public void setPlans(List<PlanSeed> plans) { this.plans = plans; }
        public Limits getCustomDefaults() { return customDefaults; }
        public void setCustomDefaults(Limits customDefaults) { this.customDefaults = customDefaults; }
        public Map<SlaPriority, Sla> getCustomDefaultSla() { return customDefaultSla; }
        public void setCustomDefaultSla(Map<SlaPriority, Sla> customDefaultSla) { this.customDefaultSla = customDefaultSla; }
        public Caps getCaps() { return caps; }
        public void setCaps(Caps caps) { this.caps = caps; }
    }

    public static class FeatureSeed {
        @NotBlank private String code;
        @NotBlank private String name;

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    public static class PlanSeed {
        @NotBlank private String name;
        private String description = "";
        /** Feature codes enabled on this plan. Use "*" to enable every configured feature. */
        private List<String> features = new ArrayList<>();
        @Valid @NotNull private Limits limits = new Limits();
        @Valid private Map<SlaPriority, Sla> sla = new EnumMap<>(SlaPriority.class);

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public List<String> getFeatures() { return features; }
        public void setFeatures(List<String> features) { this.features = features; }
        public Limits getLimits() { return limits; }
        public void setLimits(Limits limits) { this.limits = limits; }
        public Map<SlaPriority, Sla> getSla() { return sla; }
        public void setSla(Map<SlaPriority, Sla> sla) { this.sla = sla; }
    }

    public static class Limits {
        @Min(1) private int maxUsers = 10;
        @Min(1) private int monthlyTicketLimit = 100;
        @Min(1) private long storageLimitMb = 1024;

        public int getMaxUsers() { return maxUsers; }
        public void setMaxUsers(int maxUsers) { this.maxUsers = maxUsers; }
        public int getMonthlyTicketLimit() { return monthlyTicketLimit; }
        public void setMonthlyTicketLimit(int monthlyTicketLimit) { this.monthlyTicketLimit = monthlyTicketLimit; }
        public long getStorageLimitMb() { return storageLimitMb; }
        public void setStorageLimitMb(long storageLimitMb) { this.storageLimitMb = storageLimitMb; }
    }

    public static class Sla {
        @Min(1) private int responseMinutes = 60;
        @Min(1) private int resolutionMinutes = 480;

        public int getResponseMinutes() { return responseMinutes; }
        public void setResponseMinutes(int responseMinutes) { this.responseMinutes = responseMinutes; }
        public int getResolutionMinutes() { return resolutionMinutes; }
        public void setResolutionMinutes(int resolutionMinutes) { this.resolutionMinutes = resolutionMinutes; }
    }

    /** Upper bounds accepted from admins when configuring a Custom Plan (input validation guardrails). */
    public static class Caps {
        @Min(1) private int maxUsers = 100_000;
        @Min(1) private int monthlyTicketLimit = 10_000_000;
        @Min(1) private long storageLimitMb = 10_485_760L; // 10 TB
        @Min(1) private int slaResponseMinutes = 43_200;   // 30 days
        @Min(1) private int slaResolutionMinutes = 129_600; // 90 days

        public int getMaxUsers() { return maxUsers; }
        public void setMaxUsers(int maxUsers) { this.maxUsers = maxUsers; }
        public int getMonthlyTicketLimit() { return monthlyTicketLimit; }
        public void setMonthlyTicketLimit(int monthlyTicketLimit) { this.monthlyTicketLimit = monthlyTicketLimit; }
        public long getStorageLimitMb() { return storageLimitMb; }
        public void setStorageLimitMb(long storageLimitMb) { this.storageLimitMb = storageLimitMb; }
        public int getSlaResponseMinutes() { return slaResponseMinutes; }
        public void setSlaResponseMinutes(int slaResponseMinutes) { this.slaResponseMinutes = slaResponseMinutes; }
        public int getSlaResolutionMinutes() { return slaResolutionMinutes; }
        public void setSlaResolutionMinutes(int slaResolutionMinutes) { this.slaResolutionMinutes = slaResolutionMinutes; }
    }
}
