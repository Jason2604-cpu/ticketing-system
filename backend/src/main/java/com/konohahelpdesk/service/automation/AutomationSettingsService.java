package com.konohahelpdesk.service.automation;

import com.konohahelpdesk.config.AppProperties;
import com.konohahelpdesk.dto.automation.AutomationRunResponse;
import com.konohahelpdesk.dto.automation.AutomationSettingsResponse;
import com.konohahelpdesk.dto.automation.UpdateAutomationSettingsRequest;
import com.konohahelpdesk.exception.BusinessRuleException;
import com.konohahelpdesk.exception.ResourceNotFoundException;
import com.konohahelpdesk.model.automation.AutomationSettings;
import com.konohahelpdesk.model.company.Company;
import com.konohahelpdesk.model.user.Roles;
import com.konohahelpdesk.model.user.User;
import com.konohahelpdesk.model.user.UserStatus;
import com.konohahelpdesk.repository.automation.AutomationRunRepository;
import com.konohahelpdesk.repository.automation.AutomationSettingsRepository;
import com.konohahelpdesk.repository.company.CompanyRepository;
import com.konohahelpdesk.repository.user.UserRepository;
import com.konohahelpdesk.security.AuditLogger;
import com.konohahelpdesk.service.company.CompanyScope;
import com.konohahelpdesk.service.subscription.CompanySubscriptionService;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Reads and changes a company's automation settings, and decides who receives its emails.
 *
 * WHO MAY DO WHAT
 *   - Admin: read and change any company, including the lock ({@code customerCanEdit}).
 *   - Engineer: read the companies they can see; read-only.
 *   - Customer Admin: their own company only, only while the plan includes AUTOMATION_RULES,
 *     and only while staff haven't locked it. They cannot change the lock itself.
 *   - Everyone else: no access.
 *
 * A company with no settings row behaves as the column defaults (monthly summary on, day 1); the
 * row is only written when someone saves.
 */
@Service
public class AutomationSettingsService {

