package com.konohahelpdesk.dto.kb;

import com.konohahelpdesk.model.kb.KbArticle;
import com.konohahelpdesk.model.kb.KbStatus;
import com.konohahelpdesk.model.kb.KbVisibility;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A working copy for the editor, with what the caller may do to it. The buttons in the editor are
 * driven by {@code permissions}, which come from the same KbRules the endpoints enforce.
 */
public record KbManageResponse(
    Long id,
    String slug,
    String title,
    String summary,
    String body,
    KbStatus status,
    KbVisibility visibility,
    KbRefs.CategoryRef category,
    List<KbRefs.CompanyRef> companies,
    Integer publishedVersion,
    LocalDateTime publishedAt,
    boolean hasUnpublishedChanges,
    LocalDateTime publishRequestedAt,
    KbRefs.UserRef publishRequestedBy,
    KbRefs.UserRef createdBy,
    LocalDateTime createdAt,
    KbRefs.UserRef updatedBy,
    LocalDateTime updatedAt,
    long viewCount,
    Permissions permissions
) {
    public record Permissions(boolean canEdit, boolean canPublish, boolean canRequestPublish,
                              boolean canDecline, boolean canArchive, boolean canDelete) {}

    public static KbManageResponse from(KbArticle a, Permissions permissions) {
        return new KbManageResponse(a.getId(), a.getSlug(), a.getTitle(), a.getSummary(), a.getBody(),
            a.getStatus(), a.getVisibility(), KbRefs.CategoryRef.of(a.getCategory()),
            a.getCompanies().stream().map(KbRefs.CompanyRef::of).toList(),
            a.getPublishedVersion() == null ? null : a.getPublishedVersion().getVersion(),
            a.getPublishedAt(), a.isHasUnpublishedChanges(), a.getPublishRequestedAt(),
            KbRefs.UserRef.of(a.getPublishRequestedBy()), KbRefs.UserRef.of(a.getCreatedBy()), a.getCreatedAt(),
            KbRefs.UserRef.of(a.getUpdatedBy()), a.getUpdatedAt(), a.getViewCount(), permissions);
    }
}
