package com.konohahelpdesk.service.kb;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.konohahelpdesk.model.kb.KbVisibility.INTERNAL;
import static com.konohahelpdesk.model.kb.KbVisibility.PUBLIC;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Every cell of the plan's §4 matrix and the §5.3 publish rule. A leak here is one customer
 * reading another customer's runbook, so the cases are spelled out rather than generated.
 */
class KbRulesTest {

    private static final long A = 1, B = 2, C = 3;
    private static final Set<Long> GLOBAL = Set.of();

    private final KbViewer manager = KbViewer.manager(10L);
    private final KbViewer engineerAB = KbViewer.engineer(20L, List.of(A, B));
    private final KbViewer engineerAny = KbViewer.engineer(21L, null);       // no assignments = unrestricted
    private final KbViewer customerA = KbViewer.customer(30L, A, true);
    private final KbViewer customerNoPlan = KbViewer.customer(31L, A, false);
    private final KbViewer customerNoCompany = KbViewer.customer(32L, null, true);
    private final KbViewer defaultEmployee = KbViewer.none(40L);

    @Nested
    class ReadingPublished {

        @Test
        void managerReadsEverything() {
            assertTrue(KbRules.canReadPublished(manager, INTERNAL, GLOBAL));
            assertTrue(KbRules.canReadPublished(manager, INTERNAL, Set.of(C)));
            assertTrue(KbRules.canReadPublished(manager, PUBLIC, Set.of(C)));
        }

        @Test
        void engineerReadsGlobalAndOwnCompaniesOfEitherVisibility() {
            assertTrue(KbRules.canReadPublished(engineerAB, INTERNAL, GLOBAL));
            assertTrue(KbRules.canReadPublished(engineerAB, PUBLIC, GLOBAL));
            assertTrue(KbRules.canReadPublished(engineerAB, INTERNAL, Set.of(A)));
            assertTrue(KbRules.canReadPublished(engineerAB, PUBLIC, Set.of(B, C)));    // overlaps
            assertFalse(KbRules.canReadPublished(engineerAB, INTERNAL, Set.of(C)));
            assertFalse(KbRules.canReadPublished(engineerAB, PUBLIC, Set.of(C)));
        }

        @Test
        void unassignedEngineerIsUnrestricted() {
            assertTrue(KbRules.canReadPublished(engineerAny, INTERNAL, Set.of(C)));
        }

        @Test
        void customerReadsOnlyPublicGlobalOrOwnCompany() {
            assertTrue(KbRules.canReadPublished(customerA, PUBLIC, GLOBAL));
            assertTrue(KbRules.canReadPublished(customerA, PUBLIC, Set.of(A)));
            assertTrue(KbRules.canReadPublished(customerA, PUBLIC, Set.of(A, C)));
            assertFalse(KbRules.canReadPublished(customerA, PUBLIC, Set.of(B)));
            assertFalse(KbRules.canReadPublished(customerA, INTERNAL, GLOBAL));
            assertFalse(KbRules.canReadPublished(customerA, INTERNAL, Set.of(A)));
        }

        @Test
        void customerWithoutThePlanFeatureReadsNothing() {
            assertFalse(KbRules.canReadPublished(customerNoPlan, PUBLIC, GLOBAL));
            assertFalse(KbRules.canReadPublished(customerNoPlan, PUBLIC, Set.of(A)));
            assertFalse(customerNoPlan.mayRead());
        }

        @Test
        void customerWithoutACompanyReadsNothing() {
            assertFalse(customerNoCompany.featureEnabled());
            assertFalse(KbRules.canReadPublished(customerNoCompany, PUBLIC, GLOBAL));
        }

        @Test
        void defaultEmployeeReadsNothing() {
            assertFalse(KbRules.canReadPublished(defaultEmployee, PUBLIC, GLOBAL));
            assertFalse(KbRules.canReadPublished(defaultEmployee, INTERNAL, GLOBAL));
        }
    }

    @Nested
    class Editing {

        @Test
        void managerEditsAnything() {
            assertTrue(KbRules.canEdit(manager, GLOBAL));
            assertTrue(KbRules.canEdit(manager, Set.of(C)));
        }

