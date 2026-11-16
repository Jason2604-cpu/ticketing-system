package com.konohahelpdesk.dto.kb;

import com.konohahelpdesk.model.kb.KbVisibility;

import java.time.LocalDateTime;
import java.util.List;

/** One published version. {@code body} is null in the history list and filled when opening one. */
public record KbVersionResponse(
    int version,
    String title,
    String summary,
    String body,
    KbVisibility visibility,
    KbRefs.CategoryRef category,
    List<KbRefs.CompanyRef> companies,
    String changeNote,
    KbRefs.UserRef publishedBy,
    LocalDateTime publishedAt,
    boolean current
) {}
