package com.konohahelpdesk.model.template;

/**
 * What a template is for. One table serves both because they share everything that matters —
 * a name, a body, who may see it, and which company it belongs to — and differ only in where
 * the body is dropped.
 */
public enum TemplateKind {
    /** Prefills the new-ticket form: title, description, steps, category, priority. */
    TICKET,
    /** A canned reply dropped into the comment box on a ticket. Staff only, always. */
    REPLY
}
