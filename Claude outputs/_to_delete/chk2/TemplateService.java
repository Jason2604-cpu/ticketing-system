package com.konohahelpdesk.service.template;

import com.konohahelpdesk.dto.template.TemplateRequest;
import com.konohahelpdesk.dto.template.TemplateResponse;
import com.konohahelpdesk.exception.BusinessRuleException;
import com.konohahelpdesk.exception.DuplicateResourceException;
import com.konohahelpdesk.exception.ResourceNotFoundException;
import com.konohahelpdesk.model.company.Company;
import com.konohahelpdesk.model.template.TemplateKind;
import com.konohahelpdesk.model.template.TicketTemplate;
import com.konohahelpdesk.model.user.Roles;
import com.konohahelpdesk.model.user.User;
import com.konohahelpdesk.repository.company.CompanyRepository;
import com.konohahelpdesk.repository.template.TicketTemplateRepository;
import com.konohahelpdesk.security.AuditLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Ticket templates and canned replies.
 *
 * Two separate surfaces sit on this service and they have very different access rules:
 *
 *  - Management ({@link #list}, {@link #create}, {@link #update}, {@link #delete}) is admin /
 *    MANAGE_TEMPLATES, enforced on TemplateController.
 *  - The picker ({@link #usableFor}) is open to any authenticated user, so the scoping has to be
 *    done HERE rather than trusted to a query param. A customer gets only TICKET templates that
 *    are active, marked visible-to-customers, and either global or pinned to their own company.
 *    Nothing a customer sends can widen that.
 */
@Service
public class TemplateService {

    private final TicketTemplateRepository templates;
    private final CompanyRepository companies;
    private final AuditLogger audit;

    public TemplateService(TicketTemplateRepository templates, CompanyRepository companies, AuditLogger audit) {
        this.templates = templates;
        this.companies = companies;
        this.audit = audit;
    }

    // ── Management ───────────────────────────────────────────────────────────

    public List<TemplateResponse> list() {
        return templates.findAllForListing().stream().map(TemplateResponse::from).toList();
    }

    public TemplateResponse get(Long id) {
        return TemplateResponse.from(load(id));
    }

    @Transactional
    public TemplateResponse create(TemplateRequest req, User actor) {
        String name = req.getName().trim();
        if (templates.existsByKindAndNameIgnoreCase(req.getKind(), name)) {
            throw new DuplicateResourceException("A " + req.getKind().name().toLowerCase() + " template named \"" + name + "\" already exists");
        }
        TicketTemplate t = new TicketTemplate();
        t.setCreatedBy(actor);
        apply(t, req);
        TicketTemplate saved = templates.save(t);
        audit.event("TEMPLATE_CREATED", "templateId=" + saved.getId() + " kind=" + saved.getKind() + " name=" + name);
        return TemplateResponse.from(saved);
    }

    @Transactional
    public TemplateResponse update(Long id, TemplateRequest req) {
        TicketTemplate t = load(id);
        String name = req.getName().trim();
        boolean renamed = !name.equalsIgnoreCase(t.getName()) || req.getKind() != t.getKind();
        if (renamed && templates.existsByKindAndNameIgnoreCase(req.getKind(), name)) {
            throw new DuplicateResourceException("A " + req.getKind().name().toLowerCase() + " template named \"" + name + "\" already exists");
        }
        apply(t, req);
        TicketTemplate saved = templates.save(t);
        audit.event("TEMPLATE_UPDATED", "templateId=" + id + " kind=" + saved.getKind() + " name=" + name);
        return TemplateResponse.from(saved);
    }

    @Transactional
    public void delete(Long id) {
        TicketTemplate t = load(id);
        templates.delete(t);
        audit.event("TEMPLATE_DELETED", "templateId=" + id + " name=" + t.getName());
    }

    // ── Picker ───────────────────────────────────────────────────────────────

    /**
     * The templates this user may actually pick from, for one kind.
     *
     * Every restriction is derived from the caller's own identity:
     *  - REPLY templates are staff-only, full stop. A customer asking for them gets an empty list,
     *    not a 403 — there is nothing secret about the fact that canned replies exist, and a 403
     *    would only invite someone to go looking.
     *  - A customer is pinned to their own company and to visible-to-customers rows.
     *  - Staff see everything active for the company in scope; passing no company leaves the
     *    global templates, which is the right answer for a reply box on a ticket they haven't
     *    picked a company for yet.
     */
    public List<TemplateResponse> usableFor(User actor, TemplateKind kind, Long companyId) {
        boolean staff = actor.getRole() == Roles.ADMIN || actor.getRole() == Roles.ENGINEER;

        if (kind == TemplateKind.REPLY && !staff) {
            return List.of();
        }

        Long scope;
        if (staff) {
            scope = companyId;
        } else {
            // Ignore whatever the client asked for — a customer's scope is their own company.
            scope = actor.getCompany() == null ? null : actor.getCompany().getId();
        }
        // -1 matches no company, leaving only the global (company is null) rows.
        List<TicketTemplate> rows = templates.findUsable(kind, scope == null ? -1L : scope);

        return rows.stream()
            .filter(t -> staff || t.isVisibleToCustomers())
            .map(TemplateResponse::from)
            .toList();
    }

    /**
     * Records that a template was used. Separate from reading it because a picker that counted a
     * use every time the list loaded would make the number meaningless.
     *
     * The endpoint is open to every signed-in user, so it re-checks that the caller could actually
     * have picked this template. The stake is only a counter, but an endpoint that takes any id
     * from anyone and writes to that row is the shape of a problem even when this instance of it
     * is harmless.
     */
    @Transactional
    public void markUsed(Long id, User actor) {
        TicketTemplate t = load(id);
        boolean staff = actor.getRole() == Roles.ADMIN || actor.getRole() == Roles.ENGINEER;
        if (!staff) {
            boolean pickable = t.getKind() == TemplateKind.TICKET
                && t.isActive()
                && t.isVisibleToCustomers()
                && (t.getCompany() == null
                    || (actor.getCompany() != null && t.getCompany().getId().equals(actor.getCompany().getId())));
            if (!pickable) return;
        }
        templates.incrementUseCount(id);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private TicketTemplate load(Long id) {
        return templates.findDetailById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Template not found: " + id));
    }

    private void apply(TicketTemplate t, TemplateRequest req) {
        t.setKind(req.getKind());
        t.setName(req.getName().trim());
        t.setDescription(blankToNull(req.getDescription()));
        t.setBody(req.getBody().trim());
        t.setActive(req.isActive());
        t.setSortOrder(req.getSortOrder());

        if (req.getCompanyId() == null) {
            t.setCompany(null);
        } else {
            Company company = companies.findById(req.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Company not found: " + req.getCompanyId()));
            t.setCompany(company);
        }

        if (req.getKind() == TemplateKind.TICKET) {
            t.setTitleTemplate(blankToNull(req.getTitleTemplate()));
            t.setStepsTemplate(blankToNull(req.getStepsTemplate()));
            t.setCategory(req.getCategory());
            t.setPriority(req.getPriority());
            t.setVisibleToCustomers(req.isVisibleToCustomers());
        } else {
            // A canned reply has no ticket fields, and is never customer-facing. Cleared rather
            // than ignored, so switching a template's kind can't leave stale prefills behind that
            // would reappear if it were switched back.
            t.setTitleTemplate(null);
            t.setStepsTemplate(null);
            t.setCategory(null);
            t.setPriority(null);
            t.setVisibleToCustomers(false);
        }

        if (t.getBody().isEmpty()) throw new BusinessRuleException("A template needs a body");
    }

    private String blankToNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
