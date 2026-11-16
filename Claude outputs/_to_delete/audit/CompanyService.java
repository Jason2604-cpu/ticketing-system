package com.konohahelpdesk.service.company;

import com.konohahelpdesk.service.subscription.CompanySubscriptionService;

import com.konohahelpdesk.dto.company.BusinessHoursDto;
import com.konohahelpdesk.dto.company.CompanyResponse;
import com.konohahelpdesk.model.company.CompanyBusinessHours;
import com.konohahelpdesk.repository.company.CompanyBusinessHoursRepository;
import com.konohahelpdesk.dto.company.CreateCompanyRequest;
import com.konohahelpdesk.dto.company.UpdateCompanyRequest;
import com.konohahelpdesk.exception.BusinessRuleException;
import com.konohahelpdesk.exception.DuplicateResourceException;
import com.konohahelpdesk.exception.ResourceNotFoundException;
import com.konohahelpdesk.model.company.Company;
import com.konohahelpdesk.dto.user.UserResponse;
import com.konohahelpdesk.model.user.Roles;
import com.konohahelpdesk.model.user.User;
import com.konohahelpdesk.model.user.UserStatus;
import com.konohahelpdesk.repository.company.CompanyRepository;
import com.konohahelpdesk.repository.user.UserRepository;
import com.konohahelpdesk.security.AuditLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final CompanySubscriptionService subscriptionService;
    private final EngineerAssignmentService assignments;
    private final UserRepository userRepository;
    private final CompanyBusinessHoursRepository businessHoursRepository;
    private final BusinessCalendar businessCalendar;
    private final AuditLogger audit;

    public CompanyService(CompanyRepository companyRepository,
                          CompanySubscriptionService subscriptionService,
                          EngineerAssignmentService assignments,
                          UserRepository userRepository,
                          CompanyBusinessHoursRepository businessHoursRepository,
                          BusinessCalendar businessCalendar,
                          AuditLogger audit) {
        this.companyRepository = companyRepository;
        this.subscriptionService = subscriptionService;
        this.assignments = assignments;
        this.userRepository = userRepository;
        this.businessHoursRepository = businessHoursRepository;
        this.businessCalendar = businessCalendar;
        this.audit = audit;
    }

    /** Creates the company AND its subscription in one transaction — neither exists without the other. */
    @Transactional
    public CompanyResponse create(CreateCompanyRequest req) {
        String name = req.getName().trim();
        if (companyRepository.existsByNameIgnoreCase(name)) {
            throw new DuplicateResourceException("A company named '" + name + "' already exists");
        }
        Company company = new Company();
        company.setName(name);
        applyDetails(company, req.getAddress(), req.getContactEmail(), req.getContactPhone());
        applyMainEngineer(company, req.getMainEngineerId());
        company.setBusinessHoursEnabled(req.getBusinessHoursEnabled() == null || req.getBusinessHoursEnabled());
        Company saved = companyRepository.save(company);
        // A company always has a full week of hours — defaulted to Mon–Fri 09:00–18:00 — so the SLA
        // clock never has to guess, and the edit form always has seven rows to render.
        saveBusinessHours(saved, req.getBusinessHours() == null ? BusinessHoursDto.defaults() : req.getBusinessHours());

        audit.event("COMPANY_CREATED", "companyId=" + saved.getId() + " name=" + name);
        CompanyResponse response = CompanyResponse.from(saved);
        response.setBusinessHours(businessHoursOf(saved.getId()));
        response.setSubscription(subscriptionService.assign(saved.getId(), req.getSubscription()));
        return response;
    }

    @Transactional
    public CompanyResponse update(Long id, UpdateCompanyRequest req) {
        Company company = load(id);
        String name = req.getName().trim();
        if (companyRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new DuplicateResourceException("A company named '" + name + "' already exists");
        }
        company.setName(name);
        applyDetails(company, req.getAddress(), req.getContactEmail(), req.getContactPhone());
        applyMainEngineer(company, req.getMainEngineerId());
        if (req.getBusinessHoursEnabled() != null) company.setBusinessHoursEnabled(req.getBusinessHoursEnabled());
        Company saved = companyRepository.save(company);
        if (req.getBusinessHours() != null) saveBusinessHours(saved, req.getBusinessHours());
        // Changing hours must not silently re-date tickets already in flight: their deadlines are
        // absolute timestamps stamped at creation. Only new tickets use the new schedule.
        businessCalendar.evict(id);
        audit.event("COMPANY_UPDATED", "companyId=" + id);
        return withSubscription(saved);
    }

    /**
     * Company table for the admin/engineer portals: plan, seat usage, assigned engineers, company admin.
     * An ENGINEER only sees the companies they're assigned to; if they have no assignments, they see all
     * (same "no company chosen = show everything" rule the ticket list uses).
     */
    /**
     * The companies list.
     *
     * Rewritten to a fixed number of queries. It used to call {@code findResolved} once per row —
     * about nine queries each, including the storage-sum with three IN-subqueries — and to load
     * EVERY user in the system to find each company's admin. The frontend refetches this list
     * every 15 seconds and four different screens ask for it, so the cost was paid constantly.
     *
     * Now: one query for the companies, one for the engineer assignments, three inside
     * {@code summariesFor}, and one for the company admins. Flat, whatever the company count.
     */
    public List<CompanyResponse> list(User actor) {
        List<Long> engineerScope = actor.getRole() == Roles.ENGINEER
            ? assignments.companiesForEngineer(actor.getId()).stream().map(UserResponse.CompanyRef::id).toList()
            : List.of();

        List<Company> visible = companyRepository.findAll().stream()
            .filter(c -> engineerScope.isEmpty() || engineerScope.contains(c.getId()))
            .sorted(Comparator.comparing(Company::getName, String.CASE_INSENSITIVE_ORDER))
            .toList();
        if (visible.isEmpty()) return List.of();

        List<Long> ids = visible.stream().map(Company::getId).toList();
        Map<Long, List<UserResponse>> engineersByCompany = assignments.engineersByCompany();
        Map<Long, CompanySubscriptionService.SubscriptionSummary> summaries = subscriptionService.summariesFor(ids);

        Map<Long, User> adminByCompany = new HashMap<>();
        for (User admin : userRepository.findByRoleAndStatusNotAndCompanyIdIn(Roles.CUSTOMER_ADMIN, UserStatus.IS_DELETED, ids)) {
            // putIfAbsent: a company with two company-admins keeps the first, matching the old
            // findFirst() behaviour rather than silently picking a different one.
            if (admin.getCompany() != null) adminByCompany.putIfAbsent(admin.getCompany().getId(), admin);
        }

        return visible.stream()
            .map(c -> {
                CompanyResponse r = CompanyResponse.from(c);
                CompanySubscriptionService.SubscriptionSummary s = summaries.get(c.getId());
                if (s != null) {
                    r.setPlanName(s.planName());
                    r.setSubscriptionType(s.subscriptionType().name());
                    r.setMaxUsers(s.maxUsers());
                    r.setActiveUsers(s.activeUsers());
                }
                r.setEngineers(engineersByCompany.getOrDefault(c.getId(), List.of()));
                User admin = adminByCompany.get(c.getId());
                if (admin != null) r.setCompanyAdmin(UserResponse.from(admin));
                return r;
            })
            .toList();
    }

    public CompanyResponse get(Long id) {
        return withSubscription(load(id));
    }

    public Company load(Long id) {
        return companyRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Company not found: " + id));
    }

    /** Replaces the company's whole week in one go — seven rows, no partial state to reason about. */
    private void saveBusinessHours(Company company, java.util.List<BusinessHoursDto> rows) {
        java.util.Map<java.time.DayOfWeek, BusinessHoursDto> byDay = new java.util.EnumMap<>(java.time.DayOfWeek.class);
        for (BusinessHoursDto row : rows) {
            if (row.getDayOfWeek() == null) continue;
            if (row.getOpenTime() == null || row.getCloseTime() == null) continue;
            if (!row.isClosed() && !row.getOpenTime().isBefore(row.getCloseTime())) {
                throw new BusinessRuleException("Opening time must be before closing time on "
                    + row.getDayOfWeek().name().charAt(0) + row.getDayOfWeek().name().substring(1).toLowerCase(java.util.Locale.ROOT));
            }
            byDay.put(row.getDayOfWeek(), row);
        }

        java.util.Map<java.time.DayOfWeek, CompanyBusinessHours> existing = new java.util.EnumMap<>(java.time.DayOfWeek.class);
        for (CompanyBusinessHours h : businessHoursRepository.findByCompanyId(company.getId())) {
            existing.put(h.getDayOfWeek(), h);
        }

        for (java.time.DayOfWeek day : java.time.DayOfWeek.values()) {
            BusinessHoursDto incoming = byDay.get(day);
            if (incoming == null) continue;                       // day not supplied: leave it alone
            CompanyBusinessHours row = existing.get(day);
            if (row == null) {
                row = new CompanyBusinessHours();
                row.setCompany(company);
                row.setDayOfWeek(day);
            }
            row.setOpenTime(incoming.getOpenTime());
            row.setCloseTime(incoming.getCloseTime());
            row.setClosed(incoming.isClosed());
            businessHoursRepository.save(row);
        }
        businessCalendar.evict(company.getId());
    }

    /** Always seven rows, in Monday-first order, so the UI never has to fill gaps. */
    private java.util.List<BusinessHoursDto> businessHoursOf(Long companyId) {
        java.util.Map<java.time.DayOfWeek, CompanyBusinessHours> byDay = new java.util.EnumMap<>(java.time.DayOfWeek.class);
        for (CompanyBusinessHours h : businessHoursRepository.findByCompanyId(companyId)) {
            byDay.put(h.getDayOfWeek(), h);
        }
        java.util.List<BusinessHoursDto> out = new java.util.ArrayList<>();
        for (java.time.DayOfWeek day : java.time.DayOfWeek.values()) {
            CompanyBusinessHours h = byDay.get(day);
            out.add(h == null
                ? new BusinessHoursDto(day, java.time.LocalTime.of(9, 0), java.time.LocalTime.of(18, 0),
                                       day == java.time.DayOfWeek.SATURDAY || day == java.time.DayOfWeek.SUNDAY)
                : new BusinessHoursDto(day, h.getOpenTime(), h.getCloseTime(), h.isClosed()));
        }
        return out;
    }

    private CompanyResponse withSubscription(Company company) {
        CompanyResponse r = CompanyResponse.from(company);
        r.setBusinessHours(businessHoursOf(company.getId()));
        subscriptionService.findResolved(company.getId()).ifPresent(r::setSubscription);
        return r;
    }

    private void applyDetails(Company c, String address, String contactEmail, String contactPhone) {
        c.setAddress(blankToNull(address));
        c.setContactEmail(contactEmail == null || contactEmail.isBlank() ? null : contactEmail.trim().toLowerCase(Locale.ROOT));
        c.setContactPhone(blankToNull(contactPhone));
    }

    /** Resolves and validates the main-engineer selection; null clears it. */
    private void applyMainEngineer(Company c, Long mainEngineerId) {
        if (mainEngineerId == null) {
            c.setMainEngineer(null);
            return;
        }
        User engineer = userRepository.findById(mainEngineerId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + mainEngineerId));
        if (engineer.getRole() != Roles.ENGINEER && engineer.getRole() != Roles.ADMIN) {
            throw new BusinessRuleException("Only engineers and admins can be set as the main engineer");
        }
        c.setMainEngineer(engineer);
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
