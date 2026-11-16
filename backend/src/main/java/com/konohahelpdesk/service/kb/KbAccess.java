package com.konohahelpdesk.service.kb;

import com.konohahelpdesk.exception.FeatureNotEnabledException;
import com.konohahelpdesk.exception.ResourceNotFoundException;
import com.konohahelpdesk.model.kb.KbArticle;
import com.konohahelpdesk.model.user.Roles;
import com.konohahelpdesk.model.user.User;
import com.konohahelpdesk.security.CurrentUser;
import com.konohahelpdesk.service.company.CompanyScope;
import com.konohahelpdesk.service.subscription.CompanySubscriptionService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Turns the signed-in user into a {@link KbViewer} and applies {@link KbRules}, throwing the right
 * error. Every Knowledge Base endpoint goes through here — the same idea as {@link CompanyScope}.
 *
 * Errors: an article the caller can't see is a 404 (never confirm it exists); an action the caller
 * isn't allowed to take on an article they CAN see is a 403; a customer whose plan lacks the
 * feature gets the usual FeatureNotEnabledException (403 with the "not in your plan" message).
 */
@Component
public class KbAccess {

    public static final String FEATURE = "KNOWLEDGE_BASE";
    public static final String MANAGE_AUTHORITY = "PRIV_MANAGE_KNOWLEDGE_BASE";

    private final CurrentUser currentUser;
    private final CompanyScope companyScope;
    private final CompanySubscriptionService subscriptions;

    public KbAccess(CurrentUser currentUser, CompanyScope companyScope, CompanySubscriptionService subscriptions) {
        this.currentUser = currentUser;
        this.companyScope = companyScope;
        this.subscriptions = subscriptions;
    }

    /** The signed-in user as the KB rules see them. Reads authorities, so a custom-role privilege counts. */
    public KbViewer viewer(User actor) {
        if (actor.getRole() == Roles.ADMIN || currentUser.hasAuthority(MANAGE_AUTHORITY)) {
            return KbViewer.manager(actor.getId());
        }
        if (actor.getRole() == Roles.ENGINEER) {
            return KbViewer.engineer(actor.getId(), companyScope.visibleCompanyIds(actor));
        }
        if (actor.getRole().isCustomerSide()) {
            Long companyId = actor.getCompany() == null ? null : actor.getCompany().getId();
            boolean feature = companyId != null && subscriptions.hasFeature(companyId, FEATURE);
            return KbViewer.customer(actor.getId(), companyId, feature);
        }
        return KbViewer.none(actor.getId());
    }

    /**
     * The KB rules as a customer of this company would see them — used to tell staff whether the
     * customer on a ticket can open an article before they send it a link.
     */
    public KbViewer customerViewerFor(Long companyId) {
        return KbViewer.customer(null, companyId, companyId != null && subscriptions.hasFeature(companyId, FEATURE));
    }

    /** Readers: staff with KB access, or customers whose plan includes it. */
    public void requireReader(KbViewer v) {
        if (v.isCustomer() && !v.featureEnabled()) throw new FeatureNotEnabledException(FEATURE);
        if (!v.mayRead()) throw new AccessDeniedException("Forbidden");
    }

    /** Writers (managers and engineers). */
    public void requireWriter(KbViewer v) {
        if (!v.isWriter()) throw new AccessDeniedException("Forbidden");
    }

    /** Admin / MANAGE_KNOWLEDGE_BASE: publish, decline, archive, categories. */
    public void requireManager(KbViewer v) {
        if (!v.isManager()) throw new AccessDeniedException("Only an Admin or a Knowledge Base manager can do that");
    }

    /** The working copy is visible to this writer, or it's a 404. */
    public void requireEditable(KbViewer v, KbArticle a) {
        requireWriter(v);
        if (!KbRules.canEdit(v, a.companyIds())) throw notFound(a.getId());
    }

    public static ResourceNotFoundException notFound(Object idOrSlug) {
        return new ResourceNotFoundException("Article not found: " + idOrSlug);
    }
}
