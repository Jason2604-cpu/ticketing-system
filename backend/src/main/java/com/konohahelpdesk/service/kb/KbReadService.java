package com.konohahelpdesk.service.kb;

import com.konohahelpdesk.dto.kb.*;
import com.konohahelpdesk.dto.kb.KbFeedbackRequest.Event;
import com.konohahelpdesk.model.kb.KbArticle;
import com.konohahelpdesk.model.kb.KbArticleVersion;
import com.konohahelpdesk.model.kb.KbStatus;
import com.konohahelpdesk.model.user.User;
import com.konohahelpdesk.repository.company.CompanyRepository;
import com.konohahelpdesk.repository.kb.KbArticleRepository;
import com.konohahelpdesk.repository.kb.KbCategoryRepository;
import com.konohahelpdesk.security.AuditLogger;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Knowledge Base reading — staff on /kb, customers on /help. Everything here reads the PUBLISHED
 * version of an article, never the working copy, and applies the visibility rule in SQL (lists) or
 * through {@link KbRules#canReadPublished} (single article), which say the same thing.
 */
@Service
public class KbReadService {

    private static final int MAX_PAGE_SIZE = 50;

    private final KbArticleRepository articles;
    private final KbCategoryRepository categories;
    private final CompanyRepository companies;
    private final KbAccess access;
    private final AuditLogger audit;

    public KbReadService(KbArticleRepository articles, KbCategoryRepository categories,
                         CompanyRepository companies, KbAccess access, AuditLogger audit) {
        this.articles = articles;
        this.categories = categories;
        this.companies = companies;
        this.access = access;
        this.audit = audit;
    }

    /** The SQL form of a viewer's read scope (see KbArticleRepository.findPublished). */
    record SqlScope(boolean unrestricted, String scope, boolean publicOnly) {
        static SqlScope of(KbViewer v) {
            List<Long> ids = v.readerScope();
            String literal = ids == null ? "{}"
                : ids.stream().map(String::valueOf).collect(Collectors.joining(",", "{", "}"));
            return new SqlScope(ids == null, literal, v.publicOnly());
        }
    }

    /**
     * Published articles the caller may read: recently updated first ({@code popular}: most viewed
     * first), or — with {@code q} — best match first with a highlighted excerpt.
     */
    @Transactional(readOnly = true)
    public KbPage<KbArticleSummary> list(User actor, String q, Long categoryId, boolean popular, int page, int size) {
        KbViewer v = access.viewer(actor);
        access.requireReader(v);
        SqlScope s = SqlScope.of(v);
        boolean staff = !v.isCustomer();
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));

        String query = KbSearch.normaliseQuery(q);
        if (query == null) {
            return KbPage.of(popular
                    ? articles.findPopular(categoryId, s.unrestricted(), s.scope(), s.publicOnly(), pageable)
                    : articles.findPublished(categoryId, s.unrestricted(), s.scope(), s.publicOnly(), pageable),
                r -> KbArticleSummary.from(r, staff));
        }
        return KbPage.of(
            articles.searchPublished(categoryId, s.unrestricted(), s.scope(), s.publicOnly(), query,
                KbSearch.HEADLINE_OPTIONS, pageable),
            r -> KbArticleSummary.from(r, staff, KbSearch.snippetParts(r.getSnippet())));
    }

    /**
     * Up to five articles matching what has been typed so far (a ticket title). Never an error for
     * a customer whose plan lacks the feature — the ticket form just gets nothing to suggest.
     *
     * @param companyId staff only: narrow to articles that apply to the company the ticket is for.
     *                  Ignored for customers, whose own company already is their scope.
     */
    @Transactional(readOnly = true)
    public List<KbArticleSummary> suggest(User actor, String text, Long companyId) {
        KbViewer v = access.viewer(actor);
        if (!v.mayRead()) return List.of();
        String terms = KbSearch.prefixQuery(text);
        if (terms == null) return List.of();

        SqlScope s = SqlScope.of(v);
        Long forCompany = v.isCustomer() ? null : companyId;
        boolean staff = !v.isCustomer();
        return articles.suggest(s.unrestricted(), s.scope(), s.publicOnly(), terms, forCompany, KbSearch.SUGGEST_LIMIT)
            .stream().map(r -> KbArticleSummary.from(r, staff)).toList();
    }

    /**
     * One published article. Anything the caller can't read — a draft, an archived article, an
     * Internal one for a customer, one pinned to another company — is the same 404 as a slug that
     * doesn't exist.
     */
    @Transactional
    public KbArticleResponse get(User actor, String slug) {
        KbViewer v = access.viewer(actor);
        access.requireReader(v);

        KbArticle a = articles.findBySlug(slug).orElseThrow(() -> KbAccess.notFound(slug));
        KbArticleVersion pub = a.getPublishedVersion();
        if (a.getStatus() != KbStatus.PUBLISHED || pub == null
            || !KbRules.canReadPublished(v, pub.getVisibility(), pub.companyIdList())) {
            throw KbAccess.notFound(slug);
        }

        boolean counted = KbRules.countsAsView(v);
        if (counted) articles.incrementViewCount(a.getId());

        KbRefs.CategoryRef category = KbRefs.CategoryRef.of(pub.getCategory());
        if (v.isCustomer()) {
            return new KbArticleResponse(a.getId(), a.getSlug(), pub.getTitle(), pub.getSummary(), pub.getBody(),
                category, pub.getVersion(), pub.getPublishedAt(), null, null, null, null, null, null);
        }
        List<KbRefs.CompanyRef> scope = pub.companyIdList().isEmpty() ? List.of()
            : companies.findAllById(pub.companyIdList()).stream().map(KbRefs.CompanyRef::of).toList();
        return new KbArticleResponse(a.getId(), a.getSlug(), pub.getTitle(), pub.getSummary(), pub.getBody(),
            category, pub.getVersion(), pub.getPublishedAt(),
            pub.getVisibility(), scope, KbRefs.UserRef.of(pub.getPublishedBy()),
            a.getViewCount() + (counted ? 1 : 0),
            v.isWriter() && KbRules.canEdit(v, a.companyIds()),
            a.isHasUnpublishedChanges());
    }

    /**
     * Records what a reader did with a suggestion on the new-ticket form (KB_SUGGESTION_OPENED /
     * KB_TICKET_AVOIDED), so a report can later say how many tickets the Knowledge Base saved.
     * Only for an article the caller could read — anything else is the usual 404.
     */
    @Transactional
    public void feedback(User actor, String slug, Event event) {
        KbViewer v = access.viewer(actor);
        access.requireReader(v);
        KbArticle a = articles.findBySlug(slug).orElseThrow(() -> KbAccess.notFound(slug));
        KbArticleVersion pub = a.getPublishedVersion();
        if (a.getStatus() != KbStatus.PUBLISHED || pub == null
            || !KbRules.canReadPublished(v, pub.getVisibility(), pub.companyIdList())) {
            throw KbAccess.notFound(slug);
        }
        audit.event("KB_" + event.name(), "articleId=" + a.getId() + " slug=" + slug);
    }

    /**
     * May this caller download an article's image or file? Writers who can edit the article: yes
     * (the editor shows drafts). Everyone else only when they can read the PUBLISHED version AND
     * that version actually links the file — so a file uploaded for an unpublished edit stays
     * private even to someone who could read the article.
     */
    @Transactional(readOnly = true)
    public boolean canReadAttachment(User actor, Long articleId, String mediaId) {
        KbViewer v = access.viewer(actor);
        KbArticle a = articles.findById(articleId).orElse(null);
        if (a == null) return false;
        if (v.isWriter() && KbRules.canEdit(v, a.companyIds())) return true;
        KbArticleVersion pub = a.getPublishedVersion();
        return a.getStatus() == KbStatus.PUBLISHED && pub != null
            && KbRules.canReadPublished(v, pub.getVisibility(), pub.companyIdList())
            && pub.getBody().contains(mediaId);
    }

    /** Categories with the number of articles this caller can read; customers don't see empty ones. */
    @Transactional(readOnly = true)
    public List<KbCategoryResponse> categories(User actor) {
        KbViewer v = access.viewer(actor);
        access.requireReader(v);
        SqlScope s = SqlScope.of(v);
        return categories.findWithVisibleCounts(s.unrestricted(), s.scope(), s.publicOnly()).stream()
            .filter(r -> !v.isCustomer() || r.getArticleCount() > 0)
            .map(r -> new KbCategoryResponse(r.getId(), r.getName(), r.getDescription(), r.getIcon(),
                r.getSortOrder() == null ? 0 : r.getSortOrder(), r.getArticleCount()))
            .toList();
    }
}
