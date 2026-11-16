# Asset Management — Implementation Plan

Status: **phases 1–3 written 2026-09-18.** Frontend verified (tsc + oxlint + vite build all clean); backend still needs `mvnw.cmd -q compile`. See §14 Build log.
Written 2026-09-10 · Revised 2026-09-10 (navigation flow + asset assignment) · Revised again
2026-09-10 (one-way contract coverage + CSV import + the customer-filled template and list toolbar).

Feature code `ASSET_MANAGEMENT` already exists in the catalog (`app-config.properties`,
`types/index.ts`) with no code behind it. This plan fills it in.

---

## 1. Scope and the decisions behind it

**An asset is a serviceable thing at a customer site**, not a finance record. It exists to answer
three questions the helpdesk already asks and currently can't:

- *What is this ticket actually about?* — today the model number lives in free-text descriptions.
- *What does this contract cover?* — today `ServiceContract.coveredAssets` is a `text` column.
- *What is out of warranty / about to be?* — today nothing tracks it.

Deliberately **out of scope**: purchase cost, supplier, depreciation, disposal workflow, transfer
history, per-type custom fields. That is an ITAM product, not a helpdesk module. If it's wanted
later, `Asset` has room to grow; nothing here blocks it.

| Decision | Choice |
|---|---|
| Depth | Service-focused inventory |
| Who writes | Admin / Engineer / `MANAGE_ASSETS` privilege |
| Who reads | Above, plus customer users for **their own company only**, gated on the `ASSET_MANAGEMENT` plan feature |
| Navigation | **Company-first** — pick a company, then work inside it (§2) |
| Assignment | System user / guest name / shared — a three-way choice (§4) |
| Ticket link | Optional, **one asset per ticket** |
| Contract link | Edited **from the contract only**; the asset side cross-links, never writes (§8.2) |
| Bulk entry | **CSV import**, preview-then-commit, company-scoped; the template is built to be filled in by the *customer* (§9) |
| Warranty reminders | **Per-asset opt-in toggle**, emails the customer's Customer Admins |
| Plan limit | None (no `maxAssets`) — see §7 |

---

## 2. The flow

```
  Sidebar → Assets
        │
        ▼
  /admin/assets                  COMPANY PICKER
        │                        engineer → companies assigned to them
        │                        admin    → every company
        │                        each row: asset count · expiring soon · expired
        │
        ▼  click a company
  /admin/assets/company/:companyId    ASSET LIST for that company
        │                            filters: type · status · warranty state ·
        │                                     warranty-end range · assigned-to · contract
        │                            [ + Add Asset ] | [ Import CSV ] [ Export CSV ]
        │                                              [ Download template ]
        │                            ← assets are created HERE, so companyId is never ambiguous
        ▼  click an asset
  /admin/assets/:id                  ASSET DETAIL
                                     identity · assignment · warranty + reminder toggle
                                     covering contracts (read-only links) · documents ·
                                     ticket history

  ContractDetailPage → [ View assets ] ──────┐
                                             ▼
  /admin/assets/company/:companyId?contractId=:id   same list, pre-filtered
```

Three things this buys, which a flat "all assets" list does not:

1. **The company is chosen before anything is created.** An asset with the wrong company attached is
   the one mistake in this module that's genuinely annoying to unpick — it breaks the contract link,
   the ticket link and the customer's own view all at once. Making the company a place you *are*
   rather than a dropdown you *fill in* removes the mistake instead of validating it.
2. **Engineer scoping falls out for free.** `CompanyService.list(actor)` already filters to an
   engineer's assigned companies (via `EngineerAssignmentService.companiesForEngineer`), which is
   exactly the rule wanted here.
3. **The picker is a status board.** Counts of expiring-soon and expired warranties per company mean
   the landing page answers "who do I need to call about a renewal" before anyone clicks anything.

**Shortcuts:**
- An engineer assigned exactly **one** company gets redirected straight past the picker.
- **Customers never see the picker** — `/assets` goes directly to their own company's list, read-only.

**Route-ranking note:** `/admin/assets/company/:companyId` and `/admin/assets/:id` coexist safely —
react-router 7 ranks a static segment above a dynamic one, so `company` never gets read as an id.
Keep the literal segment; don't "simplify" it to `/admin/assets/:companyId/...`.

---

## 3. Domain model — `model/asset/`

### `Asset`  → table `assets`

