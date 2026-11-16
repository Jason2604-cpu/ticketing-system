package com.konohahelpdesk.service.kb;

import java.util.List;

/**
 * Everything the Knowledge Base rules need to know about the caller, resolved once per request by
 * {@link KbAccess#viewer}. A plain value so {@link KbRules} can be unit-tested without Spring.
 *
 * @param scope     ENGINEER only: the companies they are assigned to, or {@code null} = unrestricted
 *                  (the CompanyScope convention: an engineer assigned to nothing sees everything).
 * @param companyId CUSTOMER only: their own company.
 * @param featureEnabled CUSTOMER only: their plan includes KNOWLEDGE_BASE.
 */
public record KbViewer(Long userId, Kind kind, List<Long> scope, Long companyId, boolean featureEnabled) {

    public enum Kind {
        /** Admin, or anyone holding MANAGE_KNOWLEDGE_BASE: reads and edits everything, the only one who publishes. */
        MANAGER,
        /** Writes drafts within their company scope and asks a manager to publish them. */
        ENGINEER,
        /** Reads published Public articles for their own company, if their plan has the feature. */
        CUSTOMER,
        /** No Knowledge Base access (a Default employee without the privilege). */
        NONE
    }

    public static KbViewer manager(Long userId) { return new KbViewer(userId, Kind.MANAGER, null, null, true); }
    public static KbViewer engineer(Long userId, List<Long> scope) { return new KbViewer(userId, Kind.ENGINEER, scope, null, true); }
    public static KbViewer customer(Long userId, Long companyId, boolean featureEnabled) {
        return new KbViewer(userId, Kind.CUSTOMER, null, companyId, featureEnabled && companyId != null);
    }
    public static KbViewer none(Long userId) { return new KbViewer(userId, Kind.NONE, null, null, false); }

    public boolean isManager() { return kind == Kind.MANAGER; }
    public boolean isEngineer() { return kind == Kind.ENGINEER; }
    public boolean isCustomer() { return kind == Kind.CUSTOMER; }
    /** Staff who may write: managers and engineers. */
    public boolean isWriter() { return kind == Kind.MANAGER || kind == Kind.ENGINEER; }

    /**
     * Companies whose pinned articles this caller may read, or {@code null} = all of them.
     * Global articles are readable regardless (subject to {@link #publicOnly()}).
     */
    public List<Long> readerScope() {
        return switch (kind) {
            case MANAGER -> null;
            case ENGINEER -> scope;
            case CUSTOMER -> companyId == null ? List.of() : List.of(companyId);
            case NONE -> List.of();
        };
    }

    /** Customers only ever read Public articles. */
    public boolean publicOnly() { return kind == Kind.CUSTOMER; }

    /** Whether this caller may read published articles at all. */
    public boolean mayRead() {
        return switch (kind) {
            case MANAGER, ENGINEER -> true;
            case CUSTOMER -> featureEnabled;
            case NONE -> false;
        };
    }
}
