package com.konohahelpdesk.dto.kb;

import com.konohahelpdesk.model.kb.KbVisibility;
import com.konohahelpdesk.repository.kb.KbArticleRepository.PublishedRow;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A published article in a reader list, search results or suggestions. Built from the PUBLISHED
 * version, never the working copy. {@code visibility} and {@code viewCount} are staff-only and null
 * for customers; {@code snippet} is only set on search results.
 */
public record KbArticleSummary(
    Long id,
    String slug,
    String title,
    String summary,
    KbVisibility visibility,
    KbRefs.CategoryRef category,
    int version,
    LocalDateTime publishedAt,
    Long viewCount,
    List<KbSnippetPart> snippet
) {
    public static KbArticleSummary from(PublishedRow r, boolean staff) {
        return from(r, staff, null);
    }

    public static KbArticleSummary from(PublishedRow r, boolean staff, List<KbSnippetPart> snippet) {
        KbRefs.CategoryRef category = r.getCategoryId() == null ? null
            : new KbRefs.CategoryRef(r.getCategoryId(), r.getCategoryName(), r.getCategoryIcon());
        return new KbArticleSummary(r.getId(), r.getSlug(), r.getTitle(), r.getSummary(),
            staff ? KbVisibility.valueOf(r.getVisibility()) : null,
            category, r.getVersion() == null ? 0 : r.getVersion(), r.getPublishedAt(),
            staff ? r.getViewCount() : null, snippet);
    }
}