| Field | Column | Notes |
|---|---|---|
| `id` | `id` | IDENTITY, per convention |
| `company` | `company_id` | `@ManyToOne(optional=false, LAZY)`. An asset with no owner company isn't one. Indexed. |
| `assetTag` | `asset_tag` | Your reference, e.g. `MTS-SRV-0042`. Optional, unique **per company** (not globally). |
| `name` | `name` | `length 150, nullable=false`. "KATMB DC Core Switch". |
| `type` | `type` | `AssetType` enum, STRING, `length 30` |
| `make` / `model` | `make`, `model` | `length 80` each, optional |
| `serialNumber` | `serial_number` | `length 100`, optional, indexed. Warn (don't reject) on duplicate within a company. |
| `location` | `location` | `length 150`. "DC Rack B3", "HQ Level 4 Server Room". Free text on purpose — a Site entity is a second module. |
| `status` | `status` | `AssetStatus` enum, STRING, default `ACTIVE` |
| `assignedTo` | `assigned_to` | `AssetAssignment` enum — `SYSTEM_USER` / `GUEST` / `SHARED`. Default `SHARED`. See §4. |
| `owner` | `owner_id` | `@ManyToOne(LAZY)` → `User`, nullable. Set **only** when `assignedTo = SYSTEM_USER`. |
| `guestName` | `guest_name` | `length 120`, nullable. Set **only** when `assignedTo = GUEST`. |
| `guestEmail` | `guest_email` | `length 190`, nullable, optional even for guests. Contact detail only — never a login, never becomes a `User`, never mailed by the sweep. |
| `purchaseDate` | `purchase_date` | `LocalDate`, optional |
| `warrantyStart` / `warrantyEnd` | `warranty_start`, `warranty_end` | `LocalDate`, optional. Indexed on `warranty_end` for the sweep and the date-range filter. |
| `notes` | `notes` | `text` |
| `warrantyReminderEnabled` | `warranty_reminder_enabled` | `boolean not null default false` — the toggle |
| `warrantyReminderDays` | `warranty_reminder_days` | `Integer`, default 30. Null while the toggle is off. |
| `warrantyReminderSentFor` | `warranty_reminder_sent_for` | `LocalDate` — idempotence marker, **stamped with the `warrantyEnd` it fired for**, exactly like `ServiceContract.renewalReminderSentFor`. Extending the warranty re-arms it; a restart or a second sweep the same day does not resend. |
| `createdAt` / `updatedAt` | | `@PrePersist` / `@PreUpdate`, per convention |

Put a `columnDefinition` default on `assigned_to`, `warranty_reminder_enabled` and `status` so
`ddl-auto=update` can add them to a non-empty table later — the same trick `ContractType` needed.

### Enums

```
AssetType      SERVER, WORKSTATION, LAPTOP, NETWORK_DEVICE, FIREWALL, STORAGE,
               PRINTER, UPS, SOFTWARE_LICENSE, PERIPHERAL, OTHER
AssetStatus    ACTIVE, IN_REPAIR, IN_STORAGE, RETIRED, DISPOSED        (stored)
AssetAssignment SYSTEM_USER, GUEST, SHARED                             (stored)
WarrantyState  IN_WARRANTY, EXPIRING_SOON, EXPIRED, NONE               (DERIVED — never stored)
```

`WarrantyState` follows the `ContractState` precedent exactly: computed at read time by
`Asset.warrantyState(LocalDate today, int expiringSoonDays)`, `NONE` when `warrantyEnd` is null.
Nothing time-dependent gets persisted — a stored "expired" flag is wrong the morning after it's
written.

`AssetStatus` and `WarrantyState` are orthogonal on purpose: a retired box can still be in warranty,
and an active one is usually the one that just went out of it.

> **Enum gotcha (Boot 4 / Jackson 3):** any enum that overrides `toString()` needs `@JsonValue` on
> `name()`. Give these enums a `label` field and a getter — do **not** override `toString()`.

### Links

- **Ticket → Asset**: new `@ManyToOne(LAZY) Asset asset` on `Ticket`, column `asset_id`, nullable,
  indexed. One asset per ticket. Validated in `TicketService` to belong to the ticket's company.
- **Contract ↔ Asset**: `@ManyToMany` on `ServiceContract` with join table `contract_assets
  (contract_id, asset_id)`. Coverage carries no per-row state, so unlike `ContractVisit` it does
  **not** need its own entity. Owned by the contract side (§8.2). **Keep the existing `coveredAssets`
  text column** — `ddl-auto=update` can't migrate it and old contracts still hold data there. Show it
  read-only as "Legacy notes" when non-blank, and stop writing to it.

---

## 4. Who the asset belongs to

A laptop belongs to a person. A firewall belongs to nobody in particular. A printer belongs to a
floor. One nullable `owner_id` can't express that difference — a null would mean both "shared by
design" and "we haven't filled this in yet", and those are different answers to the same report.

So the assignment is an explicit **three-way choice**, always set:

| Mode | UI | What's stored |
|---|---|---|
| `SYSTEM_USER` | Pick from the company's users | `owner` FK set, `guestName`/`guestEmail` null |
| `GUEST` | Toggle "person isn't in the system" → type a name (email optional) | `guestName` set, `owner` null |
| `SHARED` | **The default.** "Shared / infrastructure" | both null |

`SHARED` is the form's initial value, so a firewall or a printer is one click — leave it alone. The
constant is named `SHARED` rather than `DEFAULT` because in six months a column full of `DEFAULT`
tells you nothing; "shared" is a positive statement about the asset.

### Invariants — enforced in `AssetService.apply()`

A half-set trio ("assigned to a guest named `null`") is the failure mode here, so the service
**clears the fields the current mode doesn't use** on every save:

- `SYSTEM_USER` → `ownerId` required; the user must belong to **this asset's company** and not be
  deleted; `guestName`/`guestEmail` forced to null.
- `GUEST` → `guestName` required, non-blank; `owner` forced to null.
- `SHARED` → all three forced to null.

This is the same defensive move `ContractService.apply()` makes when a contract switches to
ticket-based: clear what the new mode can't use, so switching modes twice can't leave a stale value
armed behind the UI.

### Reading it back

- `Asset.assignedToLabel()` → owner's full name / `guestName` / `"Shared"`. `AssetResponse` carries
  the precomputed label **as well as** the raw fields, so the table renders one string instead of
  branching three ways in JSX.
- `AssetResponse.ownerInactive` — true when the owner FK points at a deactivated or soft-deleted
  user. **The FK is deliberately not nulled** when a user is deactivated: knowing who had the laptop
  is the whole point, and losing it at offboarding is exactly backwards. The list shows a muted badge
  and the filter can find them, which is the actual offboarding workflow — *"what hardware do we need
  back from this person?"*
- Promoting a guest to a real user is just an edit: flip to `SYSTEM_USER` and pick them. If
  `guestEmail` matches a user in that company, the form can offer it — polish, not phase 1.

---

## 5. Backend surface

### `repository/asset/AssetRepository`

`JpaRepository<Asset, Long>, JpaSpecificationExecutor<Asset>` — Specifications for list filtering,
**not** `:param IS NULL` JPQL (Postgres untyped-null gotcha; `TicketService` already does this).

Named queries needed:
- `List<Asset> findByCompanyIdOrderByNameAsc(Long companyId)`
- `boolean existsByCompanyIdAndSerialNumberIgnoreCase(...)` — duplicate warning
- `boolean existsByCompanyIdAndAssetTagIgnoreCase(...)` — uniqueness check
- **`List<Object[]> countsByCompany(Collection<Long> companyIds, LocalDate soonCutoff, LocalDate today)`**
  — one grouped query returning `(companyId, total, expiringSoon, expired)` for the picker.
- `List<Asset> findWarrantyDue(LocalDate horizonEnd)` — `warrantyReminderEnabled = true`,
  `warrantyEnd is not null`, `status not in (RETIRED, DISPOSED)`,
  `(warrantyReminderSentFor is null or warrantyReminderSentFor <> warrantyEnd)`

> **PERF — the company picker must not become an N+1.** `CompanyService.list()` already calls
> `findResolved` per company, and the app polls every 15s; do **not** stack a per-company asset count
> on top of that. `countsByCompany` takes the whole id list and returns one result set, which the
> service zips into the company rows.

> **REFACTOR worth doing in phase 1:** "which companies can this actor see" is now written three
> times — `CompanyService.list()` (engineer scope), `ReportService.visibleCompanies()`, and now
> assets. Extract it to `service/company/CompanyScope.visibleCompanyIds(User actor)` and have all
> three call it. Three copies of a permission rule is two too many.

### `service/asset/AssetService`

- `companiesWithCounts(actor)` — the picker payload.
- `list(actor, companyId, filters…)` — Specification-built. **`companyId` is required** for a
  customer and for the normal staff path; the unscoped variant exists only for the CSV export.
- `get`, `create`, `update`, `delete` — delete is a hard delete but **refuses when the asset is
  linked to any ticket or contract** (`BusinessRuleException`); tell the user to set status `RETIRED`
  instead. Losing the asset off a historical ticket is worse than a stale row.
- `apply(asset, request, actor)` — shared create/update mapper; owns the §4 invariants.
- `attach(id, file, actor)` / `removeAttachment(...)` — `MediaKind.ASSET_ATTACHMENT`, routed through
  `MediaStorageService` so `assertStorageAvailable` still applies.
- `canRead(actor, assetId)` / `requireWrite(actor, companyId)` — the real enforcement, mirroring
  `TicketService.canAccess`. Customer users: own company only, **and**
  `subscriptions.requireFeature(companyId, "ASSET_MANAGEMENT")`. Engineers: assigned companies only
  (via `CompanyScope`). Admin: unscoped.

### Filters on the list

Two kinds, and the distinction matters:

| Filter | Where it runs |
|---|---|
| search `q` (name, tag, serial, model), `type`, `status`, `assignedTo`, `ownerId`, **`contractId`**, **warranty-end date range** | **Specification** — all column- or join-backed |
| `warrantyState` (in warranty / expiring soon / expired / none) | **In memory, after the query** — it's derived, there's no column to filter on |

A warranty *date range* is a column and belongs in SQL; a warranty *state* is arithmetic against
today and does not. `contractId` is an exists-subquery on `contract_assets`. Reuse the existing
`DateRangePicker` for the range.

> **Filter state lives in the URL query string** (`useSearchParams`), not component state. Three
> reasons: the contract deep-link in §8.2 needs it, the browser back button then works, and a
> filtered view becomes something you can paste to a colleague. This is a design constraint on
> `AssetsPage`, not a detail — retrofitting it later means rewriting the page.

### `controller/asset/AssetController` → `/api/assets`

Shaped like `TicketController`, **not** like `ContractController`: no class-level `@PreAuthorize`,
because customers legitimately read here. `SecurityConfig` already denies `/api/**` to anyone without
a role.

| Method | Path | Guard |
|---|---|---|
| GET | `/api/assets/companies` | none at method level — service scopes |
| GET | `/api/assets?companyId=…&contractId=…&…` | none at method level — service scopes + feature-gates |
| GET | `/api/assets/{id}` | same |
| GET | `/api/assets/export.csv` | same |
| GET | `/api/assets/import/template.csv` | write guard (below) — it's a staff tool |
| POST | `/api/assets` | `@PreAuthorize("hasAnyRole('ADMIN','ENGINEER') or hasAuthority('PRIV_MANAGE_ASSETS')")` |
| PUT | `/api/assets/{id}` | same |
| DELETE | `/api/assets/{id}` | same |
| POST | `/api/assets/import/preview` | same |
| POST | `/api/assets/import` | same |
| POST | `/api/assets/{id}/documents` | same |
| DELETE | `/api/assets/{id}/documents/{mediaId}` | same |

Every write method carries the annotation individually — the class-level shortcut isn't available
here, so the checklist matters. Put a comment at the top of the class saying exactly that.

### DTOs — `dto/asset/`

- `AssetRequest` — bean-validated; carries `assignedTo` + `ownerId` + `guestName`/`guestEmail`.
  **No `contractIds`** — coverage isn't writable from here (§8.2).
- `AssetResponse` — derived `warrantyState`, `daysToWarrantyEnd`, `assignedToLabel`, `ownerInactive`,
  company summary, owner summary, attachments, covering contracts (read-only), `ticketCount`.
- `AssetSummary` — id / tag / name / type / serial, the light shape embedded in `TicketResponse` and
  `ContractResponse` so those payloads don't drag whole assets along.
- `AssetCompanyRow` — companyId, name, logoUrl, assetCount, expiringSoonCount, expiredCount.
- `AssetImportPreview` / `AssetImportRow` / `AssetImportResult` — see §9.

---

## 6. Media

Add `ASSET_ATTACHMENT` to `MediaKind` (owner_id = asset id). Two places **must** follow, or it
breaks:

1. `MediaController`'s `switch` on `MediaKind` — it is exhaustive, so a new constant **fails
   compilation until its access rule is written**. Rule:
   `if (!assets.canRead(currentUser.get(), media.getOwnerId())) throw new AccessDeniedException(...)`
   — note `canRead`, not a `canManage*` check, because customers can view their own asset's invoice.
2. `MediaFileRepository.sumBytesForCompany` — needs an `ASSET_ATTACHMENT → asset's company` branch,
   or asset files silently escape the storage quota (the exact bug fixed for tickets on 2026-09-09).

The CSV upload in §9 is **not** stored as media — it's parsed and discarded, so it never touches the
quota and needs no `MediaKind`.

---

## 7. Gating

### New privilege `MANAGE_ASSETS`

Per the established checklist — all five, or it half-works:

1. `Privilege` enum: `MANAGE_ASSETS("Manage Assets", "Register and edit customer assets, their warranties and documents")`
2. Frontend `Privilege` union in `types/index.ts`
3. `RolesPage` `PRIVILEGES` array
4. The `@PreAuthorize` on each `AssetController` write method
5. **Both** sidebar blocks — the admin list *and* the engineer "Administration" section
   (`hasPrivilege('MANAGE_ASSETS')`), plus the `||` condition that decides whether that section
   renders at all.

### Plan feature `ASSET_MANAGEMENT`

- Rename in `app-config.properties`: `Asset Management (future)` → `Asset Management`.
- Add `ASSET_MANAGEMENT` to the Professional and Enterprise plan feature lists.
- **Gotcha:** `app.subscription.overwrite-seeded-plans=false`, so editing the properties file does
  **not** change plans already in the database. Either tick it per-plan in the Plans UI, or flip the
  flag for one boot and put it back. Say this in the commit message.
- `useFeature('ASSET_MANAGEMENT')` hides the client-side nav; the backend `requireFeature` is what
  actually enforces it.

**No `maxAssets` limit.** Seats, tickets and storage are priced because they scale with cost. An
asset row is a row; a limit here only creates a support call when a customer racks a new switch. Easy
to add later — `CompanyLimitConfiguration` + `assertAssetsAvailable(companyId, count)` on
`CompanySubscriptionService`, matching the existing shape.

---

## 8. Integrations

### 8.1 Ticket ↔ asset

- `CreateTicketRequest.assetId` (optional). `TicketService.create` resolves it, checks the asset's
  company matches the ticket's, and sets it. Same validation on the update path.
- `TicketResponse.asset` → `AssetSummary`.
- Asset detail shows that asset's ticket history — `TicketRepository.findByAssetId(...)`, reusing the
  existing list projection. Ticket detail shows the asset as a link.
- Ticket list gains an optional `assetId` filter (Specification clause).
- `TicketService.createForContract` sets the asset when the contract covers exactly one — a PM ticket
  for a single-asset contract should arrive already pointed at the thing.

### 8.2 Contract ↔ asset coverage — **one writer, two readers**

Coverage is edited **only from the contract**, in `ContractFormModal`: a `MultiSelectMenu` of that
company's assets, replacing the covered-assets textarea. Disabled with "Pick a company first" until a
company is chosen. Existing free text renders read-only as "Legacy notes" underneath.

The asset side **never writes coverage**. It links to it:

- **`ContractDetailPage` → "View assets" button** → `/admin/assets/company/:companyId?contractId=:id`
  — the ordinary asset list, pre-filtered to that contract's assets. Hidden when the actor lacks
  `MANAGE_ASSETS`, so nobody is sent to a route that will 403 them.
- **`AssetDetailPage` → "Covered by"** card listing the contracts, each linking to
  `/admin/contracts/:id`. Read-only; the links themselves only render for someone with contract
  access.

This is the better answer to the privilege question the previous draft left open, and it's cleaner
than gating a dual-write:

- **One privilege owns one write.** `MANAGE_CONTRACTS` edits contracts; `MANAGE_ASSETS` edits assets.
  Nothing to gate, nothing to silently ignore, no side door into a contract for someone who was only
  granted asset access.
- **The filtered URL is the feature.** "Show me everything this contract covers" is a genuinely
  useful view on its own — and because filter state lives in the query string, it's bookmarkable and
  pasteable, which a modal's internal state never is.
- **One less place to keep in sync.** A both-sided editor means two components, two validation paths
  and two chances to disagree about which company an asset belongs to.

The cost is the "I just registered this switch, put it on the PM contract" path, which becomes: asset
→ its contract link → edit coverage. The "Covered by" card carries a **"Manage coverage →"** link
straight to the contract form for exactly that, so it's one extra click, not a hunt.

**Landing on the filtered list must be obvious.** Render every active filter as a labelled chip above
the table — `Covered by: KATMB PM 2026 ×` — so someone arriving by deep-link can see they're looking
at a subset and can clear it in one click. Without that, a contract covering three of a company's
forty assets reads as "this company has three assets". Fetch the contract by id for the chip label
(TanStack Query caches it; no new endpoint).

### 8.3 Warranty reminders

Per-asset toggle, off by default — nobody wants 400 emails after a bulk import (and §9 makes that a
real possibility).

- `service/asset/WarrantyMonitor` (`@Scheduled`, `app.assets.warranty-check-interval-ms`, default
  24h, 2-minute initial delay) → `service/asset/WarrantyReminderSweeper.runOnce()` (`@Transactional`).
  **Split into two beans on purpose**, same reason as `SlaMonitor`/`SlaBreachRecorder` and
  `ContractMonitor`/`ContractSweeper`: `@Scheduled` on a `@Transactional` method of the same bean
  isn't proxied the way you'd expect, and the sweep needs its own transaction boundary.
- Idempotent via `warrantyReminderSentFor = warrantyEnd`.
- **Recipients** — the customer's own people, since it's their kit:
  - every `CUSTOMER_ADMIN` of the asset's company who is `ACTIVE`, has `notifyEmail` on, and a
    non-blank email;
  - plus the company's **main engineer**, gated on the existing
    `app.notifications.email-main-engineer` flag, so your side knows a renewal conversation is due;
  - **not** the asset's own owner or guest — the guest email isn't verified and the owner may be
    anyone; a renewal is an admin conversation. Reuse the `mailUnlessSelf`-style gating helper rather
    than mailing directly.
- In-app too: new `NotificationType.ASSET_WARRANTY_DUE` via `NotificationService` (bell only —
  `NotificationService` never emails; `MailService` does).
- New config in `app-config.properties`, bound to `AppProperties`:
  ```
  app.assets.warranty-check-interval-ms=86400000
  app.assets.warranty-expiring-soon-days=30       # drives the derived WarrantyState
  app.assets.default-reminder-days=30
  ```

### 8.4 Reporting and export

- `ReportResponse` gains an `assets` block: counts by type, by status, and a warranty breakdown
  (in-warranty / expiring soon / expired / none), scoped by the same `visibleCompanies(actor, …)` the
  rest of `ReportService` uses — or `CompanyScope`, once extracted.
- The warranty split is computed in **SQL** (a `CASE` in the projection), not by loading rows. This
  must not become another per-company N+1.
- CSV out: an assets section in `ReportCsvWriter`, plus `GET /api/assets/export.csv` honouring the
  same filters as the list. Its columns match the import format (§9) **plus a leading `id`**, so the
  same sheet shape serves both directions — but re-importing an export only *updates* anything if
  upsert ships; see the open question in §13. With a create-only importer it is an export for reading,
  not a round trip.
- Gate the assets block on `REPORTING` **and** `ASSET_MANAGEMENT` for customer viewers.

---

## 9. CSV import

An asset register nobody can populate in bulk gets used once. This is what makes the module survive
contact with a customer who has ninety devices.

### Shape

**Company-scoped, from inside the company.** The button sits on
`/admin/assets/company/:companyId`, and the company comes from **the URL, never the file**. There is
deliberately no company column: one accepted upload must not be able to write rows into companies the
uploading engineer can't even see.

**Two steps, never blind.**

```
1. POST /api/assets/import/preview?companyId=X   (multipart)
       → parses, validates, writes NOTHING
       → AssetImportPreview { totalRows, okCount, warnCount, errorCount, rows[] }
2. POST /api/assets/import?companyId=X           (the same file again)
       → re-parses, re-validates, commits in ONE transaction
       → AssetImportResult { imported, skipped, auditEventId }
```

Step 2 re-sends the file rather than referencing a stashed server-side batch: no cache to evict, no
expiry to handle, no window where the preview and the commit disagree. **Re-validate on commit** —
never trust the preview's verdict, the database may have moved underneath it.

### Columns

```
name*, assetTag, type, make, model, serialNumber, location, status,
assignedTo, ownerEmail, guestName, guestEmail,
purchaseDate, warrantyStart, warrantyEnd, notes
```

`name` is the only required column.

### The template — `GET /api/assets/import/template.csv?companyId=X`

**This file leaves the building.** The primary use isn't "help me start an import" — it's *"download
it, email it to the customer, let them fill in their own kit, import what comes back."* That single
fact drives every decision below, because the person typing into it has **never seen the app, has no
login, and cannot see the import modal.** Any guidance that lives in the UI is guidance they will
never read.

So the file has to document itself:

```csv
# ASSET LIST — <Company Name>
# Fill one row per device. Leave anything you do not know blank.
# Dates must be written as YYYY-MM-DD (e.g. 2026-03-31). Do not reformat the date columns in Excel.
# type   : SERVER, WORKSTATION, LAPTOP, NETWORK_DEVICE, FIREWALL, STORAGE, PRINTER, UPS,
#          SOFTWARE_LICENSE, PERIPHERAL, OTHER
# status : ACTIVE, IN_REPAIR, IN_STORAGE, RETIRED, DISPOSED   (leave blank for ACTIVE)
# Who uses it: put the person's name in guestName. Only fill ownerEmail if that person signs in
#          to the support portal. Leave both blank for shared kit (firewalls, printers, servers).
# Lines starting with # are ignored. Delete the EXAMPLE rows before sending this back.
name,assetTag,type,make,model,serialNumber,location,status,assignedTo,ownerEmail,guestName,guestEmail,purchaseDate,warrantyStart,warrantyEnd,notes
EXAMPLE - Finance Laptop,,LAPTOP,Dell,Latitude 5540,SN12345678,HQ Level 3,ACTIVE,,,Aina Rahman,,2025-01-15,2025-01-15,2028-01-14,Delete this row
EXAMPLE - Reception Printer,,PRINTER,HP,LaserJet M404dn,VNB3K12345,HQ Lobby,ACTIVE,,,,,2024-06-01,2024-06-01,2027-05-31,Delete this row
EXAMPLE - Core Firewall,,FIREWALL,Fortinet,FortiGate 100F,FG100F0000012345,DC Rack B3,ACTIVE,,,,,2024-03-10,2024-03-10,2027-03-09,Delete this row
```

**`#` comment lines are the mechanism.** The parser skips any line whose first cell starts with `#`,
and skips blank lines — anywhere in the file, not just at the top. It costs two lines in the parser
and it's what lets a CSV carry its own instructions to someone outside the system. This reverses the
earlier draft's "put the column reference in the modal": the modal panel still exists for *your*
engineers, but it is no longer where the documentation primarily lives, because the primary reader
isn't in the app.

**Per-company, from the URL.** `?companyId=X` doesn't put a company column in the file — that is
still forbidden (§9), since the company is fixed at import time by the URL you import from. It does
two things: it puts the customer's name in the `#` header, and it names the download
`asset-template_acme-sdn-bhd.csv`. When three filled sheets come back by email in the same week,
that filename is the difference between a clean import and a guess.

**Valid enum values are printed in the file**, not just validated on upload. A customer who has to
guess between "Firewall", "firewall" and `FIREWALL` will guess wrong, and the round trip costs a day
of email. Generate those lines from the enums themselves so they cannot drift from what the backend
accepts.

**`ownerEmail` is steered away from deliberately.** Most people at a customer site are *not* portal
users, so a customer-filled sheet where everyone's email is typed into `ownerEmail` would fail the
§9 rule on nearly every row. The instructions therefore push `guestName` as the normal answer and
frame `ownerEmail` as the exception. The strict ERROR rule stays — nothing gets silently downgraded —
but see the preview bulk-fix below, which turns that wall of errors into one click when it happens
anyway.

**The example rows are left in, not stripped by the parser.** They're prefixed `EXAMPLE - `, they say
"delete this row", and if someone forgets, the preview shows three rows named `EXAMPLE - …` before
anything is written. Better than magic server-side filtering on a name prefix, which would eventually
eat a real asset that someone legitimately named "EXAMPLE rig".

**Mechanics** — match the existing CSV conventions exactly:
- **Write the UTF-8 BOM**, as `AuditCsvWriter` does; Excel on Windows otherwise reads UTF-8 in the
  local codepage and mangles non-ASCII names. Symmetry with the parser: **emit a BOM on the way out,
  strip one on the way in.**
- **Same RFC 4180 quoting and formula-injection guard as `AuditCsvWriter`** (leading apostrophe on a
  cell starting `=`, `+`, `-`, `@`). Reuse that class's `escape()` rather than writing a third copy —
  pull it into a shared `util/CsvWriter` when assets become the third caller. **The importer must
  strip a leading apostrophe**, or a guarded value can't survive the round trip.
- Served as `text/csv; charset=UTF-8`, `Content-Disposition: attachment`, matching
  `ReportController.exportCsv`.

**Assignment resolves by inference**, with `assignedTo` as an optional explicit override:

| In the row | Result |
|---|---|
| `ownerEmail` matches an active user of this company | `SYSTEM_USER` |
| `ownerEmail` present but matches nobody in this company | **ERROR** — *"no user with that email in this company; put the name in guestName instead"* |
| no `ownerEmail`, `guestName` present | `GUEST` |
| neither | `SHARED` |

An unmatched `ownerEmail` is an error rather than a quiet fall-through to `GUEST`, because silently
downgrading someone's real account to a text string is the kind of data loss you only notice a year
later. **But when a customer-filled sheet trips it in bulk, the preview offers a one-click bulk fix:**
*"14 rows have an email that isn't a portal user — treat them all as guests?"*, which moves each
email into `guestEmail` and its local part into `guestName` for review. Strict by default, one click
to resolve — not thirty manual edits.

### Validation

**ERROR** (blocks the whole import — see below): missing `name`; unrecognised `type`, `status` or
`assignedTo`; unparseable date; `warrantyEnd` before `warrantyStart`; `ownerEmail` not a user of this
company; `assetTag` colliding with an existing asset in this company, or duplicated within the file.

**WARN** (imports anyway, flagged in the preview): `serialNumber` duplicated in this company or
within the file; `warrantyEnd` already in the past.

**All-or-nothing on commit**, in one `@Transactional` method. The alternative — import the good rows,
skip the bad — leaves you holding 197 of 200 assets and no easy way to work out which three are
missing. Because the preview already showed exactly what's wrong before anything was written, "fix
the sheet and re-upload" is a better loop than reconciliation. (If it ever proves annoying in
practice, partial import is a small change from here; the row-level statuses already exist.)

