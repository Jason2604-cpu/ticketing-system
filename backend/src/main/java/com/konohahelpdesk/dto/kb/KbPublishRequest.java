package com.konohahelpdesk.dto.kb;

import jakarta.validation.constraints.Size;

/** Optional note stored on the new version, e.g. "Added the Windows 11 steps". */
public record KbPublishRequest(
    @Size(max = 200, message = "The change note can be at most 200 characters")
    String changeNote
) {}
