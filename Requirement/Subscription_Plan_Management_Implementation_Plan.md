# Subscription Plan Management — Implementation Task Plan

Source: `Requirement/Ticketing_System_Subscription_Plan_Management_Final.docx`
Stack: Spring Boot (Java) backend, React frontend, MySQL/MariaDB.

## Status so far

Done in this pass, under `backend/src/main/java/com/konohahelpdesk/`:

- **Enums** (`model/`): `PlanType`, `PlanStatus`, `SubscriptionStatus`, `SlaPriority`
- **Entities** (`model/`): `Plan`, `Feature`, `PlanFeature`, `CompanySubscription`, `CompanySubscriptionFeatureOverride`, `CompanyLimitConfiguration`, `CompanySlaConfiguration`
- **Repositories** (`repository/`): one Spring Data JPA repository per entity above
- **SQL schema**: `backend/src/main/resources/db/subscription_plan_schema.sql` — tables, FKs, unique/check constraints, and seed data for the 8 baseline features and the Starter/Professional/Enterprise plans

Not verified by an actual Maven build — this machine only has Java 11 and no network access to fetch Maven, while the project targets Spring Boot 4 (Java 17+). Braces/structure were checked manually and look correct, but run `./mvnw compile` on a properly provisioned machine before trusting it fully.

## Phase 1 — Data layer (mostly done)

- [x] Enums, entities, repositories
- [x] SQL schema file
- [ ] Decide schema application strategy: either point `spring.jpa.hibernate.ddl-auto` at `update`/`validate` for dev, or wire in Flyway/Liquibase and convert `subscription_plan_schema.sql` into a versioned migration (recommended before this reaches a shared environment — there's no migration tool in the project yet)
- [ ] Run the seed data (or an equivalent `CommandLineRunner`) against a real dev database and confirm the Starter/Professional/Enterprise plans and 8 features come out as expected

## Phase 2 — DTOs

- [ ] `PlanRequest` / `PlanResponse` (create/update a plan, list plans)
- [ ] `FeatureResponse` (read-only feature catalog)
- [ ] `PlanFeatureRequest` (toggle a feature on a plan)
- [ ] `CompanySubscriptionRequest` — discriminated by `subscriptionType`:
  - `PREDEFINED`: `planId` only, everything else server-derived
  - `CUSTOM`: feature toggles + limits + SLA values inline
- [ ] `CompanySubscriptionResponse` — resolved view combining plan/override features, limits, and SLA so the frontend never has to merge three tables itself
- [ ] `CompanyLimitConfigurationRequest`
- [ ] `CompanySlaConfigurationRequest` (list of priority → response/resolution minutes)

## Phase 3 — Services

- [ ] `PlanService` — CRUD for predefined plans + their feature sets (Admin-only, per doc's "Plans" menu)
- [ ] `FeatureService` — read-only catalog listing (Admin's "Features" menu); features themselves are seeded, not user-created, per the doc
- [ ] `CompanySubscriptionService`:
  - `assignPredefinedPlan(companyId, planId)` — creates/replaces the company's subscription, copies nothing (resolution happens at read time via the plan's `plan_features`)
  - `assignCustomPlan(companyId, CompanySubscriptionRequest)` — creates/replaces the subscription as `CUSTOM`, writes `company_subscription_feature_overrides`, `company_limit_configurations`, `company_sla_configurations` in one transaction
  - `getResolvedSubscription(companyId)` — returns the merged view for the admin UI and for `hasFeature()`
  - Enforce "one active subscription per company" (the DB unique constraint on `company_id` backs this up, but the service should give a clean `DuplicateResourceException`-style error rather than surfacing a raw constraint violation)
- [ ] `FeaturePermissionService` (or a method on `CompanySubscriptionService`) exposing:
  ```java
  boolean hasFeature(Long companyId, String featureCode)
  ```
  Resolution order: for a `CUSTOM` subscription, check `company_subscription_feature_overrides`; for `PREDEFINED`, check the linked plan's `plan_features`. This is the single choke point every other module (Automation Rules, Knowledge Base, Reporting, etc.) calls before allowing access — per the doc's explicit "avoid hard-coded plan checks" rule.

## Phase 4 — Controllers (Admin-only, mirror `SecurityConfig` role checks used by `UserController`)

- [ ] `PlanController` — `GET/POST/PUT /api/admin/plans`, `PUT /api/admin/plans/{id}/features`
- [ ] `FeatureController` — `GET /api/admin/features`
- [ ] `CompanySubscriptionController`:
  - `GET /api/admin/companies/{companyId}/subscription`
  - `PUT /api/admin/companies/{companyId}/subscription` (body carries `subscriptionType` and the relevant payload from Phase 2)
- [ ] Wire the company-creation endpoint (existing or new) to require a subscription selection per doc section 2 — creating a company without a plan/custom config should be rejected

## Phase 5 — Cross-cutting enforcement

- [ ] A small interceptor/annotation (e.g. `@RequiresFeature("AUTOMATION_RULES")`) or an explicit `hasFeature()` check at the top of each feature-gated controller/service method, so ticket automation, knowledge base, reporting, and audit log endpoints all respect the company's plan
- [ ] Ticket creation and user invitation paths should check `monthly_ticket_limit` / `max_users` from `company_limit_configurations` before allowing new records, returning a clear "limit reached" error
- [ ] SLA due-date calculation (wherever tickets get a due date) should read from `company_sla_configurations` by priority instead of a hard-coded value

## Phase 6 — Frontend (`frontend/src`)

- [ ] Admin "Subscription Management" section: Plans list/editor, Features list (read-only), Company Subscriptions
- [ ] Company creation screen: radio choice between "Select Existing Plan" (dropdown, fields become read-only/greyed once picked) and "Custom Plan" (feature toggles, limit inputs, SLA inputs all editable) — matches doc section 7 layout
- [ ] Company detail/edit screen: show the resolved subscription (plan or custom config) with the same locked/editable behavior

## Phase 7 — Testing & verification

- [ ] Unit tests for `CompanySubscriptionService` (predefined vs custom assignment, re-assignment/replacement, one-subscription-per-company enforcement)
- [ ] Unit tests for `hasFeature()` covering both predefined and custom paths, including a feature with no override row (should fall back correctly rather than throwing)
- [ ] Integration test: create a company with a Custom Plan disabling Knowledge Base, then confirm a Knowledge Base endpoint returns 403/appropriate rejection
- [ ] Manual pass through the Admin UI once Phase 6 lands: predefined-plan company, custom-plan company, and switching a company from one to the other

## Open questions worth confirming with whoever owns the requirement doc

- Can a company switch from Predefined to Custom (or vice versa) after creation, and if so, what happens to existing overrides/limits — deleted, or kept as a starting point?
- Storage limit units aren't specified in the doc — I used megabytes (`storage_limit_mb`) as a placeholder; confirm the intended unit.
- SLA priorities: the doc's example only shows Critical; I've modeled `CRITICAL`, `HIGH`, `NORMAL` based on section 4's "Critical / High / Normal priority response time" list — confirm this is the full set (no `LOW`?).