### Parsing gotchas — each of these will bite once

- **Strip the UTF-8 BOM.** Excel writes one, and without stripping it the first header reads
  `﻿name`, so *every* row fails on "missing name" and the file looks corrupt.
- **Dates: ISO `yyyy-MM-dd` only.** Reject anything else with an error naming the expected format.
  Accepting `dd/MM/yyyy` too sounds friendlier until `03/04/2026` arrives and there is no way to know
  whether it's March or April. The template shows ISO; a clear rejection beats a wrong date silently
  stored.
- **Cap the input:** `app.assets.import-max-rows=1000` and a file-size limit, both in
  `app-config.properties`. Reject over-large files at the preview step with a plain message.
- **Trim every cell**, and treat blank-after-trim as absent.

### Audit

The commit writes an `AuditWriter` event — *"imported 47 assets into Acme Sdn Bhd"* — with the row
count and the actor. A bulk write is precisely the thing you want a trail for, and the audit module
already exists.

---

## 10. Frontend

```
src/api/assets.ts
src/pages/assets/AssetCompaniesPage.tsx   the picker — companies + counts
src/pages/assets/AssetsPage.tsx           one company's list + filter bar (URL state) + toolbar
src/pages/assets/AssetDetailPage.tsx      detail, assignment, covering contracts, docs, tickets
src/pages/assets/AssetFormModal.tsx       create/edit
src/pages/assets/AssetAssignmentField.tsx the three-way control (§4) — its own component
src/pages/assets/AssetImportModal.tsx     upload → preview table → confirm (§9)
src/pages/assets/AssetFilterBar.tsx       filters + the active-filter chips (§8.2)
src/pages/assets/assetMeta.ts             ASSET_TYPES, ASSET_STATUSES, warranty tones
src/pages/client/ClientAssetsPage.tsx     read-only, no picker
```

