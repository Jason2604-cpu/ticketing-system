package com.konohahelpdesk.dto.kb;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param icon      one of {@code KbCategory.ICONS}, or empty.
 * @param sortOrder lower first; null = 0. Boxed because Jackson 3 refuses a missing primitive in a record.
 */
public record KbCategoryRequest(
    @NotBlank(message = "Give the category a name")
    @Size(max = 80, message = "The name can be at most 80 characters")
    String name,

    @Size(max = 200, message = "The description can be at most 200 characters")
    String description,

    @Size(max = 40)
    String icon,

    Integer sortOrder
) {}
