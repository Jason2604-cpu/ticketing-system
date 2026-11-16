package com.konohahelpdesk.model.kb;

/** Who may read a published article. */
public enum KbVisibility {
    /** Staff only. */
    INTERNAL,
    /** Staff, and customers whose plan includes KNOWLEDGE_BASE (within the article's company scope). */
    PUBLIC
}