Routes in `App.tsx`:

```tsx
<Route element={<RequireRoleOrPrivilege roles={['ADMIN','ENGINEER']} privilege="MANAGE_ASSETS" />}>
  <Route path="admin/assets" element={<AssetCompaniesPage />} />
  <Route path="admin/assets/company/:companyId" element={<AssetsPage />} />
  <Route path="admin/assets/:id" element={<AssetDetailPage />} />
</Route>
<Route path="assets" element={<ByPortal staff={<Navigate to="/admin/assets" replace />}
                                        client={<ClientAssetsPage />} />} />
```

Sidebar: `HardDrive` (lucide), label "Assets" — admin block, engineer Administration block, and the
client block behind `useFeature('ASSET_MANAGEMENT')`.

**The assignment control** — one segmented group, three states, only the relevant input visible:

```
Assigned to    ( ) A user in the system   [ SelectMenu of this company's users ]
               ( ) Someone else           [ Name ______ ]  [ Email (optional) ______ ]
               (•) Shared / infrastructure    ← default; nothing else to fill in
```

Pre-select by type as a convenience: `LAPTOP`/`WORKSTATION` → *A user in the system*;
`FIREWALL`/`PRINTER`/`NETWORK_DEVICE`/`UPS`/`STORAGE`/`SERVER` → *Shared*. A hint, freely
overridable — never a rule.

