package com.konohahelpdesk.model.contract;

/** What a contract actually is right now — {@link ContractStatus} resolved against today's date. */
public enum ContractState {
    ACTIVE,
    EXPIRING_SOON,
    EXPIRED,
    CANCELLED
}
