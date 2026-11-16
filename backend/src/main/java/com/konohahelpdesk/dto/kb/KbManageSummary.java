package com.konohahelpdesk.dto.kb;

import com.konohahelpdesk.model.kb.KbArticle;
import com.konohahelpdesk.model.kb.KbStatus;
import com.konohahelpdesk.model.kb.KbVisibility;

import java.time.LocalDateTime;
import java.util.List;

/** A working copy in the writers' list. */
public record KbManageSummary(
    Long id,
    String slug,
    String title,
    String summary,
    KbStatus status,
    KbVisibility visibility,
    KbRefs.CategoryRef category,
    List<KbRefs.CompanyRef> companies,
    Integer publishedVersion,
    boolean hasUnpublishedChanges,
    LocalDateTime publishRequestedAt,
    KbRefs.UserRef publishRequestedBy,
    LocalDateTime updatedAt,
    KbRefs.UserRef updatedBy,
    long viewCount
) {
    public static KbManageSummary from(KbArticle a) {
        return new KbManageSummary(a.getId(), a.getSlug(), a.getTitle(), a.getSummary(), a.getStatus(),
            a.getVisibility(), KbRefs.CategoryRef.of(a.getCategory()),
            a.getCompanies().stream().map(KbRefs.CompanyRef::of).toList(),
            a.getPublishedVersion() == null ? null : a.getPublishedVersion().getVersion(),
            a.isHasUnpublishedChanges(), a.getPublishRequestedAt(), KbRefs.UserRef.of(a.getPublishRequestedBy()),
            a.getUpdatedAt(), KbRefs.UserRef.of(a.getUpdatedBy()), a.getViewCount());
    }
}