**The list toolbar** — four actions, because they are four different jobs, not one job with options:

| Action | Style | Goes to |
|---|---|---|
| **+ Add Asset** | primary | `AssetFormModal` — the one-off, "a switch arrived today" path |
| **Import CSV** | secondary | `AssetImportModal` — the filled sheet coming back |
| **Export CSV** | secondary | `/api/assets/export.csv` **with the current filters applied** |
| **Download template** | secondary | `/api/assets/import/template.csv?companyId=…` — the blank sheet going out |

The last three are CSV tools and read as a group; visually segment them from *Add Asset* so the
primary action stays obvious. Below ~768px they collapse into a single **⋯** menu — four buttons plus
a filter bar does not fit a phone, and this page is the one an engineer opens on site.

Two details that prevent a support call each:

- **Export follows the filters.** Someone who filtered to "expired warranty" and hit Export expects
  those rows. Label it with the count when a filter is active — *"Export 12 filtered"* vs
  *"Export all 212"* — so nobody emails a customer the wrong list.
- **Download template is a top-level button, not buried in the import modal**, because sending the
  sheet to a customer and importing what comes back happen days apart. Making the outbound step a
  sub-step of the inbound one gets the workflow backwards.

**The empty state** (a company with no assets yet) leads with *Download template* and *Import CSV*
rather than *Add Asset* — a brand-new customer's register gets populated from a sheet, not typed in
one device at a time.