        @Test
        void engineerEditsGlobalAndArticlesEntirelyInsideTheirScope() {
            assertTrue(KbRules.canEdit(engineerAB, GLOBAL));
            assertTrue(KbRules.canEdit(engineerAB, Set.of(A)));
            assertTrue(KbRules.canEdit(engineerAB, Set.of(A, B)));
            assertFalse(KbRules.canEdit(engineerAB, Set.of(A, C)));   // names a company they aren't assigned to
            assertFalse(KbRules.canEdit(engineerAB, Set.of(C)));
            assertTrue(KbRules.canEdit(engineerAny, Set.of(C)));
        }

        @Test
        void customersAndDefaultEmployeesNeverEdit() {
            assertFalse(KbRules.canEdit(customerA, GLOBAL));
            assertFalse(KbRules.canEdit(customerA, Set.of(A)));
            assertFalse(KbRules.canEdit(defaultEmployee, GLOBAL));
        }
    }

    @Nested
    class Publishing {

        @Test
        void onlyManagersPublish() {
            assertTrue(KbRules.canPublish(manager));
            assertFalse(KbRules.canPublish(engineerAB));
            assertFalse(KbRules.canPublish(engineerAny));
            assertFalse(KbRules.canPublish(customerA));
            assertFalse(KbRules.canPublish(defaultEmployee));
        }

        @Test
        void engineersRequestPublishForWhatTheyCanEdit() {
            assertTrue(KbRules.canRequestPublish(engineerAB, GLOBAL));
            assertTrue(KbRules.canRequestPublish(engineerAB, Set.of(B)));
            assertFalse(KbRules.canRequestPublish(engineerAB, Set.of(C)));
        }

        @Test
        void managersAndCustomersDontRequest() {
            assertFalse(KbRules.canRequestPublish(manager, GLOBAL));   // they just publish
            assertFalse(KbRules.canRequestPublish(customerA, GLOBAL));
        }

        @Test
        void onlyManagersManageCategories() {
            assertTrue(KbRules.canManageCategories(manager));
            assertFalse(KbRules.canManageCategories(engineerAny));
            assertFalse(KbRules.canManageCategories(customerA));
        }
    }

    @Nested
    class Deleting {

        @Test
        void publishedArticlesAreNeverDeleted() {
            assertFalse(KbRules.canDelete(manager, true, 10L, GLOBAL));
            assertFalse(KbRules.canDelete(engineerAB, true, 20L, GLOBAL));
        }

        @Test
        void managerDeletesAnyDraft() {
            assertTrue(KbRules.canDelete(manager, false, 99L, Set.of(C)));
        }

        @Test
        void engineerDeletesOnlyTheirOwnDraftsInScope() {
            assertTrue(KbRules.canDelete(engineerAB, false, 20L, Set.of(A)));
            assertFalse(KbRules.canDelete(engineerAB, false, 99L, Set.of(A)));   // someone else's
            assertFalse(KbRules.canDelete(engineerAB, false, 20L, Set.of(C)));   // out of scope
        }

        @Test
        void customersNeverDelete() {
            assertFalse(KbRules.canDelete(customerA, false, 30L, GLOBAL));
        }
    }

    @Test
    void managerViewsDontCount() {
        assertFalse(KbRules.countsAsView(manager));
        assertTrue(KbRules.countsAsView(engineerAB));
        assertTrue(KbRules.countsAsView(customerA));
        assertFalse(KbRules.countsAsView(customerNoPlan));
    }

    @Test
    void sqlScopeMatchesTheJavaRule() {
        assertEquals(new KbReadService.SqlScope(true, "{}", false), KbReadService.SqlScope.of(manager));
        assertEquals(new KbReadService.SqlScope(false, "{1,2}", false), KbReadService.SqlScope.of(engineerAB));
        assertEquals(new KbReadService.SqlScope(true, "{}", false), KbReadService.SqlScope.of(engineerAny));
        assertEquals(new KbReadService.SqlScope(false, "{1}", true), KbReadService.SqlScope.of(customerA));
        assertEquals(new KbReadService.SqlScope(false, "{}", false), KbReadService.SqlScope.of(defaultEmployee));
    }
}
