package com.konohahelpdesk.service.company;

import com.konohahelpdesk.dto.user.UserResponse;
import com.konohahelpdesk.model.company.Company;
import com.konohahelpdesk.model.user.Roles;
import com.konohahelpdesk.model.user.User;
import com.konohahelpdesk.repository.company.CompanyRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * "Which companies may this actor see?" — one rule, one place.
 *
 * It was written three times before this class existed ({@code CompanyService.list},
 * {@code ReportService.visibleCompanies}, and every new module that needs scoping), and a
 * permission rule with three copies is two chances to fix a bug in only some of them.
 *
 * THE ENGINEER-WITH-NO-ASSIGNMENTS CONVENTION IS LOAD-BEARING: an engineer assigned to nothing
 * sees everything, not nothing. That is what {@code TicketService.engineerScope} and
 * {@code ReportService} already do, and changing it here would silently empty those pages for a
 * new hire. It is deliberate — a brand-new engineer is unrestricted until someone scopes them.
 *
 * {@code ReportService.visibleCompanies} is intentionally NOT routed through this yet: it folds a
 * requested-company argument and a plan-feature gate into the same method, so collapsing the two
 * would either complicate this class or change that endpoint's behaviour. Worth doing, separately,
 * with the report endpoints in front of you.
 */
@Service
public class CompanyScope {

    private final CompanyRepository companies;
    private final EngineerAssignmentService assignments;

    public CompanyScope(CompanyRepository companies, EngineerAssignmentService assignments) {
        this.companies = companies;
        this.assignments = assignments;
    }

    /**
     * Every company id the actor may act on, or {@code null} meaning "no restriction" — admins,
     * and engineers who have no assignments.
     *
     * Null rather than "all the ids" on purpose: the callers use it to decide whether to add a
     * {@code company_id in (...)} clause at all, and materialising every id just to hand it
     * straight back into an IN list costs a full table scan of companies on every request.
     */
    public List<Long> visibleCompanyIds(User actor) {
        if (actor == null) throw new AccessDeniedException("Not authenticated");

        if (actor.getRole() == Roles.ADMIN) return null;

        if (actor.getRole() == Roles.ENGINEER) {
            List<Long> assigned = assignments.companiesForEngineer(actor.getId()).stream()
                .map(UserResponse.CompanyRef::id)
                .toList();
            return assigned.isEmpty() ? null : assigned;
        }

        // Customer side: exactly their own company, and an account with none can't see anything.
        Long own = actor.getCompany() == null ? null : actor.getCompany().getId();
        if (own == null) throw new AccessDeniedException("Your account is not linked to a company");
        return List.of(own);
    }

    /** True when the actor may act on this company. */
    public boolean canAccess(User actor, Long companyId) {
        if (companyId == null) return false;
        List<Long> scope = visibleCompanyIds(actor);
        return scope == null || scope.contains(companyId);
    }

    /** As {@link #canAccess}, but 403s instead of returning false. */
    public void requireAccess(User actor, Long companyId) {
        if (!canAccess(actor, companyId)) {
            throw new AccessDeniedException("You do not have access to that company");
        }
    }

    /** The visible companies themselves, name-ordered — for pickers that list them. */
    public List<Company> visibleCompanies(User actor) {
        List<Long> scope = visibleCompanyIds(actor);
        return companies.findAll().stream()
            .filter(c -> scope == null || scope.contains(c.getId()))
            .sorted(Comparator.comparing(Company::getName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }
}