**The import modal** — three states in one modal: drop/choose file → preview table (row number,
status pill, the parsed values, the message for anything not OK) → confirm. Disable Confirm entirely
while `errorCount > 0`, with the reason on the button. Offer *Download template* and *Export current*
from the empty state too, and surface the bulk-fix action from §9 above the table when unmatched
emails are the dominant error.

Other touch points:
- `ContractDetailPage` — the **"View assets"** button (§8.2), on the coverage card.
- `CompanyDetailPage` — an "Assets" card next to Plan Usage, linking to `/admin/assets/company/:id`.
- `NewTicketModal` / `NewTicketPage` — optional asset `SelectMenu` from the selected company, hidden
  entirely when the company has no assets or lacks the feature.
- `TicketDetailPage` — asset row linking to the asset.
- `lib/format.ts` — reuse `formatDateOnly` / `todayIso`; **never** `new Date(isoDateOnly)`, that
  reads as UTC midnight and shifts the day in UTC+8.
- `badgeStyles.ts` — warranty tones: in-warranty success, expiring soon warn, expired error, none
  neutral. Import row statuses reuse the same tones.

---

## 11. Build order

Each phase compiles and is usable on its own.

| # | Phase | Contents |
|---|---|---|
| 1 | **Core backend** | `model/asset/*` incl. the assignment trio, repository (+`countsByCompany`), `CompanyScope` extraction, `AssetService` CRUD + scoping + invariants, DTOs, `AssetController`, `MANAGE_ASSETS` in all five places, `ASSET_MANAGEMENT` turned on |
| 2 | **Picker + list** | `api/assets.ts`, `AssetCompaniesPage`, `AssetsPage`, `AssetFilterBar` with URL-backed state and chips, sidebar, routes, one-company redirect |
| 3 | **Form + detail** | `AssetFormModal`, `AssetAssignmentField`, `AssetDetailPage`, client read-only page |
| 4 | **CSV import + export** | template endpoint, preview + commit, `AssetImportModal`, `export.csv` with matching columns, audit event |
| 5 | **Media** | `ASSET_ATTACHMENT` kind, `MediaController` case, `sumBytesForCompany` branch, upload UI |
| 6 | **Ticket link** | `Ticket.asset`, request/response fields, ticket picker, asset ticket history, list filter |
| 7 | **Contract link** | `contract_assets`, contract-side multi-select, "View assets" deep link, "Covered by" card |
| 8 | **Warranty sweep** | toggle fields, `WarrantyMonitor` + `WarrantyReminderSweeper`, `ASSET_WARRANTY_DUE`, mail copy, config |
| 9 | **Reporting** | report block, analytics cards |

