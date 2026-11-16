package com.konohahelpdesk.dto.kb;

import com.konohahelpdesk.model.kb.KbStatus;
import com.konohahelpdesk.model.kb.KbVisibility;

import java.time.LocalDateTime;

/**
 * An article linked to a ticket. Title and summary are the published version's when there is one.
 * Fields after {@code summary} are for staff and null for customers.
 *
 * @param visibleToCustomer the ticket's customer could open it (published, Public, applies to
 *                          their company, and their plan includes the Knowledge Base)
 */
public record KbTicketArticleResponse(
    Long articleId,
    String slug,
    String title,
    String summary,
    // staff only
    KbStatus status,
    KbVisibility visibility,
    Boolean visibleToCustomer,
    KbRefs.UserRef linkedBy,
    LocalDateTime linkedAt
) {}
