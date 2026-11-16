package com.konohahelpdesk.service.kb;

import com.konohahelpdesk.model.kb.KbVisibility;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * The Knowledge Base access matrix (plan §4 and §5.3) as pure functions — no Spring, no database —
 * so every cell is covered by {@code KbRulesTest}. The reader SQL in {@code KbArticleRepository}
 * applies the same read rule; keep the two in step.
 *
 * Publishing is managers only, for every article. Engineers write and ask; customers read.
 */
public final class KbRules {

    private KbRules() {}

    /** May this caller read a published version with this visibility and company snapshot? */
    public static boolean canReadPublished(KbViewer v, KbVisibility visibility, Collection<Long> companyIds) {
        if (!v.mayRead()) return false;
        if (v.publicOnly() && visibility != KbVisibility.PUBLIC) return false;
        List<Long> scope = v.readerScope();
        if (scope == null || companyIds == null || companyIds.isEmpty()) return true;
        return companyIds.stream().anyMatch(scope::contains);
    }

    /**
     * May this caller open and edit a working copy pinned to these companies?
     * An engineer only when every company on it is inside their scope: an article that names a
     * company they aren't assigned to is not theirs to change (and would show them that company).
     */
    public static boolean canEdit(KbViewer v, Collection<Long> workingCompanyIds) {
        if (v.isManager()) return true;
        if (!v.isEngineer()) return false;
        List<Long> scope = v.scope();
        if (scope == null || workingCompanyIds == null || workingCompanyIds.isEmpty()) return true;
        return scope.containsAll(workingCompanyIds);
    }

    /** Publish, decline a request, archive / unarchive. */
    public static boolean canPublish(KbViewer v) {
        return v.isManager();
    }

    /** Engineers ask a manager to publish what they can edit. Managers just publish. */
    public static boolean canRequestPublish(KbViewer v, Collection<Long> workingCompanyIds) {
        return v.isEngineer() && canEdit(v, workingCompanyIds);
    }

    /**
     * Only never-published articles can be deleted (published ones are archived so links in old
     * tickets keep resolving). A manager may delete any such draft; an engineer only their own.
     */
    public static boolean canDelete(KbViewer v, boolean everPublished, Long createdById, Collection<Long> workingCompanyIds) {
        if (everPublished) return false;
        if (v.isManager()) return true;
        return v.isEngineer() && canEdit(v, workingCompanyIds) && Objects.equals(createdById, v.userId());
    }

    public static boolean canManageCategories(KbViewer v) {
        return v.isManager();
    }

    /** Managers reviewing articles don't inflate the view counter. */
    public static boolean countsAsView(KbViewer v) {
        return v.mayRead() && !v.isManager();
    }
}
