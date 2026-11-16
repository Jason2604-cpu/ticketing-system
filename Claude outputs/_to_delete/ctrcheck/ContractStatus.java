package com.konohahelpdesk.model.contract;

/**
 * The stored lifecycle of a contract. Deliberately only the two states a human sets —
 * "expired" and "expiring soon" are derived from {@code endDate} at read time
 * (see {@code ServiceContract.derivedState}) so a contract can never sit in the database
 * claiming to be active a year after it ran out just because no sweep touched it.
 */
public enum ContractStatus {
    ACTIVE,
    CANCELLED
}
