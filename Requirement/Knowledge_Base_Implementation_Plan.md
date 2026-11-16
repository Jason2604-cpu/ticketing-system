# Knowledge Base — Implementation Plan

Status: **all seven phases built (2026-10-01/02)** — the v1 Knowledge Base is complete. See §14 Build log.
Written 2026-10-01 from these decisions (Jason, 2026-10-01): one KB for both audiences with
per-article visibility; articles global or pinned to companies; v1 includes ticket-form
suggestions, ticket links, images/attachments and draft → publish with versions; customer access
follows the plan feature.
Revised 2026-10-01 (Jason): **every** publish — Internal or Public — needs Admin or
`MANAGE_KNOWLEDGE_BASE`; engineers write and press **Request publish**; customers only read;
articles are in English.

Feature code `KNOWLEDGE_BASE` already exists in the catalog (`app-config.properties`
`app.subscription.features[3]`, `types/index.ts` `FeatureCode`) and is already in the Professional
and Enterprise plans (not Starter), with no code behind it. This plan fills it in.

---

## 1. Scope and the decisions behind it

**An article is a written answer to a recurring question** — "how do I reset the VPN token", "printer
shows offline after a Windows update", "Acme's site-B network diagram". It exists to:

- let customers solve the easy ones themselves *before* raising a ticket (suggestions on the form),
- give engineers one place for fixes and runbooks instead of old tickets and chat history,
- let a reply say "follow this article" instead of retyping the same steps.

Deliberately **out of scope for v1**: "was this helpful?" voting, article comments, a public
(not-signed-in) help site, multi-language articles, AI answers, approval chains with more than one
reviewer, per-article permissions beyond visibility + companies. None of these is blocked by the model.

| Decision | Choice |
|---|---|
| Audience | One KB. Each article is **Internal** (staff only) or **Public** (customers can read it too) |
| Company scope | **Global, or pinned to chosen companies** — the same model as ticket templates (§3) |
| Who writes | Admin, Engineer, and the new `MANAGE_KNOWLEDGE_BASE` privilege (§7) |
| Who publishes | **Admin / `MANAGE_KNOWLEDGE_BASE` only — every article, Internal or Public.** Engineers save drafts and press **Request publish**; a manager publishes or declines (§5.3) |
| Who reads | Staff within their company scope; customers **read only** — Public + published + global-or-their-company, **and** their plan has `KNOWLEDGE_BASE` |
| Language | **English.** Search stems with the `english` dictionary (§6) |
| Format | **Markdown**, rendered with `react-markdown` + `remark-gfm` (no raw HTML → no XSS surface) |
| Versions | Every **publish** writes an immutable version; readers always see the published version while the working copy is edited; any version can be restored into the working copy |
| Search | PostgreSQL full-text search on the published version (title > summary > body), plus prefix matching for type-ahead (§6) |
| Ticket form | Customers see up to 5 matching articles under the title as they type (§8.1) |
| Ticket link | Staff link/insert articles in a ticket; the ticket shows "Related articles" (§8.2) |
| Media | New media kind `KB_ATTACHMENT`: inline images and downloadable files; not counted against any company's storage quota (§9) |

---

## 2. The flow

```
 CUSTOMER (plan has KNOWLEDGE_BASE)                 STAFF
 ──────────────────────────────────                 ─────
 Sidebar → Help Centre                              Sidebar → Knowledge Base
      │                                                  │
      ▼                                                  ▼
 /help            search · categories · popular     /kb            same reader, plus Internal
      │                                                  │          articles and a "Manage" button
      ▼                                                  ▼
 /help/:slug      published version only            /kb/:slug      + visibility badge, linked tickets
                                                         │
 New ticket (/tickets/new)                                ▼ (writers)
      title: "printer offline after upd…"           /admin/kb                 MANAGE LIST
      ┌──────────────────────────────────┐               │  tabs: Drafts · Published · Needs publishing · Archived
      │ These articles might help:        │               │  [ + New article ]  [ Categories ]
      │  • Printer offline after Windows… │               ▼
      │  • Re-adding a network printer    │          /admin/kb/:id/edit         EDITOR
      └──────────────────────────────────┘               │  title · summary · category · visibility · companies
         (opens in a side panel; the form                │  Markdown editor ⟷ preview · paste/drag images
          keeps what was typed)                          │  attachments · [Save draft]
                                                         │  managers: [Publish…] [Decline request]
                                                         │  engineers: [Request publish]
                                                         ▼
 Ticket detail (staff)                              /admin/kb/:id/history       VERSIONS
      reply box: [Template ▾] [Article ▾]                    view any version · restore into the draft
      sidebar:  Related articles (linked)
      resolved ticket: [Create article from ticket]
```

---

## 3. Domain model — `model/kb/`

### `KbArticle` → table `kb_articles` (the working copy + pointers)

