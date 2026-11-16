package com.konohahelpdesk.model.kb;

/** Lifecycle of an article. Readers only ever see PUBLISHED ones (their published version). */
public enum KbStatus {
    /** Never published. Visible to writers only. */
    DRAFT,
    /** Has a published version that readers see. The working copy may hold unpublished edits. */
    PUBLISHED,
    /** Hidden from readers and suggestions; versions kept so old links can be restored. */
    ARCHIVED
}
