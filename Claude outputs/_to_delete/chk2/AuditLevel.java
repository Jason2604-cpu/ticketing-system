package com.konohahelpdesk.model.audit;

/** Severity of a recorded event — the two levels {@code AuditLogger} already emits. */
public enum AuditLevel {
    /** Something happened and it was allowed. */
    INFO,
    /** Something was refused, failed, or looks like abuse — a failed login, a locked account. */
    WARN
}
