package com.konohahelpdesk.dto.kb;

import com.konohahelpdesk.model.kb.KbVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Create / save-draft payload. Saving never publishes: the body may still be empty, and nothing
 * here changes what readers see until an Admin / KB manager publishes.
 *
 * @param companyIds empty or null = global; otherwise pinned to exactly these companies.
 */
public record KbArticleRequest(
    @NotBlank(message = "Give the article a title")
    @Size(min = 4, max = 200, message = "The title must be 4 to 200 characters")
    String title,

    @Size(max = 300, message = "The summary can be at most 300 characters")
    String summary,

    @Size(max = 100_000, message = "The article is too long (100,000 characters at most)")
    String body,

    Long categoryId,

    @NotNull(message = "Choose who can read the article")
    KbVisibility visibility,

    Set<Long> companyIds
) {}
