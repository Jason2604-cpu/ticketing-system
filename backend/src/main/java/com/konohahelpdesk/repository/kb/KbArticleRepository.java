package com.konohahelpdesk.repository.kb;

import com.konohahelpdesk.model.kb.KbArticle;
import com.konohahelpdesk.model.kb.KbStatus;
import com.konohahelpdesk.model.kb.KbVisibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface KbArticleRepository extends JpaRepository<KbArticle, Long> {

    Optional<KbArticle> findBySlug(String slug);

    boolean existsBySlug(String slug);

    /**
     * The writers' list (working copies). Every filter is optional. {@code unrestricted = false}
     * keeps only articles the caller can edit: global ones, and pinned ones whose companies are all
     * inside {@code scope} (KbRules.canEdit).
     */
    @Query(value = """
        select a from KbArticle a
        where (:status is null or a.status = :status)
          and (:visibility is null or a.visibility = :visibility)
          and (:categoryId is null or a.category.id = :categoryId)
          and (:requestedOnly = false or a.publishRequestedAt is not null)
          and (:q is null or lower(a.title) like :q escape '\\')
          and (:unrestricted = true or not exists (
                select 1 from KbArticle x join x.companies c where x = a and c.id not in :scope))
        order by a.updatedAt desc, a.id desc
        """,
        countQuery = """
        select count(a) from KbArticle a
        where (:status is null or a.status = :status)
          and (:visibility is null or a.visibility = :visibility)
          and (:categoryId is null or a.category.id = :categoryId)
          and (:requestedOnly = false or a.publishRequestedAt is not null)
          and (:q is null or lower(a.title) like :q escape '\\')
          and (:unrestricted = true or not exists (
                select 1 from KbArticle x join x.companies c where x = a and c.id not in :scope))
        """)
    Page<KbArticle> findForWriters(@Param("status") KbStatus status,
                                   @Param("visibility") KbVisibility visibility,
                                   @Param("categoryId") Long categoryId,
                                   @Param("requestedOnly") boolean requestedOnly,
                                   @Param("q") String q,
                                   @Param("unrestricted") boolean unrestricted,
                                   @Param("scope") Collection<Long> scope,
                                   Pageable pageable);

    /**
     * "Needs publishing": how many working copies are waiting for a manager, within the same scope
     * as {@link #findForWriters}. Polled by the sidebar badge — one count over a partial index.
     */
    @Query("""
        select count(a) from KbArticle a
        where a.publishRequestedAt is not null
          and (:unrestricted = true or not exists (
                select 1 from KbArticle x join x.companies c where x = a and c.id not in :scope))
        """)
    long countRequested(@Param("unrestricted") boolean unrestricted, @Param("scope") Collection<Long> scope);

    // ── Reader SQL ───────────────────────────────────────────────────────────
    //
    // Everything readers see comes from the PUBLISHED version (v), never the working copy (a.title,
    // a.body...). Who may read what is decided here, in SQL, by READABLE — the same rule as
    // KbRules.canReadPublished — so a "top 5" is five rows the caller may actually open:
    //  - :unrestricted  managers (and unassigned engineers): every company;
    //  - otherwise      global articles plus those pinned to a company in :scope
    //                   (a Postgres array literal such as "{3,7}");
    //  - :publicOnly    customers.

    String READABLE = """
          a.status = 'PUBLISHED'
          and (:unrestricted or cardinality(v.company_ids) = 0 or v.company_ids && cast(:scope as bigint[]))
          and (not :publicOnly or v.visibility = 'PUBLIC')
        """;

    String IN_CATEGORY = " and (cast(:categoryId as bigint) is null or v.category_id = cast(:categoryId as bigint)) ";

    String ROW = """
        select a.id as "id", a.slug as "slug", v.title as "title", v.summary as "summary",
               v.visibility as "visibility", v.version as "version", v.published_at as "publishedAt",
               a.view_count as "viewCount", c.id as "categoryId", c.name as "categoryName", c.icon as "categoryIcon"
        """;

    String FROM_PUBLISHED = """
        from kb_articles a
        join kb_article_versions v on v.id = a.published_version_id
        left join kb_categories c on c.id = v.category_id
        """;

    /**
     * The body with Markdown punctuation removed, so a search snippet reads as text: images and
     * links keep only their label, emphasis / code marks (* ` ~) disappear, and heading, quote and
     * table marks (# > |) become spaces. Underscores stay — they're usually part of a file or
     * setting name in an IT article.
     */
    String PLAIN_BODY = "regexp_replace(regexp_replace(regexp_replace(regexp_replace(v.body,"
        + " '!\\[([^]]*)\\]\\([^)]*\\)', '\\1', 'g'),"
        + " '\\[([^]]*)\\]\\([^)]*\\)', '\\1', 'g'),"
        + " '[*`~]+', '', 'g'),"
        + " '[#>|]+', ' ', 'g')";

    /** One row per published article the caller may read. */
    interface PublishedRow {
        Long getId();
        String getSlug();
        String getTitle();
        String getSummary();
        String getVisibility();
        Integer getVersion();
        LocalDateTime getPublishedAt();
        Long getViewCount();
        Long getCategoryId();
        String getCategoryName();
        String getCategoryIcon();
    }

    /** A search hit: the row plus a ts_headline excerpt, matches wrapped in KbSearch.START / KbSearch.STOP. */
    interface SearchRow extends PublishedRow {
        String getSnippet();
    }

    /** Published articles for readers, newest first. */
    @Query(nativeQuery = true,
        value = ROW + FROM_PUBLISHED + " where " + READABLE + IN_CATEGORY
              + " order by v.published_at desc, a.id desc",
        countQuery = "select count(*) " + FROM_PUBLISHED + " where " + READABLE + IN_CATEGORY)
    Page<PublishedRow> findPublished(@Param("categoryId") Long categoryId,
                                     @Param("unrestricted") boolean unrestricted,
                                     @Param("scope") String scope,
                                     @Param("publicOnly") boolean publicOnly,
                                     Pageable pageable);

    /** The same list, most viewed first ("Most viewed" on the Knowledge Base home). */
    @Query(nativeQuery = true,
        value = ROW + FROM_PUBLISHED + " where " + READABLE + IN_CATEGORY
              + " order by a.view_count desc, v.published_at desc, a.id desc",
        countQuery = "select count(*) " + FROM_PUBLISHED + " where " + READABLE + IN_CATEGORY)
    Page<PublishedRow> findPopular(@Param("categoryId") Long categoryId,
                                   @Param("unrestricted") boolean unrestricted,
                                   @Param("scope") String scope,
                                   @Param("publicOnly") boolean publicOnly,
                                   Pageable pageable);

    /**
     * The search page. {@code websearch_to_tsquery} accepts what people type into a search box —
     * "exact phrase", -excluded, or plain words (all required) — and never fails on odd input.
     * Best match first: title (weight A) beats summary (B) beats body (C). The snippet is only
     * computed for the rows on the page (Postgres evaluates the expensive select list after LIMIT).
     */
    @Query(nativeQuery = true,
        value = ROW + ", ts_headline('english', " + PLAIN_BODY + ", s.query, cast(:headline as text)) as \"snippet\" "
              + FROM_PUBLISHED + " cross join websearch_to_tsquery('english', cast(:q as text)) as s(query)"
              + " where " + READABLE + IN_CATEGORY + " and v.search_vector @@ s.query"
              + " order by ts_rank_cd(v.search_vector, s.query) desc, v.published_at desc, a.id desc",
        countQuery = "select count(*) " + FROM_PUBLISHED
              + " cross join websearch_to_tsquery('english', cast(:q as text)) as s(query)"
              + " where " + READABLE + IN_CATEGORY + " and v.search_vector @@ s.query")
    Page<SearchRow> searchPublished(@Param("categoryId") Long categoryId,
                                    @Param("unrestricted") boolean unrestricted,
                                    @Param("scope") String scope,
                                    @Param("publicOnly") boolean publicOnly,
                                    @Param("q") String q,
                                    @Param("headline") String headlineOptions,
                                    Pageable pageable);

    /**
     * Type-ahead for the ticket forms. {@code terms} is a prefix OR query built by KbSearch
     * ("printer:* | offline:* | updat:*"): half-typed words match, and an article sharing any
     * meaningful word appears, best match first. {@code companyId} (staff only) narrows to what
     * applies to that customer: global articles and those pinned to it.
     */
    @Query(nativeQuery = true,
        value = ROW + FROM_PUBLISHED + " cross join to_tsquery('english', cast(:terms as text)) as s(query)"
              + " where " + READABLE + " and v.search_vector @@ s.query"
              + " and (cast(:companyId as bigint) is null or cardinality(v.company_ids) = 0"
              + "      or cast(:companyId as bigint) = any(v.company_ids))"
              + " order by ts_rank_cd(v.search_vector, s.query) desc, a.view_count desc, a.id desc"
              + " limit :limit")
    List<PublishedRow> suggest(@Param("unrestricted") boolean unrestricted,
                               @Param("scope") String scope,
                               @Param("publicOnly") boolean publicOnly,
                               @Param("terms") String terms,
                               @Param("companyId") Long companyId,
                               @Param("limit") int limit);

    /** Reader view counter: one UPDATE, no entity load, doesn't touch updated_at. */
    @Modifying
    @Query("update KbArticle a set a.viewCount = a.viewCount + 1 where a.id = :id")
    int incrementViewCount(@Param("id") Long id);

    /** Working copies per category, for the categories admin page: rows of [categoryId, count]. */
    @Query("select a.category.id, count(a) from KbArticle a where a.category is not null group by a.category.id")
    List<Object[]> countByCategory();

    /** Moves a category's articles to Uncategorised before the category is deleted. */
    @Modifying
    @Query("update KbArticle a set a.category = null where a.category.id = :categoryId")
    int clearCategory(@Param("categoryId") Long categoryId);
}
