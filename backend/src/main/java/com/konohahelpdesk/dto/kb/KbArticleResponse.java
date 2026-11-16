package com.konohahelpdesk.dto.kb;

import com.konohahelpdesk.model.kb.KbVisibility;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One published article as a reader sees it — the published version's text. The fields after
 * {@code publishedAt} are for staff and are null for customers.
 */
public record KbArticleResponse(
    Long id,
    String slug,
    String title,
    String summary,
    String body,
    KbRefs.CategoryRef category,
    int version,
    LocalDateTime publishedAt,
    // staff only
    KbVisibility visibility,
    List<KbRefs.CompanyRef> companies,
    KbRefs.UserRef publishedBy,
    Long viewCount,
    Boolean canEdit,
    Boolean hasUnpublishedChanges
) {}
