package com.konohahelpdesk.model.kb;

import com.konohahelpdesk.model.company.Company;
import com.konohahelpdesk.model.user.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * A Knowledge Base article: the WORKING COPY that writers edit, plus a pointer to the version
 * readers see.
 *
 * Readers never read the fields on this row (title, body, visibility, companies...) — they read
 * {@link #getPublishedVersion()}. That is what lets an engineer edit a published article while
 * customers keep seeing the approved text until an Admin / KB manager publishes the change.
 *
 * Company scope works like ticket templates: an empty {@code companies} set = global, otherwise
 * pinned to exactly those companies. LAZY here (templates are EAGER) because KB lists are longer;
 * {@code hibernate.default_batch_fetch_size} turns the loads into one query per page.
 */
@Entity
@Table(name = "kb_articles")
public class KbArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** URL key. Follows the title until the first publish, then never changes, so links stay valid. */
    @Column(nullable = false, length = 160, unique = true)
    private String slug;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 300)
    private String summary;

    /** Markdown. Rendered without raw HTML on the frontend. */
    @Column(nullable = false, columnDefinition = "text")
    private String body = "";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private KbCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private KbVisibility visibility = KbVisibility.INTERNAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private KbStatus status = KbStatus.DRAFT;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "kb_article_companies",
               joinColumns = @JoinColumn(name = "article_id"),
               inverseJoinColumns = @JoinColumn(name = "company_id"))
    private Set<Company> companies = new LinkedHashSet<>();

    /** What readers see. Null until the first publish. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "published_version_id")
    private KbArticleVersion publishedVersion;

    /** The working copy differs from the published version. Always false before the first publish. */
    @Column(name = "has_unpublished_changes", nullable = false)
    private boolean hasUnpublishedChanges = false;

    /** Set when an engineer presses Request publish; cleared by publish or decline. */
    @Column(name = "publish_requested_at")
    private LocalDateTime publishRequestedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "publish_requested_by")
    private User publishRequestedBy;

    /** Reader views. Bumped with a bulk UPDATE, so it never touches updated_at. */
    @Column(name = "view_count", nullable = false)
    private long viewCount = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Latest publish. */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** True when pinned to specific companies rather than global. */
    public boolean isScoped() { return companies != null && !companies.isEmpty(); }

    /** The working copy's company ids, sorted. Empty = global. */
    public Set<Long> companyIds() {
        Set<Long> ids = new TreeSet<>();
        if (companies != null) companies.forEach(c -> ids.add(c.getId()));
        return ids;
    }

    public boolean isPublishRequested() { return publishRequestedAt != null; }

    public boolean everPublished() { return publishedVersion != null; }

    public Long getId() { return id; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body == null ? "" : body; }
    public KbCategory getCategory() { return category; }
    public void setCategory(KbCategory category) { this.category = category; }
    public KbVisibility getVisibility() { return visibility; }
    public void setVisibility(KbVisibility visibility) { this.visibility = visibility; }
    public KbStatus getStatus() { return status; }
    public void setStatus(KbStatus status) { this.status = status; }
    public Set<Company> getCompanies() { return companies; }
    public void setCompanies(Set<Company> companies) {
        // Mutate in place: replacing a Hibernate-managed collection on a loaded entity is what made
        // Role.setPrivileges throw on edit.
        if (this.companies == null) this.companies = new LinkedHashSet<>();
        this.companies.clear();
        if (companies != null) this.companies.addAll(companies);
    }
    public KbArticleVersion getPublishedVersion() { return publishedVersion; }
    public void setPublishedVersion(KbArticleVersion publishedVersion) { this.publishedVersion = publishedVersion; }
    public boolean isHasUnpublishedChanges() { return hasUnpublishedChanges; }
    public void setHasUnpublishedChanges(boolean hasUnpublishedChanges) { this.hasUnpublishedChanges = hasUnpublishedChanges; }
    public LocalDateTime getPublishRequestedAt() { return publishRequestedAt; }
    public User getPublishRequestedBy() { return publishRequestedBy; }
    public void requestPublish(User by, LocalDateTime at) { this.publishRequestedBy = by; this.publishRequestedAt = at; }
    public void clearPublishRequest() { this.publishRequestedBy = null; this.publishRequestedAt = null; }
    public long getViewCount() { return viewCount; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public User getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(User updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
}
