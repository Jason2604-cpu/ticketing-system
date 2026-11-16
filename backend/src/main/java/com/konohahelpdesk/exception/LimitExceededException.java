package com.konohahelpdesk.exception;

/** A subscription quota (users, tickets, storage) has been reached (HTTP 403). */
public class LimitExceededException extends RuntimeException {
    public LimitExceededException(String message) {
        super(message);
    }
}
