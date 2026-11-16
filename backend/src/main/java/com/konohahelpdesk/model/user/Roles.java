package com.konohahelpdesk.model.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Roles {
        ENGINEER("Engineer"),
        ADMIN("Admin"),
        CUSTOMER_ADMIN("Customer Admin"),
        CUSTOMER("Customer"),
        /**
         * An employee with the Default role: can sign in, but no module is open to them until an
         * admin ticks Engineer / Admin, or grants a custom role (Role Management) whose privileges
         * open specific admin pages. Staff side (EMPxxxxx code), never linked to a company.
         */
        STAFF("Default");

        private final String displayName;

        Roles(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        /** Employees of the service provider: Admin, Engineer and Default (EMPxxxxx codes). */
        public boolean isStaffSide() {
            return this == ADMIN || this == ENGINEER || this == STAFF;
        }

        /**
         * Admin and Engineer: the roles that work tickets (every company, or an engineer's assigned
         * ones). The single definition of "support staff" — use this instead of comparing to both.
         */
        public boolean worksTickets() {
            return this == ADMIN || this == ENGINEER;
        }

        /**
         * Who can hold custom (Role Management) roles: Engineer and Default employees. An Admin
         * already has every privilege, and customers never get any — so for everyone else the
         * per-request privilege lookup is skipped entirely (JwtCookieFilter).
         */
        public boolean canHoldCustomRoles() {
            return this == ENGINEER || this == STAFF;
        }

        /** The customer's people: Company Admin and Customer (CUSxxxxxx codes, always linked to a company). */
        public boolean isCustomerSide() {
            return this == CUSTOMER_ADMIN || this == CUSTOMER;
        }

        /**
         * Always serialize as the constant name ("ADMIN"), never the display name.
         * Jackson 3 defaults to toString() for enums, which would otherwise emit "Admin".
         */
        @JsonValue
        public String toJson() {
            return name();
        }

        @JsonCreator
        public static Roles fromJson(String value) {
            for (Roles r : values()) {
                if (r.name().equalsIgnoreCase(value) || r.displayName.equalsIgnoreCase(value)) return r;
            }
            throw new IllegalArgumentException("Unknown role: " + value);
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
