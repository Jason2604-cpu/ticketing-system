package com.konohahelpdesk.repository.kb;

import com.konohahelpdesk.model.kb.KbArticleVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface KbArticleVersionRepository extends JpaRepository<KbArticleVersion, Long> {

    List<KbArticleVersion> findByArticleIdOrderByVersionDesc(Long articleId);

    Optional<KbArticleVersion> findByArticleIdAndVersion(Long articleId, int version);

    @Query("select coalesce(max(v.version), 0) from KbArticleVersion v where v.article.id = :articleId")
    int maxVersion(@Param("articleId") Long articleId);
}
