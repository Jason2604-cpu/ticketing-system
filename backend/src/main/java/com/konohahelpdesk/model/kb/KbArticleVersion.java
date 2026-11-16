package com.konohahelpdesk.model.kb;

import com.konohahelpdesk.model.user.User;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * One publish of an article: an immutable snapshot of the working copy at that moment.
 *
 * Readers see the version {@link KbArticle#getPublishedVersion()} points at. Visibility, category
 * and company scope are snapshotted too, because they are part of what was approved — widening an
 * article from Internal to Public is an edit like any other and only takes effect when published.
 *
 * The table also has a generated {@code search_vector} column (V5) that is deliberately not mapped.
 */
@Entity
@Table(name = "kb_article_versions")
public class KbArticleVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "article_id", nullable = false, updatable = false)
    private KbArticle article;

    /** 1, 2, 3… per article. */
    @Column(nullable = false, updatable = false)
    private int version;

    @Column(nullable = false, length = 200, updatable = false)
    private String title;

    @Column(length = 300, updatable = false)
    private String summary;

    @Column(nullable = false, columnDefinition = "text", updatable = false)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private KbVisibility visibility;

    /** Not updatable from Java; the database sets it to null if the category is deleted. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", updatable = false)
    private KbCategory category;

    /** Company scope at publish time, sorted. Empty = global. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "company_ids", nullable = false, updatable = false, columnDefinition = "bigint[]")
    private Long[] companyIds = new Long[0];

    @Column(name = "change_note", length = 200, updatable = false)
    private String changeNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "published_by", updatable = false)
    private User publishedBy;

    @Column(name = "published_at", nullable = false, updatable = false)
    private LocalDateTime publishedAt;

    protected KbArticleVersion() {}

    /** Snapshot of the article's working copy as it is now. */
    public static KbArticleVersion snapshotOf(KbArticle a, int version, String changeNote, User by, LocalDateTime at) {
        KbArticleVersion v = new KbArticleVersion();
        v.article = a;
        v.version = version;
        v.title = a.getTitle();
        v.summary = a.getSummary();
        v.body = a.getBody();
        v.visibility = a.getVisibility();
        v.category = a.getCategory();
        v.companyIds = a.companyIds().toArray(Long[]::new);
        v.changeNote = changeNote;
        v.publishedBy = by;
        v.publishedAt = at;
        return v;
    }

    /** True when the article's working copy says something different from this version. */
    public boolean differsFrom(KbArticle a) {
        Long catHere = category == null ? null : category.getId();
        Long catThere = a.getCategory() == null ? null : a.getCategory().getId();
        return !java.util.Objects.equals(title, a.getTitle())
            || !java.util.Objects.equals(summary, a.getSummary())
            || !java.util.Objects.equals(body, a.getBody())
            || visibility != a.getVisibility()
            || !java.util.Objects.equals(catHere, catThere)
            || !companyIdList().equals(List.copyOf(a.companyIds()));
    }

    public List<Long> companyIdList() {
        return companyIds == null ? List.of() : Arrays.stream(companyIds).sorted().toList();
    }

    public Long getId() { return id; }
    public KbArticle getArticle() { return article; }
    public int getVersion() { return version; }
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public String getBody() { return body; }
    public KbVisibility getVisibility() { return visibility; }
    public KbCategory getCategory() { return category; }
    public String getChangeNote() { return changeNote; }
    public User getPublishedBy() { return publishedBy; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
}