| Column | Type | Notes |
|---|---|---|
| `id` | bigint identity | |
| `slug` | varchar(160) unique, not null | from the title on create (`printer-offline-after-windows-update`), de-duplicated with `-2`, `-3`; **never changes** after first publish so links stay valid |
| `title` | varchar(200) not null | working copy |
| `summary` | varchar(300) | working copy; shown in lists and suggestions |
| `body` | text not null | working copy, Markdown |
| `category_id` | bigint FK → `kb_categories` | nullable = "Uncategorised" |
| `visibility` | varchar(10) not null | `INTERNAL` \| `PUBLIC` (working copy) |
| `status` | varchar(12) not null | `DRAFT` \| `PUBLISHED` \| `ARCHIVED` |
| `published_version_id` | bigint FK → `kb_article_versions` | what readers see; null until first publish |
| `has_unpublished_changes` | boolean not null default false | working copy differs from the published version |
| `publish_requested_at` / `publish_requested_by` | timestamp / FK users | an engineer asked for the working copy to be published (§5.3); cleared by publish or decline |
| `view_count` | bigint not null default 0 | reader views (not editors) |
| `created_by`, `updated_by` | FK users | |
| `created_at`, `updated_at`, `published_at` | timestamp | `published_at` = latest publish |

Companies: `@ManyToMany Set<Company> companies` on join table **`kb_article_companies`**
`(article_id, company_id)` PK both — empty set = global. Same `isScoped()` / `appliesTo(companyId)`
helpers as `TicketTemplate`, but **LAZY** with a batch fetch (templates are EAGER; KB lists are larger).

### `KbArticleVersion` → table `kb_article_versions` (immutable publish history)

| Column | Notes |
|---|---|
| `id`, `article_id` FK, `version` int | unique `(article_id, version)`; 1, 2, 3… |
| `title`, `summary`, `body`, `visibility`, `category_id` | snapshot of the working copy at publish time |
| `company_ids` | `bigint[]` snapshot (scope is part of what was published) |
| `change_note` | varchar(200), optional — "Added step for Windows 11" |
| `published_by`, `published_at` | |
| `search_vector` | **generated** `tsvector`, GIN-indexed (§6) — not mapped in JPA, so `ddl-auto=validate` ignores it |

### `KbCategory` → table `kb_categories`

`id`, `name` varchar(80) unique (case-insensitive), `description` varchar(200), `icon` varchar(40)
(a lucide icon name from a short allow-list), `sort_order` int, `created_at`. One level — no nesting
in v1.

### `KbTicketLink` → table `kb_ticket_articles`

`(ticket_id, article_id)` PK, `linked_by` FK users, `linked_at`. Index on `article_id` ("used in N tickets").

### Enums

- `KbVisibility { INTERNAL, PUBLIC }`
- `KbStatus { DRAFT, PUBLISHED, ARCHIVED }`
- `MediaKind` gains `KB_ATTACHMENT` (owner = article id)
- `Privilege` gains `MANAGE_KNOWLEDGE_BASE`

---

## 4. Who can see what

The rules live in one place, `KbAccess` (service), and every endpoint goes through it — the same
pattern as `CompanyScope`.

| Caller | Drafts / working copies | Published **Internal** | Published **Public**, global | Published **Public**, pinned to company X |
|---|---|---|---|---|
| Admin, `MANAGE_KNOWLEDGE_BASE` | ✔ all (edit, publish, archive) | ✔ | ✔ | ✔ |
| Engineer (assigned to companies S, or unrestricted) | ✔ global + pinned within S (edit, request publish — never publish) | ✔ global + pinned within S | ✔ | ✔ if X ∈ S |
| Default employee without the privilege | ✘ | ✘ | ✘ | ✘ |
| Customer / Company Admin of company C, plan **has** `KNOWLEDGE_BASE` | ✘ | ✘ | ✔ | ✔ if X = C |
| Customer whose plan **lacks** it | ✘ | ✘ | ✘ (403 on /help; suggestions return `[]`) | ✘ |

Notes:

- "Pinned within S" uses `CompanyScope.visibleCompanyIds(actor)` — `null` = unrestricted, so the
  engineer-with-no-assignments convention carries over unchanged.
- An **archived** article is visible only to writers (manage list), never to readers or suggestions.
- Customers have **no write path at all** — no manage endpoints, no proposals (`/api/kb/manage/**`
  is closed to customer roles by `@PreAuthorize`).
- An engineer can only pin an article to companies inside S (an unrestricted engineer: any), and
  can't edit an article pinned to a company outside S — the same rule as tickets.
- A customer following a stale link to something they can't see gets **404**, not 403 — don't confirm
  the article exists (same as tickets of other companies).
- The plan check is `CompanySubscriptionService.hasFeature(companyId, "KNOWLEDGE_BASE")` for lists and
  suggestions (empty result), `requireFeature(...)` for the article page and the Help Centre (403 →
  the page shows the "not in your plan" empty state, like `ClientReportsPage`).

---

## 5. Backend surface

### 5.1 Repositories — `repository/kb/`

