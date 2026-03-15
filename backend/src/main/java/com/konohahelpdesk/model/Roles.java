package com.konohahelpdesk.model;

public enum Roles {
        ENGINEER("Engineer"),
        ADMIN("Admin"),
        CUSTOMER_ADMIN("Customer Admin"),
        CUSTOMER("Customer");

        private final String displayName;
        
        Roles(String displayName) {
            this.displayName = displayName;
        }     

        @Override
        public String toString() {
            return displayName;
        }
    }
