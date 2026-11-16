package com.konohahelpdesk.repository.kb;

import com.konohahelpdesk.model.kb.KbCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface KbCategoryRepository extends JpaRepository<KbCategory, Long> {

    List<KbCategory> findAllByOrderBySortOrderAscNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    interface ReaderCategoryRow {
        Long getId();
        String getName();
        String getDescription();
        String getIcon();
        Integer getSortOrder();
        Long getArticleCount();
    }

    /**
     * Every category with the number of published articles in it that the caller may read. Same
     * visibility parameters as {@code KbArticleRepository.findPublished}. Categories number in the
     * tens, so the correlated count is cheap.
     */
    @Query(nativeQuery = true, value = """
        select c.id as "id", c.name as "name", c.description as "description", c.icon as "icon",
               c.sort_order as "sortOrder",
               (select count(*)
                  from kb_articles a
                  join kb_article_versions v on v.id = a.published_version_id
                 where a.status = 'PUBLISHED'
                   and v.category_id = c.id
                   and (:unrestricted or cardinality(v.company_ids) = 0 or v.company_ids && cast(:scope as bigint[]))
                   and (not :publicOnly or v.visibility = 'PUBLIC')) as "articleCount"
        from kb_categories c
        order by c.sort_order, lower(c.name)
        """)
    List<ReaderCategoryRow> findWithVisibleCounts(@Param("unrestricted") boolean unrestricted,
                                                  @Param("scope") String scope,
                                                  @Param("publicOnly") boolean publicOnly);
}