Phases 6–8 are independent of each other. 4 is what makes the module usable at all; 7 and 8 are what
make it worth having.

---

## 12. Verification and known constraints

- Backend compile is **yours to run**: `mvnw.cmd -q compile` in Windows cmd. The sandbox can't reach
  Maven Central (proxy 403) and the device VM has a JRE only. Cloud-side `javac` gives a syntax-only
  parse check at best — ignore its "cannot find symbol" noise.
- Frontend: `tsc` + `oxlint` + `vite build`, run in Windows cmd (`node_modules` are Windows
  binaries). Screenshots via `/tmp/stub.mjs` + playwright with `ROLE` set.
- Schema arrives through `ddl-auto=update`. It **adds** columns and tables; it never backfills, and it
  can't add a non-null column to a populated table without a `columnDefinition` default. That applies
  to `assigned_to`, `warranty_reminder_enabled` and `status`; `tickets.asset_id` is nullable, so fine.
- Case-insensitive filesystem: never create two files differing only by case.
- **Test cases that will catch it if it's wrong:**
  - warranty ending today / in 29 days / in 31 days / yesterday / never — the derived-state
    boundaries;
  - switch an asset `SYSTEM_USER → GUEST → SHARED → SYSTEM_USER`, confirming no stale `owner_id` or
    `guest_name` survives any hop;
  - deactivate a user who owns three assets — the assets keep the owner and show `ownerInactive`;
  - an engineer assigned to one company sees the redirect; assigned to none sees an empty picker, not
    an error;
  - an engineer assigned to company A opening `/admin/assets/company/B` by URL gets a 403, not a list;
  - the "View assets" deep link lands with a visible, clearable filter chip — not a silently
    truncated list;
  - a CSV saved from Excel (BOM, `dd/MM/yyyy` dates, trailing blank rows) is rejected with messages
    that say what to fix, and a re-upload of the corrected file commits every row.

---

## 13. Open questions

1. **Upsert on import — update existing rows, or create-only?** Phase 4 as written is create-only:
   an `assetTag` matching an existing asset is an ERROR, which is the safe default and needs no
   decision to ship. But *"export 50 assets, fill in the warranty dates in Excel, re-import"* is the
   single most valuable thing this feature could do, and it needs matching-row updates.

   The workflow in §9 makes this sharper: once you're sending sheets to customers, the second round
   is *"here's what we have on file — correct it and send it back"*, which is an export the customer
   edits and returns. That is upsert by definition, and it's the point at which create-only starts to
   hurt.

   Recommendation: ship create-only in phase 4, then add upsert as an **opt-in checkbox on the import
   modal** — *"Update existing assets that match by tag"* — so the destructive mode is never the
   default and the preview can report "12 new, 38 updated, 3 errors" before anything is written.
   Matching on `assetTag` only works if tags are actually populated, which is an argument for
   auto-generating them (next question).
2. **Asset tag generation** — auto (`<COMPANY_CODE>-<TYPE>-<seq>`) or manual? The plan assumes manual
   with a per-company uniqueness check. Auto needs a per-company counter, and interacts with the CSV
   import (blank tag column → generate one?) and with upsert above.
3. **Parent/child assets** — a blade in a chassis, a licence on a server. A self-referencing
   `parent_id` is cheap now and awkward to retrofit. Recommendation: add the column in phase 1, expose
   it in the UI only if it's actually wanted.

*Resolved:* contract coverage is contract-side only, with a filtered deep link from the contract page
(§8.2). CSV import is in, as phase 4 (§9).

---

## 14. Build log

### Phase 1 — core backend · written 2026-09-18 · NOT YET COMPILED

Parse-verified only: javac 21 in the cloud container over the 16 touched files with no classpath
produced 381 errors, **every one of them `cannot find symbol` or `package … does not exist`** — pure
dependency noise, zero syntax errors. That is the strongest signal available here; the sandbox can't
reach Maven Central and the device VM has a JRE without javac. **`mvnw.cmd -q compile` in Windows cmd
is still the real check.**

**New**

| File | Notes |
|---|---|
| `model/asset/Asset.java` | table `assets`; indexes on company/warranty_end/serial/owner. Derived: `warrantyState`, `daysToWarrantyEnd`, `usedByLabel`, `ownerInactive` |
| `model/asset/AssetType·AssetStatus·AssetAssignment·WarrantyState` | each carries `label` + `getLabel()`; **none overrides `toString()`**, so no `@JsonValue` needed |
| `repository/asset/AssetRepository.java` | + `JpaSpecificationExecutor`; `countsByCompany` is the one grouped picker query |
| `service/company/CompanyScope.java` | the shared "which companies can this actor see" rule |
| `service/asset/AssetService.java` | CRUD, scoping, feature gate, assignment invariants |
| `dto/asset/AssetRequest·AssetResponse·AssetSummary·AssetCompanyRow` | summary + company row are records |
| `controller/asset/AssetController.java` | `/api/assets` |

**Modified:** `Privilege` (+`MANAGE_ASSETS`), `CurrentUser` (+`canManageAssets`), `AppProperties`
(+nested `Assets`), `app-config.properties` (assets block, feature rename, Professional plan),
`frontend/src/types/index.ts`, `frontend/src/pages/admin/RolesPage.tsx`.

**Decisions taken while building, that aren't above:**

- **`update()` checks write access TWICE** — against the asset's current company *and* the requested
  one. Checking only the target lets someone move an asset out of a company they can't see;
  checking only the source lets them move one into it.
- **`resolveOwner()` requires the owner to belong to the asset's own company.** Without it an admin
  could put customer A's laptop in customer B's employee's name — and that name then renders on a
  page customer A can read.
- **`CompanyScope.visibleCompanyIds` returns `null` for "unrestricted"**, not a list of every id.
  The callers use it to decide whether to add a `company_id in (…)` clause at all, and materialising
  every company just to hand it back into an IN list costs a full scan on every request.
  `ReportService.visibleCompanies` was deliberately **not** folded into it — it mixes in a
  requested-company argument and a REPORTING gate, so collapsing them would change that endpoint's
  behaviour. Worth doing separately.
- **Duplicate serials are neither rejected nor warned in phase 1.** There is no warnings channel in
  this API. Inventing one for a single case would be worse than the comment in `apply()` pointing at
  the phase-4 CSV preview, which is the first place in the module with somewhere to put a warning.
- **The warranty reminder can't be switched on without a warranty end date** — a reminder with
  nothing to count back from is a silent no-op, and a toggle that reads "on" while doing nothing is
  worse than a validation error.
