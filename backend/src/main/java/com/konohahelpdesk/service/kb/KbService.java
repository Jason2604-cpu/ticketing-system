package com.konohahelpdesk.service.kb;

import com.konohahelpdesk.config.AppProperties;
import com.konohahelpdesk.dto.kb.*;
import com.konohahelpdesk.exception.BusinessRuleException;
import com.konohahelpdesk.exception.DuplicateResourceException;
import com.konohahelpdesk.exception.ResourceNotFoundException;
import com.konohahelpdesk.model.company.Company;
import com.konohahelpdesk.model.kb.*;
import com.konohahelpdesk.model.media.MediaFile;
import com.konohahelpdesk.model.media.MediaKind;
import com.konohahelpdesk.model.user.User;
import com.konohahelpdesk.repository.company.CompanyRepository;
import com.konohahelpdesk.repository.kb.KbArticleRepository;
import com.konohahelpdesk.repository.kb.KbArticleVersionRepository;
import com.konohahelpdesk.repository.kb.KbCategoryRepository;
import com.konohahelpdesk.security.AuditLogger;
import com.konohahelpdesk.service.media.MediaStorageService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Knowledge Base writing: drafts, publishing, versions, archive, categories.
 *
 * Who may do what is decided by {@link KbRules} through {@link KbAccess}:
 *  - Admin / MANAGE_KNOWLEDGE_BASE ("managers") do everything, and are the ONLY ones who publish.
 *  - Engineers create and edit working copies within their company scope, restore old versions
 *    into the draft, delete their own never-published drafts, and press Request publish.
 *  - Customers have no write path (the controller doesn't let them in, and nothing here would).
 *
 * The working copy is what this service edits; readers only ever see the published version, so an
 * engineer's edit is invisible to readers until a manager publishes it.
 */
@Service
public class KbService {

    private static final int MAX_PAGE_SIZE = 100;

    private final KbArticleRepository articles;
    private final KbArticleVersionRepository versions;
    private final KbCategoryRepository categories;
    private final CompanyRepository companies;
    private final KbAccess access;
    private final AuditLogger audit;
    private final MediaStorageService media;
    private final KbNotifier notifier;
    private final int maxAttachments;

    public KbService(KbArticleRepository articles, KbArticleVersionRepository versions,
                     KbCategoryRepository categories, CompanyRepository companies,
                     KbAccess access, AuditLogger audit, MediaStorageService media, KbNotifier notifier,
                     AppProperties props) {
        this.articles = articles;
        this.versions = versions;
        this.categories = categories;
        this.companies = companies;
        this.access = access;
        this.audit = audit;
        this.media = media;
        this.notifier = notifier;
        this.maxAttachments = props.getMedia().getMaxAttachmentsPerArticle();
    }

    // ── Working copies ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public KbPage<KbManageSummary> list(User actor, KbStatus status, KbVisibility visibility, Long categoryId,
                                        boolean requestedOnly, String q, int page, int size) {
        KbViewer v = access.viewer(actor);
        access.requireWriter(v);

        boolean unrestricted = v.isManager() || v.scope() == null;
        // The IN list must not be empty; -1 matches no company, leaving only global articles.
        Collection<Long> scope = unrestricted || v.scope().isEmpty() ? List.of(-1L) : v.scope();
        String like = q == null || q.isBlank() ? null : "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";

        Page<KbArticle> result = articles.findForWriters(status, visibility, categoryId, requestedOnly, like,
            unrestricted, scope, PageRequest.of(Math.max(page, 0), clamp(size)));
        return KbPage.of(result, KbManageSummary::from);
    }

    /** Working copies waiting for a manager, among the ones the caller can edit. */
    @Transactional(readOnly = true)
    public long requestedCount(User actor) {
        KbViewer v = access.viewer(actor);
        access.requireWriter(v);
        boolean unrestricted = v.isManager() || v.scope() == null;
        return articles.countRequested(unrestricted, unrestricted || v.scope().isEmpty() ? List.of(-1L) : v.scope());
    }

    @Transactional(readOnly = true)
    public KbManageResponse get(User actor, Long id) {
        KbViewer v = access.viewer(actor);
        KbArticle a = load(id);
        access.requireEditable(v, a);
        return toResponse(v, a);
    }

    @Transactional
    public KbManageResponse create(User actor, KbArticleRequest req) {
        KbViewer v = access.viewer(actor);
        access.requireWriter(v);

        KbArticle a = new KbArticle();
        apply(v, a, req);
        a.setSlug(KbSlugs.unique(KbSlugs.slugify(a.getTitle()), articles::existsBySlug));
        a.setStatus(KbStatus.DRAFT);
        a.setCreatedBy(actor);
        a.setUpdatedBy(actor);
        KbArticle saved = articles.save(a);

        audit.event("KB_ARTICLE_CREATED", "articleId=" + saved.getId() + " visibility=" + saved.getVisibility()
            + " title=" + saved.getTitle());
        return toResponse(v, saved);
    }

    @Transactional
    public KbManageResponse update(User actor, Long id, KbArticleRequest req) {
        KbViewer v = access.viewer(actor);
        KbArticle a = load(id);
        access.requireEditable(v, a);

        String oldTitle = a.getTitle();
        apply(v, a, req);
        // The slug follows the title until the first publish; after that links must keep working.
        if (!a.everPublished() && !a.getTitle().equals(oldTitle)) {
            String current = a.getSlug();
            a.setSlug(KbSlugs.unique(KbSlugs.slugify(a.getTitle()),
                s -> !s.equals(current) && articles.existsBySlug(s)));
        }
        a.setUpdatedBy(actor);
        refreshChangeFlag(a);

        audit.event("KB_ARTICLE_UPDATED", "articleId=" + id + " visibility=" + a.getVisibility()
            + (a.isHasUnpublishedChanges() ? " unpublishedChanges=true" : ""));
        return toResponse(v, a);
    }

    /** Managers only. Snapshots the working copy as a new version and points readers at it. */
    @Transactional
    public KbManageResponse publish(User actor, Long id, String changeNote) {
        KbViewer v = access.viewer(actor);
        KbArticle a = load(id);
        access.requireEditable(v, a);
        access.requireManager(v);

        if (a.getStatus() == KbStatus.ARCHIVED) {
            throw new BusinessRuleException("Unarchive the article before publishing it");
        }
        requireSomethingToPublish(a);
        if (a.getBody().isBlank()) {
            throw new BusinessRuleException("Write the article before publishing it");
        }

        LocalDateTime now = LocalDateTime.now();
        int next = versions.maxVersion(a.getId()) + 1;
        KbArticleVersion version = versions.save(
            KbArticleVersion.snapshotOf(a, next, blankToNull(changeNote), actor, now));

        User requester = a.getPublishRequestedBy();
        a.setPublishedVersion(version);
        a.setStatus(KbStatus.PUBLISHED);
        a.setHasUnpublishedChanges(false);
        a.clearPublishRequest();
        a.setPublishedAt(now);

        audit.event("KB_ARTICLE_PUBLISHED", "articleId=" + id + " version=" + next
            + " visibility=" + version.getVisibility() + " scope=" + scopeLabel(version.companyIdList()));
        notifier.published(a, requester, actor, next);
        return toResponse(v, a);
    }

    /**
     * Engineers: ask an Admin / KB manager to publish the working copy. Managers are told once per
     * request — asking again while it is still waiting refreshes it without notifying them again.
     */
    @Transactional
    public KbManageResponse requestPublish(User actor, Long id) {
        KbViewer v = access.viewer(actor);
        KbArticle a = load(id);
        access.requireEditable(v, a);

        if (v.isManager()) throw new BusinessRuleException("You can publish this article yourself");
        if (!KbRules.canRequestPublish(v, a.companyIds())) throw new AccessDeniedException("Forbidden");
        if (a.getStatus() == KbStatus.ARCHIVED) throw new BusinessRuleException("This article is archived");
        requireSomethingToPublish(a);
        if (a.getBody().isBlank()) {
            throw new BusinessRuleException("Write the article before asking for it to be published");
        }

        boolean alreadyWaiting = a.isPublishRequested();
        a.requestPublish(actor, LocalDateTime.now());
        audit.event("KB_ARTICLE_PUBLISH_REQUESTED", "articleId=" + id + " visibility=" + a.getVisibility());
        if (!alreadyWaiting) notifier.publishRequested(a, actor);
        return toResponse(v, a);
    }

    /** Managers: turn down a publish request. The draft stays as it is. */
    @Transactional
    public KbManageResponse declinePublish(User actor, Long id, String note) {
        KbViewer v = access.viewer(actor);
        KbArticle a = load(id);
        access.requireEditable(v, a);
        access.requireManager(v);

        if (!a.isPublishRequested()) throw new BusinessRuleException("Nobody has asked for this article to be published");
        User requester = a.getPublishRequestedBy();
        a.clearPublishRequest();

        String reason = blankToNull(note);
        audit.event("KB_ARTICLE_PUBLISH_DECLINED", "articleId=" + id + " requestedBy=" + AuditLogger.describe(requester)
            + (reason == null ? "" : " note=" + reason));
        notifier.notPublished(a, requester, actor, reason, KbNotifier.Outcome.DECLINED);
        return toResponse(v, a);
    }

    /** Managers: hide from readers and suggestions. Versions are kept. */
    @Transactional
    public KbManageResponse archive(User actor, Long id) {
        KbViewer v = access.viewer(actor);
        KbArticle a = load(id);
        access.requireEditable(v, a);
        access.requireManager(v);

        if (a.getStatus() == KbStatus.ARCHIVED) throw new BusinessRuleException("The article is already archived");
        User requester = a.getPublishRequestedBy();
        a.setStatus(KbStatus.ARCHIVED);
        a.clearPublishRequest();
        audit.event("KB_ARTICLE_ARCHIVED", "articleId=" + id + " title=" + a.getTitle());
        // A waiting request ends here too — the engineer who asked shouldn't be left waiting.
        if (requester != null) notifier.notPublished(a, requester, actor, null, KbNotifier.Outcome.ARCHIVED);
        return toResponse(v, a);
    }

    @Transactional
    public KbManageResponse unarchive(User actor, Long id) {
        KbViewer v = access.viewer(actor);
        KbArticle a = load(id);
        access.requireEditable(v, a);
        access.requireManager(v);

        if (a.getStatus() != KbStatus.ARCHIVED) throw new BusinessRuleException("The article isn't archived");
        a.setStatus(a.everPublished() ? KbStatus.PUBLISHED : KbStatus.DRAFT);
        audit.event("KB_ARTICLE_UNARCHIVED", "articleId=" + id + " title=" + a.getTitle());
        return toResponse(v, a);
    }

    /** Only never-published drafts; a manager any of them, an engineer their own. */
    @Transactional
    public void delete(User actor, Long id) {
        KbViewer v = access.viewer(actor);
        KbArticle a = load(id);
        access.requireEditable(v, a);

        if (a.everPublished()) {
            throw new BusinessRuleException("A published article can't be deleted — archive it instead, so links to it keep working");
        }
        if (!KbRules.canDelete(v, false, userId(a.getCreatedBy()), a.companyIds())) {
            throw new AccessDeniedException("You can only delete drafts you created");
        }
        User requester = a.getPublishRequestedBy();
        // Its files go too — nothing else can reference a never-published article's uploads.
        for (MediaFile f : media.listFor(MediaKind.KB_ATTACHMENT, id)) media.delete(f.getId());
        articles.delete(a);
        audit.event("KB_ARTICLE_DELETED", "articleId=" + id + " title=" + a.getTitle());
        if (requester != null) notifier.notPublished(a, requester, actor, null, KbNotifier.Outcome.DELETED);
    }

    // ── Images and files ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<KbAttachmentResponse> attachments(User actor, Long id) {
        KbViewer v = access.viewer(actor);
        KbArticle a = load(id);
        access.requireEditable(v, a);
        String published = a.getPublishedVersion() == null ? null : a.getPublishedVersion().getBody();
        return media.listFor(MediaKind.KB_ATTACHMENT, id).stream()
            .map(m -> KbAttachmentResponse.from(m, published, a.getBody()))
            .toList();
    }

    /**
     * Stores an image or file for an article (magic-byte type check, size cap — the ticket
     * attachment rules). Uploading doesn't change the article: the editor inserts the returned URL
     * into the draft, and readers only get the file once a published version references it.
     */
    @Transactional
    public KbAttachmentResponse upload(User actor, Long id, MultipartFile file) {
        KbViewer v = access.viewer(actor);
        KbArticle a = load(id);
        access.requireEditable(v, a);
        if (media.countFor(MediaKind.KB_ATTACHMENT, id) >= maxAttachments) {
            throw new BusinessRuleException("An article can have at most " + maxAttachments + " images and files");
        }
        MediaFile m = media.storeKbAttachment(file, id, actor.getId());
        String published = a.getPublishedVersion() == null ? null : a.getPublishedVersion().getBody();
        return KbAttachmentResponse.from(m, published, a.getBody());
    }

    /**
     * Removes a file. Attachments aren't versioned, so this affects every version that links to
     * it — the editor warns first when the published version does.
     */
    @Transactional
    public void deleteAttachment(User actor, Long id, String mediaId) {
        KbViewer v = access.viewer(actor);
        KbArticle a = load(id);
        access.requireEditable(v, a);
        MediaFile m = media.find(mediaId)
            .filter(f -> f.getKind() == MediaKind.KB_ATTACHMENT && id.equals(f.getOwnerId()))
            .orElseThrow(() -> new ResourceNotFoundException("File not found"));
        media.delete(m.getId());
        audit.event("KB_ATTACHMENT_REMOVED", "articleId=" + id + " file=" + m.getOriginalName());
    }

    // ── Versions ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<KbVersionResponse> versions(User actor, Long id) {
        KbViewer v = access.viewer(actor);
        KbArticle a = load(id);
        access.requireEditable(v, a);

        List<KbArticleVersion> list = versions.findByArticleIdOrderByVersionDesc(id);
        Map<Long, Company> names = companiesById(list.stream().flatMap(x -> x.companyIdList().stream()).collect(Collectors.toSet()));
        Long current = a.getPublishedVersion() == null ? null : a.getPublishedVersion().getId();
        return list.stream().map(x -> toVersion(x, names, current, false)).toList();
    }

    @Transactional(readOnly = true)
    public KbVersionResponse version(User actor, Long id, int number) {
        KbViewer v = access.viewer(actor);
        KbArticle a = load(id);
        access.requireEditable(v, a);

        KbArticleVersion x = loadVersion(id, number);
        Long current = a.getPublishedVersion() == null ? null : a.getPublishedVersion().getId();
        return toVersion(x, companiesById(new HashSet<>(x.companyIdList())), current, true);
    }

    /**
     * Copies an old version into the WORKING COPY. Nothing changes for readers until a manager
     * publishes — restoring is an edit, publishing is the decision.
     */
    @Transactional
    public KbManageResponse restoreVersion(User actor, Long id, int number) {
        KbViewer v = access.viewer(actor);
        KbArticle a = load(id);
        access.requireEditable(v, a);

        KbArticleVersion x = loadVersion(id, number);
        // Companies deleted since then simply drop out.
        List<Company> scope = companies.findAllById(x.companyIdList());
        Set<Long> scopeIds = scope.stream().map(Company::getId).collect(Collectors.toSet());
        if (!KbRules.canEdit(v, scopeIds)) {
            throw new AccessDeniedException("That version is pinned to a company you aren't assigned to");
        }

        a.setTitle(x.getTitle());
        a.setSummary(x.getSummary());
        a.setBody(x.getBody());
        a.setVisibility(x.getVisibility());
        a.setCategory(x.getCategory());
        a.setCompanies(new LinkedHashSet<>(scope));
        a.setUpdatedBy(actor);
        refreshChangeFlag(a);

        audit.event("KB_ARTICLE_RESTORED", "articleId=" + id + " fromVersion=" + number);
        return toResponse(v, a);
    }

    // ── Categories ───────────────────────────────────────────────────────────

    /** Every category with its number of working copies. Writers (the editor's picker uses it). */
    @Transactional(readOnly = true)
    public List<KbCategoryResponse> categories(User actor) {
        access.requireWriter(access.viewer(actor));
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : articles.countByCategory()) counts.put((Long) row[0], (Long) row[1]);
        return categories.findAllByOrderBySortOrderAscNameAsc().stream()
            .map(c -> toCategory(c, counts.getOrDefault(c.getId(), 0L)))
            .toList();
    }

    @Transactional
    public KbCategoryResponse createCategory(User actor, KbCategoryRequest req) {
        access.requireManager(access.viewer(actor));
        String name = req.name().trim();
        if (categories.existsByNameIgnoreCase(name)) {
            throw new DuplicateResourceException("A category named \"" + name + "\" already exists");
        }
        KbCategory c = new KbCategory();
        applyCategory(c, req);
        KbCategory saved = categories.save(c);
        audit.event("KB_CATEGORY_CREATED", "categoryId=" + saved.getId() + " name=" + saved.getName());
        return toCategory(saved, 0);
    }

    @Transactional
    public KbCategoryResponse updateCategory(User actor, Long id, KbCategoryRequest req) {
        access.requireManager(access.viewer(actor));
        KbCategory c = loadCategory(id);
        String name = req.name().trim();
        if (categories.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new DuplicateResourceException("A category named \"" + name + "\" already exists");
        }
        applyCategory(c, req);
        audit.event("KB_CATEGORY_UPDATED", "categoryId=" + id + " name=" + c.getName());
        long count = 0;
        for (Object[] row : articles.countByCategory()) if (id.equals(row[0])) count = (Long) row[1];
        return toCategory(c, count);
    }

    /** Its articles become Uncategorised (published versions too, via ON DELETE SET NULL). */
    @Transactional
    public void deleteCategory(User actor, Long id) {
        access.requireManager(access.viewer(actor));
        KbCategory c = loadCategory(id);
        int moved = articles.clearCategory(id);
        categories.delete(c);
        audit.event("KB_CATEGORY_DELETED", "categoryId=" + id + " name=" + c.getName() + " movedArticles=" + moved);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void apply(KbViewer v, KbArticle a, KbArticleRequest req) {
        a.setTitle(req.title().trim());
        a.setSummary(blankToNull(req.summary()));
        a.setBody(req.body() == null ? "" : req.body().stripTrailing());
        a.setVisibility(req.visibility());
        a.setCategory(req.categoryId() == null ? null : loadCategory(req.categoryId()));

        // Empty = global. Resolved rather than trusted, so a deleted company can't pin it to nothing.
        Set<Long> wanted = req.companyIds() == null ? Set.of() : new TreeSet<>(req.companyIds());
        if (!KbRules.canEdit(v, wanted)) {
            throw new AccessDeniedException("You can only pin an article to companies you're assigned to");
        }
        List<Company> found = wanted.isEmpty() ? List.of() : companies.findAllById(wanted);
        if (found.size() != wanted.size()) {
            Set<Long> foundIds = found.stream().map(Company::getId).collect(Collectors.toSet());
            Long missing = wanted.stream().filter(x -> !foundIds.contains(x)).findFirst().orElse(null);
            throw new ResourceNotFoundException("Company not found: " + missing);
        }
        a.setCompanies(new LinkedHashSet<>(found));
    }

    /** Working copy vs the published version: the "unpublished changes" badge and the publish button. */
    private void refreshChangeFlag(KbArticle a) {
        a.setHasUnpublishedChanges(a.everPublished() && a.getPublishedVersion().differsFrom(a));
    }

    private static void requireSomethingToPublish(KbArticle a) {
        if (a.everPublished() && !a.isHasUnpublishedChanges()) {
            throw new BusinessRuleException("Nothing new to publish — readers already see this version");
        }
    }

    private KbManageResponse toResponse(KbViewer v, KbArticle a) {
        Set<Long> ids = a.companyIds();
        boolean archived = a.getStatus() == KbStatus.ARCHIVED;
        boolean pending = !a.everPublished() || a.isHasUnpublishedChanges();
        var permissions = new KbManageResponse.Permissions(
            KbRules.canEdit(v, ids),
            KbRules.canPublish(v) && !archived && pending,
            KbRules.canRequestPublish(v, ids) && !archived && pending,
            KbRules.canPublish(v) && a.isPublishRequested(),
            KbRules.canPublish(v),
            KbRules.canDelete(v, a.everPublished(), userId(a.getCreatedBy()), ids));
        return KbManageResponse.from(a, permissions);
    }

    private KbVersionResponse toVersion(KbArticleVersion x, Map<Long, Company> names, Long currentId, boolean withBody) {
        List<KbRefs.CompanyRef> scope = x.companyIdList().stream()
            .map(names::get).filter(Objects::nonNull).map(KbRefs.CompanyRef::of).toList();
        return new KbVersionResponse(x.getVersion(), x.getTitle(), x.getSummary(), withBody ? x.getBody() : null,
            x.getVisibility(), KbRefs.CategoryRef.of(x.getCategory()), scope, x.getChangeNote(),
            KbRefs.UserRef.of(x.getPublishedBy()), x.getPublishedAt(), x.getId().equals(currentId));
    }

    private Map<Long, Company> companiesById(Set<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return companies.findAllById(ids).stream().collect(Collectors.toMap(Company::getId, Function.identity()));
    }

    private void applyCategory(KbCategory c, KbCategoryRequest req) {
        String icon = blankToNull(req.icon());
        if (icon != null && !KbCategory.ICONS.contains(icon)) {
            throw new BusinessRuleException("Unknown icon: " + icon);
        }
        c.setName(req.name().trim());
        c.setDescription(blankToNull(req.description()));
        c.setIcon(icon);
        c.setSortOrder(req.sortOrder() == null ? 0 : req.sortOrder());
    }

    private static KbCategoryResponse toCategory(KbCategory c, long count) {
        return new KbCategoryResponse(c.getId(), c.getName(), c.getDescription(), c.getIcon(), c.getSortOrder(), count);
    }

    private KbArticle load(Long id) {
        return articles.findById(id).orElseThrow(() -> KbAccess.notFound(id));
    }

    private KbArticleVersion loadVersion(Long articleId, int number) {
        return versions.findByArticleIdAndVersion(articleId, number)
            .orElseThrow(() -> new ResourceNotFoundException("Version " + number + " not found"));
    }

    private KbCategory loadCategory(Long id) {
        return categories.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
    }

    private static Long userId(User u) {
        return u == null ? null : u.getId();
    }

    private static String scopeLabel(List<Long> companyIds) {
        return companyIds.isEmpty() ? "global" : "companies" + companyIds;
    }

    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static int clamp(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
