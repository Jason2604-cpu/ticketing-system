package com.konohahelpdesk.model.automation;

public enum RunStatus {
    /** Handed to the mail dispatcher. Delivery itself is asynchronous, so this is "queued", not "received". */
    SENT,
    /** Deliberately not sent (no one to send to, company didn't exist yet). Not retried. */
    SKIPPED,
    /** Building or queueing the email threw. Retried by the next sweep, up to the configured attempts. */
    FAILED
}