- `KbArticleRepository` — `findBySlug`, manage-list `Specification` (status, visibility, category,
  `lower(title) like`), `@Modifying incrementViewCount(id)` (JPQL update, doesn't touch `updated_at`).
- `KbArticleVersionRepository` — `findByArticleIdOrderByVersionDesc`, `findByArticleIdAndVersion`,
  and the **native search queries** (§6).
- `KbCategoryRepository` — ordered list + per-category published counts (one grouped query).
- `KbTicketLinkRepository` — by ticket, by article, count by article.

### 5.2 `service/kb/KbService` (writers)

- `create(req, actor)` → DRAFT working copy, slug generated, audit `KB_ARTICLE_CREATED`.
- `update(id, req, actor)` → saves the working copy; sets `has_unpublished_changes` if published.
- `publish(id, changeNote, actor)` → managers only (§5.3); snapshots a new `KbArticleVersion`, points
  `published_version_id` at it, `status = PUBLISHED`, clears `has_unpublished_changes` and any
  publish request, audit `KB_ARTICLE_PUBLISHED v<n>`. Refused when there is nothing new to publish.
- `requestPublish(id, actor)` → engineers; sets `publish_requested_*` (re-requesting just refreshes
  it), notifies Admins / `MANAGE_KNOWLEDGE_BASE` holders (§8.3, phase 7). Refused when there is
  nothing new to publish.
- `declinePublish(id, actor)` → managers; clears the request (the draft stays as it is), audit
  `KB_PUBLISH_DECLINED`.
- `archive(id)` / `unarchive(id)` → managers; readers stop/start seeing it; versions kept.
- `restoreVersion(id, version, actor)` → copies the snapshot into the **working copy** (not live
  until published again — restoring is an edit, publishing is the decision).
- `delete(id)` → only articles **never published**; published ones are archived instead (links in
  old tickets and replies must keep resolving). Deletes its attachments.
- Categories CRUD; deleting a category moves its articles to Uncategorised.

### 5.3 The publish rule — one method, `KbAccess.canPublish(actor)`

```
Admin or MANAGE_KNOWLEDGE_BASE   → publish, decline a request, archive / unarchive, categories
Engineer                         → write drafts, edit working copies, restore a version into the
                                   draft, delete a never-published draft — then "Request publish"
Customer                         → read only
```

Every word a reader sees — staff or customer, Internal or Public — was published by an Admin or a
KB manager. An engineer's edit to a published article goes into the working copy; readers keep
seeing the published version until a manager publishes the change.

### 5.4 `service/kb/KbReadService` (readers)

- `list(actor, q, categoryId, page)` → published + visible (§4); `q` uses search (§6), else newest.
- `get(actor, slug)` → the **published version** + attachments + (staff) linked-ticket count;
  increments `view_count` for readers who aren't writers.
- `suggest(actor, text, companyId)` → top 5 (§6, §8.1).
- `categories(actor)` → categories with counts of articles **this caller** can see (empty ones hidden
  for customers).

### 5.5 Controllers

`controller/kb/KbController` → `/api/kb` (readers)
`@PreAuthorize("hasAnyRole('ADMIN','ENGINEER','CUSTOMER_ADMIN','CUSTOMER') or hasAuthority('PRIV_MANAGE_KNOWLEDGE_BASE')")`

| Method | Path | |
|---|---|---|
| GET | `/articles?q=&categoryId=&page=` | published list / search |
| GET | `/articles/{slug}` | one published article |
| GET | `/categories` | with visible counts |
| GET | `/suggest?q=&companyId=` | type-ahead for the ticket forms (`companyId` honoured for staff only) |

`controller/kb/KbManageController` → `/api/kb/manage` (writers)
`@PreAuthorize("hasAnyRole('ADMIN','ENGINEER') or hasAuthority('PRIV_MANAGE_KNOWLEDGE_BASE')")`

| Method | Path | |
|---|---|---|
| GET | `/articles?status=&visibility=&categoryId=&q=` | working copies |
| GET | `/articles/{id}` | working copy + publish state |
| POST / PUT | `/articles`, `/articles/{id}` | create / save draft |
| POST | `/articles/{id}/publish` `{changeNote}` | managers (§5.3) |
| POST | `/articles/{id}/request-publish` | engineers |
| POST | `/articles/{id}/decline-publish` | managers |
| POST | `/articles/{id}/archive`, `/unarchive` | managers |
| GET | `/articles/{id}/versions`, `/versions/{n}` | history |
| POST | `/articles/{id}/versions/{n}/restore` | into the working copy |
| POST / DELETE | `/articles/{id}/attachments`, `/attachments/{mediaId}` | §9 |
| DELETE | `/articles/{id}` | never-published only |
| GET/POST/PUT/DELETE | `/categories…` | Admin / `MANAGE_KNOWLEDGE_BASE` only |

Ticket side (in `TicketController`, staff methods): `GET /api/tickets/{id}/articles` (customers get
only those they could read), `POST|DELETE /api/tickets/{id}/articles/{articleId}`.

### 5.6 DTOs — `dto/kb/`

`KbArticleRequest` (title 4–200, summary ≤300, body ≤100 000, categoryId, visibility, companyIds),
`KbArticleSummary` (list/suggest rows: id, slug, title, summary, category, visibility*, updatedAt,
snippet*), `KbArticleResponse` (published view: + body, attachments, versionNumber, publishedAt,
linkedTicketCount*), `KbManageResponse` (working copy + status, hasUnpublishedChanges,
publishRequested, canPublish), `KbVersionResponse`. *staff only.

---

## 6. Search

PostgreSQL 17 full-text search — no extra service.

```sql
-- on kb_article_versions (only published snapshots are searchable)
search_vector tsvector GENERATED ALWAYS AS (
      setweight(to_tsvector('english'::regconfig, coalesce(title,'')),   'A')
   || setweight(to_tsvector('english'::regconfig, coalesce(summary,'')), 'B')
   || setweight(to_tsvector('english'::regconfig, coalesce(body,'')),    'C')
) STORED;
CREATE INDEX ix_kb_versions_search ON kb_article_versions USING gin (search_vector);
```

- Articles are English, so the `english` dictionary stems ("printers" finds "printer", "updating"
  finds "update") and drops stop-words. Product codes like `HP-M404` are still indexed as words.
  The explicit `::regconfig` makes the expression immutable, which a generated column requires.
- **Search page** (`GET /api/kb/articles?q=`): `websearch_to_tsquery` (users can type
  `"exact phrase"` and `-word`; odd input never errors), ranked with `ts_rank_cd`, excerpt via
  `ts_headline` over the body with Markdown removed (images/links keep their label; `* ` ~` dropped;
  `# > |` become spaces). The excerpt is returned as plain-text pieces `[{text, match}]`, never HTML,
  so the page can highlight matches without rendering article text as markup.
- **Type-ahead / suggestions** (`GET /api/kb/suggest?q=&companyId=`): the typed words are sanitised
  to `[\p{L}\p{N}]+` (one-letter words dropped, at most 8), each turned into a prefix term and
  OR-ed — `printer:* | offline:* | updating:*` — and Postgres stems them and drops stop-words, so
  half-typed words match and an article sharing any meaningful word appears, best match first, then
  most viewed. Top 5. The "minimum 2 words or 8 characters" rule is the form's (phase 6).
- Visibility (§4) is applied **in the same SQL** (join to `kb_articles` on `published_version_id`,
  `status = 'PUBLISHED'`, visibility and company conditions), never filtered afterwards in Java —
  otherwise "top 5" could come back with 2.

---

## 7. Gating

### New privilege `MANAGE_KNOWLEDGE_BASE`

Label "Manage Knowledge Base" — "Write, publish and archive any article; approve engineers' publish
requests; manage categories". It is the only way besides Admin to publish. Wiring (every place a
privilege is listed):

1. `Privilege` enum (+ label/description).
2. Migration: replace `role_privileges_privilege_check` (the V3 `users_role_check` pattern).
3. `types/index.ts` `Privilege` union; `RolesPage` `PRIVILEGES` list.
4. `Sidebar` admin block + `PrivilegedLinks` (both the `any` list and the link); `StaffHomePage` `MODULES`.
5. `App.tsx` `RequireRoleOrPrivilege roles={['ADMIN','ENGINEER']} privilege="MANAGE_KNOWLEDGE_BASE"` on `/admin/kb/*`.
6. `@PreAuthorize` on both controllers (§5.5); `KbAccess` checks the authority for the publish rule.
7. `CurrentUser.COMPANY_WIDE_PRIVILEGES` — the editor's company picker needs `GET /api/companies`,
   and add it to that endpoint's `@PreAuthorize` list.

### Plan feature `KNOWLEDGE_BASE`

Customers only (§4). Already seeded into Professional/Enterprise. Because
`overwrite-seeded-plans=false`, plans an admin has edited keep their own feature list — nothing to
migrate, but worth knowing when testing with an old database.

---

## 8. Integrations

### 8.1 Suggestions on the new-ticket form

- `NewTicketPage` (customers): a `SuggestedArticles` panel under the **Issue Title** field, fed by a
  new `useDebouncedValue(title, 400)` hook → `GET /api/kb/suggest`. React Query key
  `['kb','suggest',q]`, `staleTime` 5 min, no polling. Hidden when the plan lacks the feature, the
  title is too short, or nothing matches.
- Clicking a suggestion opens the article in a **side panel** (`Modal` variant), not a navigation — the
  half-filled form must survive. The panel has "This solved it — discard my ticket" (clears the form,
  goes back) and "I still need help" (closes the panel).
- `NewTicketModal` (staff quick-create) gets the same panel, using the chosen `companyId`.
- Measuring it: audit `KB_SUGGESTION_OPENED` and `KB_TICKET_AVOIDED` (the discard button) so a
  report can later say how many tickets the KB prevented.

### 8.2 Articles in tickets

- Reply box on `TicketDetailPage` (staff): an **Article** picker next to the existing Template
  picker. Picking one (a) inserts `[Title](/help/slug)` plus the summary into the reply — the same
  append behaviour as templates — and (b) records a `kb_ticket_articles` link.
  - If the article is **Internal**, or not visible to the ticket's company, the picker says so and
    only offers "link for staff" (and ticks *Internal note* if the reply would contain the link) —
    a customer must never receive a link they'd get a 404 on.
- Ticket sidebar card **Related articles**: linked articles; customers see only the ones they can read.
- On a **resolved/closed** ticket, staff get **Create article from ticket** → new draft with the
  ticket title and the last public staff reply as the body, category guessed from the ticket
  category, linked back to the ticket.
- Article page (staff): "Linked to N tickets" with the list.

### 8.3 Notifications

Two new `NotificationType`s — `KB_PUBLISH_REQUESTED` → Admins and `MANAGE_KNOWLEDGE_BASE` holders when
an engineer presses Request publish; `KB_PUBLISH_DECIDED` → back to that engineer when a manager
publishes or declines. Needs: the enum value, a migration replacing
`notifications_type_check`, and the bell must open `/admin/kb/:id/edit` instead of a ticket (today
`Notification` only links tickets — add a nullable `link` column, or a `kb_article_id`; the generic
`link` is the better investment).

### 8.4 Audit

`KB_ARTICLE_CREATED / UPDATED / PUBLISHED / PUBLISH_REQUESTED / PUBLISH_DECLINED / ARCHIVED /
UNARCHIVED / RESTORED / DELETED`, `KB_CATEGORY_CREATED / UPDATED / DELETED`, `KB_ARTICLE_LINKED / UNLINKED` (ticket),
`KB_SUGGESTION_OPENED`, `KB_TICKET_AVOIDED`. Details as `key=value`, e.g.
`articleId=12 version=3 visibility=PUBLIC`.

---

## 9. Media

- New `MediaKind.KB_ATTACHMENT`, folder `kb-attachment`, owner = article id. Migration replaces
  `media_files_kind_check`.
- `MediaStorageService.storeKbAttachment(file, articleId, uploadedBy)` through the existing `store(...)`
  (magic-byte sniffing, UUID names, `MEDIA_UPLOADED` audit). Limits: `app.media.max-attachment-size-mb`
  (20) and allowed types; new `app.media.max-attachments-per-article=30`.
- **Images in the body:** the editor uploads on paste/drag-drop and inserts
  `![alt](/api/media/<uuid>)`. Rendered images go through the normal cookie-authenticated media URL.
- `MediaController.get` (exhaustive switch — it won't compile without the case):
  `KB_ATTACHMENT -> if (!kbAccess.canReadMedia(currentUser.get(), ownerId)) throw AccessDenied` —
  readable if the caller can read the **published** article, or is a writer who can open the draft.
- **Not counted** in any company's storage quota: `MediaFileRepository.sumBytesForCompany` lists kinds
  explicitly, so a new kind is excluded by default — KB content belongs to the provider, not a customer.
  (Fix the stale Javadoc on `usedBytesForCompany` while there.)
- Attachments are not versioned in v1: removing a file affects every version. The editor warns when
  removing a file the published version references.

---

## 10. Frontend

New dependencies (frontend `package.json`): `react-markdown`, `remark-gfm` (tables, task lists,
strikethrough), `diff` (version comparison). No WYSIWYG editor and no `rehype-raw` — raw HTML in an
article is shown as text, which is what keeps it XSS-free.

| Route | Page | Who |
|---|---|---|
| `/help` | `HelpCentrePage` — search, category tiles, popular (by views), recently updated | customers with the feature |
| `/help/:slug` | `ArticlePage` (reader) | customers |
| `/kb`, `/kb/:slug` | same two components in staff mode (Internal badge, linked tickets, "Edit") | staff, KB privilege |
| `/admin/kb` | `KbManagePage` — tabs Drafts · Published · Needs publishing · Archived; filters | writers |
| `/admin/kb/new`, `/admin/kb/:id/edit` | `KbEditorPage` | writers |
| `/admin/kb/:id/history` | `KbHistoryPage` — versions, side-by-side diff, restore | writers |
| `/admin/kb/categories` | `KbCategoriesPage` | Admin / privilege |

Components: `MarkdownView` (react-markdown + gfm, links to `/help/...` routed internally, images via
`api.mediaUrl`), `MarkdownEditor` (textarea + small toolbar: bold, heading, list, code, link, image;
**Write / Preview / Split** tabs; paste-to-upload), `SuggestedArticles`, `ArticlePicker` (reply box),
`RelatedArticlesCard`, `KbVisibilityBadge` (Internal = gray lock, Public = green globe).

Sidebar: admin & engineer portals **Knowledge Base** (`/kb`); client portal **Help Centre** behind
`useFeature('KNOWLEDGE_BASE')`; staff portal via `PrivilegedLinks`. All pages lazy-loaded like the rest.

---

## 11. Build order

| # | Phase | Contents |
|---|---|---|
| 1 | **Schema + core backend** | V-migration (tables, generated tsvector + GIN, the three CHECK constraint replacements, privilege), entities, repositories, `KbAccess` + unit tests of the §4 matrix, `KbService` (create/update/publish/request/archive/restore/delete), categories, both controllers |
| 2 | **Search** | native search + suggest queries with visibility in SQL, ranking/snippets, tests with a seeded DB |
| 3 | **Media** | `KB_ATTACHMENT`, upload/delete endpoints, `MediaController` case |
| 4 | **Authoring UI** | manage list, editor (Markdown + preview + paste-to-upload), categories, history/diff/restore, privilege wiring in Sidebar/App/RolesPage/StaffHomePage |
| 5 | **Reading UI** | `/kb` + `/help`, article page, sidebar links, plan-gated empty state |
| 6 | **Ticket integration** | suggestions on both ticket forms, article picker + links, related-articles card, create-from-ticket |
| 7 | **Notifications + polish** | `KB_PUBLISH_REQUESTED` with generic notification links, "Needs publishing" tab badge, popular/recent, audit review, docs |

Each phase compiles and is usable on its own. 2 and 3 are independent of each other; 6 needs 2.

---

## 12. Verification and known constraints

- **Access matrix tests** (§4) — every cell, for list, get, suggest, media and ticket links. These are
  the tests that matter; a leak here is a customer reading another customer's runbook.
- **XSS:** an article body containing `<script>`, `<img onerror>`, `javascript:` links renders inert.
- **Search:** a draft's words never appear in results; editing a published article doesn't change what
  readers find until it's published again; an archived article disappears from search and suggestions.
- **Versions:** publish → edit → reader still sees v1; publish → reader sees v2; restore v1 → working copy
  equals v1, readers still on v2 until published.
- **Plan:** a Starter customer gets no sidebar link, 403 on `/api/kb/articles`, `[]` from suggest, and
  the ticket form works normally.
- Known constraint: attachments aren't versioned (§9). Known constraint: `slug` is fixed after first
  publish — renaming the title doesn't change URLs (intended).
- Performance: list/suggest are one query each (visibility in SQL, categories/companies batch-fetched);
  view counting is a single `UPDATE … + 1`.

---

## 13. Open questions

1. ~~Language of articles~~ — **resolved: English** (§6).
2. ~~Engineer publishing~~ — **resolved: only Admin / `MANAGE_KNOWLEDGE_BASE` publish; engineers
   Request publish** (§5.3).
3. ~~Customer-proposed articles~~ — **resolved: customers only read.**
4. **"Was this helpful?" + view analytics report** — cheap to add in phase 7 if wanted.
5. ~~Notification links (§8.3)~~ — **done in phase 7:** a nullable `notifications.link` (V6);
   contract renewal reminders use it too.

---

## 14. Build log

### Phase 1 — schema + core backend (2026-10-01) ✔

**Database** — `V5__knowledge_base.sql`: `kb_categories`, `kb_articles` (working copy),
`kb_article_companies`, `kb_article_versions` (immutable, `company_ids bigint[]`, generated English
`search_vector` + GIN), `kb_ticket_articles` (for phase 6), and the `role_privileges` /
`media_files` CHECK constraints widened for `MANAGE_KNOWLEDGE_BASE` / `KB_ATTACHMENT`.
Tested: fresh DB, a V4 DB, and a pre-Flyway (baselined) DB all end with an identical KB schema;
the app starts with `ddl-auto=validate` against it.

**Backend** (`model/kb`, `repository/kb`, `service/kb`, `dto/kb`, `controller/kb`):

| Piece | What it does |
|---|---|
| `KbViewer` + `KbRules` | The §4/§5.3 matrix as pure functions. Manager = Admin or `PRIV_MANAGE_KNOWLEDGE_BASE` (works for a Default employee via a custom role) |
| `KbAccess` | Builds the viewer from the signed-in user (company scope, plan feature); 404 for articles you can't see, 403 for actions you can't take, plan-feature 403 for customers |
| `KbService` | create / save draft (slug follows the title until first publish), publish (managers), request publish (engineers), decline (managers), archive / unarchive (managers), versions + restore into the draft, delete never-published drafts (managers any, engineers their own), categories CRUD (managers) |
| `KbReadService` | reader list / article / categories from the **published version**, visibility in SQL; view counter (managers' views not counted) |
| `KbController` `/api/kb` | `GET /articles?categoryId&page&size`, `GET /articles/{slug}`, `GET /categories` |
| `KbManageController` `/api/kb/manage` | everything in §5.5 except search, suggest and attachments |
| Privilege | `MANAGE_KNOWLEDGE_BASE` in the enum, `CurrentUser.COMPANY_WIDE_PRIVILEGES`, `GET /api/companies`, TS `Privilege` union |

**Tests** — `KbRulesTest` (20 cases: every cell of §4, publish/request/delete rules, SQL scope
parameters), `KbSlugsTest` (4). End-to-end against a real database: 115 checks covering the reader
matrix for 8 kinds of user, the engineer → request → decline → request → publish flow, edit-after-publish
(readers keep v1 until v2 is published), restore, visibility change only live after publish,
archive, delete rules, slugs, categories (counts per caller, empty hidden for customers, delete →
Uncategorised), view counting, paging and LIKE escaping, audit rows. SQL per request: reader list
2–7 (most of a customer's are the existing plan-feature check), article 8–9, manage list 5–7.

**Left for later phases:** `RolesPage` / Sidebar / StaffHomePage entries for the privilege are added
with the authoring UI (phase 4) so it isn't offered before there's a page behind it; `q` search and
`/suggest` (phase 2); attachments and the `MediaKind` value (phase 3); notifications (phase 7).

### Phase 2 — search + suggest (2026-10-01) ✔

| Piece | What it does |
|---|---|
| `KbArticleRepository` | The reader SQL now shares one visibility fragment (`READABLE`) between list, search and suggest; `searchPublished` (ranked, paged, excerpt) and `suggest` (prefix OR query, top N, optional company) |
| `KbSearch` | Pure helpers: query normalising (cap 200 chars), the prefix query builder, excerpt → `[{text, match}]` |
| `KbReadService` / `KbController` | `GET /api/kb/articles?q=` searches (no `q` = newest first, as before); `GET /api/kb/suggest` — customers whose plan lacks the feature get `[]` (200), not an error; `companyId` honoured for staff only |
| `CompanySubscriptionService.hasFeature` | Was four queries (subscription, company, plan, feature); now one (`countActiveFeature`), same rule. Every plan-gated check in the app benefits; a customer's KB request went from 7 queries to 4 |

**Tests** — `KbSearchTest` (7). End-to-end now 166 checks; the 51 new ones cover ranking (title >
summary > body), customers never finding Internal or other companies' articles, engineer scope,
stemming, quoted phrases and `-exclusion`, drafts and unpublished edits never searchable (until
published), archived articles gone from search and suggestions, stop-word-only and odd input,
plain-text excerpts, paging, category + search, suggestion limits, plan gating and `companyId`
narrowing, the GIN index being usable, and every branch of the one-query plan-feature check
(predefined with/without, custom with/without, inactive subscription, inactive plan, no subscription).

### Phases 4 + 5 — authoring and reading UI (2026-10-01) ✔

New dependencies: `react-markdown` 10, `remark-gfm` 4, `diff` 9 (run `npm install` in `frontend/`).
All pages are lazy-loaded; the Markdown renderer is its own chunk (≈46 KB gzip) loaded only on KB pages.

| Route | Page | Who |
|---|---|---|
| `/kb`, `/kb/:slug` | `KbHomePage`, `KbArticlePage` — search as you type (excerpts with highlighted matches), topic tiles, latest; article with category, version, visibility, scope, views, Edit/History, "unpublished changes" note | Admin, Engineer, `MANAGE_KNOWLEDGE_BASE` |
| `/help`, `/help/:slug` | same pages, customer mode: no staff fields, "Raise a ticket" prompts, "not in your plan" state | Customers |
| `/admin/kb` | `KbManagePage` — tabs All · Needs publishing (count) · Drafts · Published · Archived; title search, visibility and category filters; paging | writers |
| `/admin/kb/new`, `/admin/kb/:id/edit` | `KbEditorPage` — title, summary, Markdown editor (toolbar, Ctrl+B/I/K, Write/Preview/Split), visibility cards, companies, category; **Save draft**; managers **Publish…** (change note) / **Decline request**, engineers **Request publish**; archive/unarchive; delete own never-published draft; unsaved-changes guard | writers |
| `/admin/kb/:id/history` | `KbHistoryPage` — versions with change notes, "what changed" (field changes + line diff, against the previous version or today's draft), read any version, restore into draft | writers |
| `/admin/kb/categories` | `KbCategoriesPage` — create/edit (name, description, icon from the allow-list, order), delete → articles become Uncategorised | Admin, `MANAGE_KNOWLEDGE_BASE` |

- `KbGate`: a `/help/…` link opened by staff goes to `/kb/…` and vice versa, so article-to-article
  links (written as `/help/slug`) and links pasted into tickets work for both sides.
- Sidebar: **Knowledge Base** for Admin and Engineer (highlighted on `/kb` and `/admin/kb`); Default
  employees with the privilege get it under Administration plus a Staff-home tile; customers get
  **Help Centre** when their plan has `KNOWLEDGE_BASE`. Roles page lists "Manage Knowledge Base".
- `MarkdownView`: no raw HTML (shown as text), `javascript:` links dropped by react-markdown,
  internal links routed in-app, external links open in a new tab; `.kb-prose` styles in `index.css`
  use theme tokens, so articles follow light/dark.

**Verified:** `tsc -b`, `vite build`, `oxlint` (no new warnings); a browser run (Playwright) on a
seeded database — 33 checks across Admin, Engineer, a Default employee with the privilege, a
Professional customer and a Starter customer: search highlighting, link rewriting, needs-publishing
queue, publish with change note → v2 live for customers, diff shows the added step, engineer creates
a draft through the editor and requests publish (no Publish button), route guards for categories
and `/admin/kb`, `/kb` → `/help` redirect for customers, Internal article = "not found" for
customers, Starter plan sees the plan message and no sidebar link, and a hostile article
(`<script>`, `onerror`, `javascript:`) renders as text without executing.

### Phase 3 — images & files (2026-10-02) ✔

- `MediaKind.KB_ATTACHMENT` (owner = article id), stored by `MediaStorageService.storeKbAttachment`
  with the ticket-attachment rules (magic-byte type check: PNG/JPG/WebP/PDF/CSV/TXT, 20 MB), at most
  `app.media.max-attachments-per-article` (30) per article. Not counted in any company's storage.
- `GET|POST /api/kb/manage/articles/{id}/attachments`, `DELETE …/attachments/{mediaId}` — writers who
  can edit the article. Each file says whether the draft and/or the published version uses it.
- **Who can download** (`MediaController` → `KbReadService.canReadAttachment`): writers who can edit
  the article; anyone else only if they can read the published version **and that version links the
  file** — so a file uploaded for an unpublished edit stays private even to readers of the article.
- Deleting a never-published draft deletes its files.
- Editor: paste or drop images into the text, or use Image / Attach; each upload inserts
  `![name](/api/media/…)` or `[file](/api/media/…)` at the cursor. A brand-new article is saved as a
  draft first (needs a title). "Images & files" card: thumbnails, insert, delete (warns when the
  published version uses the file).

### Phase 6 — tickets (2026-10-02) ✔

- **Suggestions on the ticket form:** customers see up to 5 matching articles under *Issue Title*
  (after 2 words or 8 characters, 400 ms pause). One opens over the form; *I still need help* keeps
  everything typed, *This solved it — discard my ticket* leaves without raising one. Staff
  quick-create shows the same list for the chosen company (opens in a new tab). Hidden when the plan
  lacks the Help Centre. Audit: `KB_SUGGESTION_OPENED`, `KB_TICKET_AVOIDED`
  (`POST /api/kb/articles/{slug}/feedback`).
- **Articles on tickets** (`kb_ticket_articles`, `KbTicketService`, `GET|POST|DELETE
  /api/tickets/{id}/articles[/{articleId}]`): staff link from the reply box (**Article** → *Add to
  reply* puts "Title: https://…/help/slug" in the reply and links it) or the **Related articles**
  card (*Link only*). An article the customer can't open (Internal, another company's, or no plan
  feature) is flagged and can only be added to an internal note. Customers see only links they can
  open. Ticket descriptions and comments now make web addresses clickable (in-app ones open in
  place; `/help/…` links take staff to `/kb/…`).
- **Write an article from a resolved/closed ticket:** prefilled title, *Problem* (the description)
  and *Fix* (last public reply by staff); linked to the ticket on first save.
- **Article page (writers):** "Linked to N tickets" with the ones the reader can open.

**Verified:** backend suite (44) green; API e2e now **216 checks** (50 new: file type/size/permission
matrix incl. draft-only files, delete cleanup, link/unlink rules, customer filtering, Starter plan,
drafts linked from tickets, feedback + audit). Browser run: **27 checks** — image button and clipboard
paste upload into a new article, preview renders them, customer sees them once published;
suggestions → preview → "I still need help" keeps the form → "This solved it" raises nothing and is
audited; staff modal suggestions; picker adds a link to a reply and blocks an Internal article from a
public reply; Related articles for staff vs customer; comment links work for both sides;
article from a resolved ticket is prefilled and linked; linked tickets on the article page.

### Phase 7 — notifications + polish (2026-10-02) ✔

- **V6** `V6__notification_links.sql`: `notifications.link varchar(300)` — an in-app path the bell
  opens (ticket notifications keep `ticket_id`); `notifications_type_check` widened with
  `KB_PUBLISH_REQUESTED`, `KB_PUBLISH_DECIDED`. `NotificationService.notifyLink(...)`; titles and
  bodies are trimmed to their columns instead of failing.
- **`KbNotifier`** (bell always, email when the person keeps email notifications on, sent after
  commit; nobody is told about their own action):
  - **Request publish** → every active Admin and `MANAGE_KNOWLEDGE_BASE` holder
    (`UserRepository.findActiveHolders`, same rule as the PRIV_* authorities), opening the draft.
    Once per request — pressing it again while it waits doesn't ping them again.
  - **Published** → the engineer who asked, opening the live article ("readers now see version N").
  - **Declined** → the engineer, with the reason, opening their draft. A manager **archiving** or
    **deleting** a waiting draft tells them too (deleted: nothing to open).
- **"Needs publishing"** — `GET /api/kb/manage/requested-count` (one count; engineers get their
  scope). Sidebar badge on Knowledge Base for Admins / KB managers (a dot when collapsed), shared
  with the Manage tab; the KB home shows them "N articles are waiting for you to publish → Review
  them". Refreshes after publish/decline/request and when a publish notification arrives.
- **Recently updated / Most viewed** on the KB home and Help Centre — `GET /api/kb/articles?sort=popular`
  (`view_count`, all-time). A search is always best match first.
- Contract renewal reminders now open the company when clicked (they had no target before).

**Verified:** V6 applied from fresh, from a populated V5 database and from the legacy-baselined
one — identical `notifications` schema, existing rows kept. Backend suite (44) green; API e2e now
**262 checks** (46 new: who is and isn't notified — inactive admin, Default employee, the requester,
KB-manager engineer; no double ping; decline note; publish link; archive and delete outcomes; email
opt-out; scoped count; popular order, category, search ignores sort). Browser run: **30 checks** —
badge counts down as a manager publishes and declines, bell opens the draft / live article, the
engineer's Request publish reaches the admin, collapsed dot, Most viewed for staff and customers,
ticket notifications still open their ticket; the phase 4–6 browser runs still pass.

**Not in v1 (possible later):** a separate "Knowledge Base emails" switch in notification settings
(today it follows the email on/off switch), "Was this helpful?" votes, and views over a time window
rather than all-time.