    public static final String FEATURE = "AUTOMATION_RULES";
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@,;]+@[^\\s@,;]+\\.[^\\s@,;]+$");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    private final AutomationSettingsRepository settings;
    private final AutomationRunRepository runs;
    private final CompanyRepository companies;
    private final UserRepository users;
    private final CompanyScope scope;
    private final CompanySubscriptionService subscriptions;
    private final AutomationClock clock;
    private final AuditLogger audit;
    private final AppProperties.Automation cfg;

    public AutomationSettingsService(AutomationSettingsRepository settings, AutomationRunRepository runs,
                                     CompanyRepository companies, UserRepository users, CompanyScope scope,
                                     CompanySubscriptionService subscriptions, AutomationClock clock,
                                     AuditLogger audit, AppProperties props) {
        this.settings = settings;
        this.runs = runs;
        this.companies = companies;
        this.users = users;
        this.scope = scope;
        this.subscriptions = subscriptions;
        this.clock = clock;
        this.audit = audit;
        this.cfg = props.getAutomation();
    }

    // ── Access ───────────────────────────────────────────────────────────────

    /** 403s unless the actor may look at this company's automation page. */
    public void requireView(User actor, Long companyId) {
        Roles role = actor.getRole();
        if (role == Roles.ADMIN || role == Roles.ENGINEER) {
            scope.requireAccess(actor, companyId);
        } else if (role == Roles.CUSTOMER_ADMIN) {
            Long own = actor.getCompany() == null ? null : actor.getCompany().getId();
            if (own == null || !own.equals(companyId)) throw new AccessDeniedException("You can only view your own company's automations");
            subscriptions.requireFeature(companyId, FEATURE);
        } else {
            throw new AccessDeniedException("You do not have access to automations");
        }
    }

    private boolean canEdit(User actor, AutomationSettings s) {
        return switch (actor.getRole()) {
            case ADMIN -> true;
            case CUSTOMER_ADMIN -> s.isCustomerCanEdit();   // requireView already checked company + plan
            default -> false;
        };
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    /** The stored settings, or an unsaved copy of the defaults. Never writes. */
    @Transactional(readOnly = true)
    public AutomationSettings peek(Long companyId) {
        return settings.findById(companyId).orElseGet(() -> new AutomationSettings(companyId));
    }

    @Transactional(readOnly = true)
    public AutomationSettingsResponse view(User actor, Long companyId) {
        requireView(actor, companyId);
        return toResponse(actor, companyId, peek(companyId));
    }

    @Transactional(readOnly = true)
    public List<AutomationRunResponse> recentRuns(User actor, Long companyId) {
        requireView(actor, companyId);
        return runs.findByCompanyIdOrderByRanAtDesc(companyId, PageRequest.of(0, 24)).stream()
            .map(AutomationRunResponse::of).toList();
    }

    // ── Write ────────────────────────────────────────────────────────────────

    @Transactional
    public AutomationSettingsResponse update(User actor, Long companyId, UpdateAutomationSettingsRequest req) {
        requireView(actor, companyId);
        AutomationSettings s = settings.findById(companyId).orElseGet(() -> new AutomationSettings(companyId));
        if (!canEdit(actor, s)) {
            throw new AccessDeniedException(actor.getRole() == Roles.CUSTOMER_ADMIN
                ? "Your automation settings are managed by your service provider"
                : "You do not have permission to change automation settings");
        }
        if (req.customerCanEdit() != null && actor.getRole() != Roles.ADMIN) {
            throw new AccessDeniedException("Only staff can lock or unlock a company's automations");
        }

        StringBuilder changes = new StringBuilder();
        if (req.monthlySummaryEnabled() != null && req.monthlySummaryEnabled() != s.isMonthlySummaryEnabled()) {
            s.setMonthlySummaryEnabled(req.monthlySummaryEnabled());
            changes.append(" monthlySummaryEnabled=").append(req.monthlySummaryEnabled());
        }
        if (req.monthlySummaryDay() != null && req.monthlySummaryDay() != s.getMonthlySummaryDay()) {
            int day = req.monthlySummaryDay();
            if (day < 1 || day > 28) throw new BusinessRuleException("The summary day must be between 1 and 28");
            s.setMonthlySummaryDay(day);
            changes.append(" monthlySummaryDay=").append(day);
        }
        if (req.monthlySummaryExtraRecipients() != null) {
            List<String> cleaned = cleanExtras(req.monthlySummaryExtraRecipients());
            String joined = cleaned.isEmpty() ? null : String.join(",", cleaned);
            if (!Objects.equals(joined, s.getMonthlySummaryExtraRecipients())) {
                s.setMonthlySummaryExtraRecipients(joined);
                changes.append(" extraRecipients=").append(cleaned.size());
            }
        }
        if (req.customerCanEdit() != null && req.customerCanEdit() != s.isCustomerCanEdit()) {
            s.setCustomerCanEdit(req.customerCanEdit());
            changes.append(" customerCanEdit=").append(req.customerCanEdit());
        }

        if (changes.length() > 0) {
            s = settings.save(s);
            audit.event("AUTOMATION_SETTINGS_UPDATED", "company=" + companyId + changes, actor);
        }
        return toResponse(actor, companyId, s);
    }

    // ── Recipients ───────────────────────────────────────────────────────────

    /** Active Customer Admins of the company who haven't switched email off in their own profile. */
    @Transactional(readOnly = true)
    public List<String> adminEmails(Long companyId) {
        return users.findByCompanyIdAndRoleAndStatus(companyId, Roles.CUSTOMER_ADMIN, UserStatus.ACTIVE).stream()
            .filter(User::isNotifyEmail)
            .map(User::getEmail)
            .filter(e -> e != null && !e.isBlank())
            .toList();
    }

    /** Customer Admins plus the company's extra addresses, de-duplicated case-insensitively. */
    @Transactional(readOnly = true)
    public List<String> recipientsFor(Long companyId, AutomationSettings s) {
        Map<String, String> byLower = new LinkedHashMap<>();
        for (String e : adminEmails(companyId)) byLower.putIfAbsent(e.toLowerCase(Locale.ROOT), e);
        for (String e : splitExtras(s.getMonthlySummaryExtraRecipients())) byLower.putIfAbsent(e.toLowerCase(Locale.ROOT), e);
        return new ArrayList<>(byLower.values());
    }

    static List<String> splitExtras(String stored) {
        if (stored == null || stored.isBlank()) return List.of();
        return Arrays.stream(stored.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList();
    }

    private List<String> cleanExtras(List<String> raw) {
        Map<String, String> byLower = new LinkedHashMap<>();
        for (String r : raw) {
            if (r == null) continue;
            String e = r.trim();
            if (e.isEmpty()) continue;
            if (e.length() > 254 || !EMAIL.matcher(e).matches()) {
                throw new BusinessRuleException("\"" + e + "\" is not a valid email address");
            }
            byLower.putIfAbsent(e.toLowerCase(Locale.ROOT), e);
        }
        if (byLower.size() > cfg.getMaxExtraRecipients()) {
            throw new BusinessRuleException("You can add at most " + cfg.getMaxExtraRecipients() + " extra recipients");
        }
        return new ArrayList<>(byLower.values());
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    private AutomationSettingsResponse toResponse(User actor, Long companyId, AutomationSettings s) {
        Company company = companies.findById(companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Company not found"));
        ZonedDateTime now = clock.now();
        ZonedDateTime next = clock.dueAt(YearMonth.from(now), s.getMonthlySummaryDay());
        if (!now.isBefore(next)) next = clock.dueAt(YearMonth.from(now).plusMonths(1), s.getMonthlySummaryDay());
        // The email sent at `next` covers the month before the one it lands in.
        String period = YearMonth.from(next).minusMonths(1).format(MONTH_LABEL);
        return new AutomationSettingsResponse(
            companyId, company.getName(),
            subscriptions.hasFeature(companyId, FEATURE),
            canEdit(actor, s),
            s.isCustomerCanEdit(),
            s.isMonthlySummaryEnabled(),
            s.getMonthlySummaryDay(),
            splitExtras(s.getMonthlySummaryExtraRecipients()),
            adminEmails(companyId),
            period,
            next.toOffsetDateTime().toString(),
            clock.zone().getId());
    }
}
