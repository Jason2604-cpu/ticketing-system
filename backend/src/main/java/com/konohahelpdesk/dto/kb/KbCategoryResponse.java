package com.konohahelpdesk.dto.kb;

/**
 * A category with an article count: for readers, the published articles THEY can read; on the
 * categories admin page, every working copy in it.
 */
public record KbCategoryResponse(Long id, String name, String description, String icon, int sortOrder, long articleCount) {}
