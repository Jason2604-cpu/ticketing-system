package com.konohahelpdesk.dto.kb;

import jakarta.validation.constraints.Size;

/** Optional reason when a manager declines an engineer's publish request. */
public record KbDeclineRequest(
    @Size(max = 300, message = "The note can be at most 300 characters")
    String note
) {}