- `delete()` carries the guard's reasoning as a comment but no reference checks: the ticket and
  contract FKs don't exist until phases 6 and 7.

**The five-place privilege checklist is 4 of 5 done.** `Privilege`, the frontend union, `RolesPage`
and the controller are wired. **Both Sidebar blocks are deliberately deferred to phase 2** — a nav
link to `/admin/assets` before the route and page exist ships a 404. Wire them in the same commit as
the routes.

### Phase 2 — picker + list · written 2026-09-18 · **VERIFIED**

**The frontend can be verified properly from here after all** — this is worth knowing for every
later phase. The device's `node_modules` are Windows binaries, but the sources are portable: zip
`src` + the tsconfigs + `package.json` + `index.html` + `public/`, `npm install` in the cloud
container, and run the real toolchain. Phase 2 was checked that way:

| Check | Result |
|---|---|
| `tsc -p tsconfig.app.json --noEmit` | **clean** — and this config has `noUnusedLocals` + `noUnusedParameters`, so an unused import is an error, not a warning |
| `oxlint src` | **clean in the asset files**; the 8 findings are all pre-existing (MultiSelectMenu, DateRangePicker, BusinessHoursEditor, TicketDetailPage, AuditLogPage) |
| `vite build` | **succeeds**, 2589 modules |

**New**

| File | Notes |
|---|---|
| `api/assets.ts` | multi-value filters go out comma-joined (`?type=LAPTOP,SERVER`), matching what the page keeps in its own URL |
| `pages/assets/assetMeta.ts` | `ASSET_TYPES`, `ASSET_STATUSES`, `ASSIGNMENTS`, `WARRANTY_STATES`, tone maps, `warrantyPhrase` |
| `pages/assets/assetFilters.ts` | `useAssetFilters()` — URL-backed filter state + the chip list |
| `pages/assets/AssetFilterBar.tsx` | search / type / status / warranty / assigned-to / date range + chips |
| `pages/assets/AssetCompaniesPage.tsx` | the picker, as a card grid with warranty counts |
| `pages/assets/AssetsPage.tsx` | one company's register |

**Modified:** `types/index.ts` (`Asset`, `AssetCompanyRow`, `AssetRequest` + the four unions),
`App.tsx` (routes), `Sidebar.tsx` (**both** blocks — this closes the fifth item on the privilege
checklist).

**Decisions taken while building:**

- **The warranty date range is two plain `<input type="date">`, not the shared `DateRangePicker`.**
  That control is built around report presets — "last 7 days", "this month" — which are relative to
  today and point *backwards*. "Warranty ends between" is an absolute window that usually points
  *forwards*, so its presets would be actively wrong here rather than merely unhelpful.
- **Every filter write uses `replace: true`.** Without it, typing eight characters into the search
  box pushes eight history entries and the back button has to walk out of them one keystroke at a
  time.
- **The search box is debounced 300 ms and syncs both ways**, using a `pushed` ref to tell its own
  writes apart from external ones. Without that, clearing the Search chip wouldn't clear the input,
  and the input would immediately push the old text back.
- **The company name on the list page comes from `/api/assets/companies`, not the companies API.**
  That endpoint is already scoped by exactly the rule this page obeys, so it needs no second
  permission surface — and someone arriving from the picker already has it cached.
- **Warranty counts render on a company card only when non-zero.** A row of zeroes on every card
  trains people to stop reading the numbers, which is the opposite of why they're there.
- **`NONE` is grey, not red.** "No warranty date recorded" is a gap in the record; "went out of
  warranty in March" is a fact about the asset. Same colour would send someone chasing a renewal
  that may not exist.
- **The one-company redirect waits for the data to land** — redirecting during `isLoading` would
  bounce the page on every visit over a slow connection.
- **Table rows are deliberately NOT clickable yet.** `AssetDetailPage` is phase 3; a row that looks
  clickable and isn't is worse than one that doesn't pretend. Phase 3 adds the row click and the
  detail route in the same commit.

### Phase 3 — form + detail · written 2026-09-18 · **VERIFIED**

`tsc` clean · oxlint clean in the asset files (the same 8 pre-existing findings elsewhere) ·
`vite build` succeeds.

**New:** `pages/assets/AssetAssignmentField.tsx`, `pages/assets/AssetFormModal.tsx`,
`pages/assets/AssetDetailPage.tsx`, `pages/client/ClientAssetsPage.tsx`.
**Modified:** `AssetsPage` (+ Add asset, clickable rows), `App.tsx` (routes), `Sidebar.tsx`
(client block).

**Decisions taken while building:**

- **The company is not a field on the form.** `AssetFormModal` takes it as a prop from the page and
  renders it as text. Putting a company dropdown here would reintroduce exactly the mistake the
  company-first navigation exists to make unreachable.
- **Picking a type nudges the assignment** — laptop/workstation → *A user in the system*, everything
  else → *Shared* — but only until the user touches the assignment themselves (`assignmentTouched`).
  A default that keeps overriding a deliberate choice is worse than no default.
- **Clearing the warranty end date disarms the reminder.** Without it the checkbox goes disabled
  while still ticked, and submitting fails with "turn it on after setting a date" about a toggle
  that is visibly already on. Same shape as the contract form disarming auto-ticketing when a
  contract turns ticket-based: a setting that can no longer apply is switched off, not left armed
  behind a greyed-out control.
- **Deactivated users are excluded from the owner picker — except the one already selected.** You
  don't hand a laptop to someone who can't sign in, but editing an asset owned by a leaver must not
  silently reassign it just because the form couldn't render the name.
- **`/assets` sits OUTSIDE the MANAGE_ASSETS route guard.** It is the customer's own equipment list
  and a customer never holds that privilege; their access is decided server-side by company scope
  plus the ASSET_MANAGEMENT plan feature, which is the only layer that can actually enforce it.
- **No privilege check on the "Add asset" button** — the route guard already limits the page to
  ADMIN, ENGINEER or MANAGE_ASSETS, which is exactly who the API lets write. A second check would
  be a second thing to keep in sync.
- **The client list sends no `companyId`.** The backend resolves a customer to their own company
  anyway, so sending one would only add a value the client could tamper with for no benefit.
- **The detail page has no ticket or contract cards.** They land in phases 6 and 7 with the links
  themselves; stubbing empty cards now would promise something that isn't there.

### Next — phase 4 (CSV import + export + template)

The three remaining toolbar buttons, `/api/assets/import/template.csv` (with the `#` comment
header), preview + commit endpoints, `AssetImportModal`, `export.csv`, and the audit event.
