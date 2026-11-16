package com.konohahelpdesk.dto.kb;

import com.konohahelpdesk.model.media.MediaFile;

import java.time.LocalDateTime;

/**
 * An image or file uploaded to an article. Articles reference it by {@code url} in their Markdown
 * (an image as {@code ![name](url)}, anything else as a link).
 *
 * @param inPublished the version readers see references it — removing it breaks that version
 * @param inDraft     the working copy references it
 */
public record KbAttachmentResponse(
    String id,
    String name,
    String contentType,
    long sizeBytes,
    String url,
    boolean image,
    LocalDateTime createdAt,
    boolean inPublished,
    boolean inDraft
) {
    public static String urlOf(String mediaId) {
        return "/api/media/" + mediaId;
    }

    public static KbAttachmentResponse from(MediaFile m, String publishedBody, String draftBody) {
        String url = urlOf(m.getId());
        return new KbAttachmentResponse(m.getId(), m.getOriginalName(), m.getContentType(), m.getSizeBytes(), url,
            m.getContentType() != null && m.getContentType().startsWith("image/"), m.getCreatedAt(),
            publishedBody != null && publishedBody.contains(m.getId()),
            draftBody != null && draftBody.contains(m.getId()));
    }
}
