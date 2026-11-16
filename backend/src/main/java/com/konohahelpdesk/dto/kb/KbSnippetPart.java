package com.konohahelpdesk.dto.kb;

/**
 * One piece of a search excerpt. The excerpt is sent as plain-text pieces rather than HTML with
 * {@code <mark>} tags, so the frontend can highlight matches without ever rendering article text
 * as HTML.
 */
public record KbSnippetPart(String text, boolean match) {}
