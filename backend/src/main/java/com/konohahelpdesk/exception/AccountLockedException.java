package com.konohahelpdesk.exception;

/** Too many failed logins; the account is temporarily locked (HTTP 423). */
public class AccountLockedException extends RuntimeException {
    public AccountLockedException(String message) {
        super(message);
    }
}
