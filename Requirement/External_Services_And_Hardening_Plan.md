# External Services & Hardening — Implementation Plan

Status: **planned, not built**. Written 2026-09-10.

Target deployment: **single Docker host** (Docker Compose), **all self-hosted, open-source only**.
This plan answers "which external services should this app gain, and in what order" — grounded in
what the code actually does today, not in generic best practice.

---

## 0. Decisions taken before writing this

| Question | Decision |
|---|---|
| Deployment | Docker Compose, single host, one app instance |
| Hosting philosophy | All self-hosted, OSS only |
| JWT revocation | **`tokenVersion` column on `users`** — no Valkey dependency for logout correctness |
| Polling → realtime | **Tune polling now**, SSE as a later phase |
| Auth-path query load | **Fix polling first, then re-measure** — do not cache an authorization decision until it is proven still hot |
| Redis vs Valkey | **Valkey** — Redis relicensed away from OSI-approved terms in 2024; Valkey is the BSD-licensed fork and a drop-in replacement |

---

## 1. Findings this plan responds to

Evidence gathered from the codebase on 2026-09-10.

### 1.1 Correction to an earlier assumption

`JwtCookieFilter.doFilterInternal` **re-loads the user from the database on every request** and
rebuilds authorities from `user_roles` each time. Therefore:

- Deactivation, soft-delete and status changes take effect **immediately**. ✅
- Role and custom-privilege changes take effect **immediately**. ✅

Any claim that "JWT changes take up to 24h to apply" is wrong for this codebase. The cost of that
correctness is two queries per request (§1.4).

### 1.2 What is genuinely not revocable

| Gap | Impact |
|---|---|
| `AuthController.logout` only clears the cookie | A copied token keeps working for the rest of `JWT_EXPIRATION_MS` (24h) |
| Password change / reset does not invalidate existing sessions | **The important one.** The usual reason to reset a password is suspected compromise — and the attacker's live session survives it |
| No per-device revocation | "Log out my other devices" is impossible |

### 1.3 In-memory state that a container restart wipes

| Component | Problem |
|---|---|
| `security/RateLimitFilter` | `ConcurrentHashMap` per-IP fixed window. Every `docker compose restart` or crash resets every window. The file's own javadoc already flags this. |
| `security/LoginAttemptService` | Same — and worse, because the 5-failure / 15-minute lockout **is** the brute-force defence. A restart clears every lockout. |

### 1.4 Query load

- **Auth path:** every request runs `findByIdAndStatusNot` + `findByUserId` before reaching a
  controller. Correct, but it is the hottest path in the app.
- **Amplified by polling:** `frontend/src/App.tsx:37` sets a **global** `refetchInterval: 15_000`
  on *every* TanStack query. `TicketDetailPage` polls at 4s; `NotificationBell` at 10s. Most
  refetches return unchanged data, and each one pays the auth-path cost on top of its own query.
- **`FeatureService.allFeatures()`** hits the DB on every call and is called once per plan inside
  `PlanService.list()` (already recorded as outstanding in `project_audit_cleanup.md`).

### 1.5 Ticket search cannot use an index

`TicketService.list()` builds `cb.like(cb.lower(root.get("title")), "%" + query + "%")`. A leading
wildcard defeats every B-tree index, so this is a sequential scan over every ticket in scope — on a
query the tickets page re-fires every 15 seconds.

### 1.6 Schema management

`spring.jpa.hibernate.ddl-auto=${DDL_AUTO:update}` with a hand-maintained
`src/main/resources/db/subscription_plan_schema.sql`. `update` never drops or safely alters a
column, offers no rollback, and silently diverges between a dev machine and a container whose
volume outlives the image. **This is the highest-risk item in the whole project.**

### 1.7 Uploads are unscanned

`TicketService.attach()` accepts PDF / CSV / TXT / images up to 20MB from customer users.
`MediaController` serves images back with `Content-Disposition: inline`. Content type is validated
against real bytes (good), size and storage quota are enforced (good), but nothing scans content.

### 1.8 Operational gaps

- No TLS terminator. `hsts-enabled=true` and `cookie.secure=true` are set but meaningless until
  something actually serves HTTPS.
- `trust-forwarded-for=false` is correct today, but must flip to `true` once a proxy exists —
  otherwise the rate limiter sees every request as coming from the proxy's single IP.
- No `spring-boot-starter-actuator`, no metrics, no error tracking. Logs go to `backend/run.log`.
- `MailDispatcher.deliver` is `@Async` fire-and-forget: a failed send is logged and lost.
- No backup of Postgres or of `app.media.base-path`.
- Three `@Scheduled` sweeps (`SlaMonitor`, `ContractMonitor`, `AuditRetentionMonitor`) plus
  `AccountTokenService.purgeExpired` run unguarded — fine on one instance, duplicated on two.

---

## 2. What this plan deliberately does NOT add

Saying no is most of the value here.

| Service | Why not |
|---|---|
| **Keycloak** | Would replace a large amount of working, audited code (JWT issuance, RBAC, activation, reset flows) to solve a problem you do not have. Revisit only if a customer contractually demands SSO/SAML. |
| **RabbitMQ / Kafka** | The only queue-shaped need is retrying failed email. A `mail_outbox` table plus the scheduler you already run solves it with zero new infrastructure. A broker for one email queue is over-engineering. |
| **MinIO** | Media on local disk is a bind mount in Docker and works correctly. MinIO earns its place at >1 replica, or when offsite/versioned object storage is wanted. Until then a volume + `restic` is strictly better. |
| **Meilisearch / Typesense / Elasticsearch** | A `pg_trgm` GIN index (§3.5) fixes the actual problem for one migration and no new container. Reconsider only for typo tolerance or cross-entity search at much larger scale. |
| **ShedLock** | Only needed at >1 replica. Noted in §5.4 so it is not forgotten, but it is not phase 1 or 2 work — and when it is needed, it can use Postgres rather than Valkey. |

---

## 3. Phase 1 — no new services

Everything here runs against the stack you already have. Do this first.

### 3.1 Flyway

Add `org.flywaydb:flyway-core` + `flyway-database-postgresql`. Baseline the current schema as
`V1__baseline.sql` (generate from the live DB, not by hand), set `spring.flyway.baseline-on-migrate=true`,
then move `DDL_AUTO` to `validate` in every environment.

Every later item in this plan that touches the schema becomes a numbered migration.

**Rationale:** `ddl-auto=update` is survivable on a laptop and dangerous against a Docker volume
that outlives the image. This unblocks safe deployment more than any external service does.

### 3.2 `tokenVersion` — close the revocation gaps

| Touchpoint | Change |
|---|---|
| `model/user/User` | New `int tokenVersion` column, default 0 (Flyway migration) |
| `security/JwtService.generateToken` | Add `.claim("tv", user.getTokenVersion())` |
| `security/JwtCookieFilter` | After loading the user, reject when `claims.get("tv") != user.getTokenVersion()`. **No extra query** — the user row is already loaded. |
| `service/auth/AccountService` | Increment on password reset and password change |
| `controller/auth/AuthController.logout` | Increment |
| `service/user/UserService` | Optionally increment on `setStatus` / `softDelete` (belt and braces — status is already checked live) |

Revokes **all** of a user's sessions at once; per-device revocation is out of scope and not needed.

**Verify:** log in on two browsers, reset the password in one, confirm the other is rejected on its
next request.

### 3.3 Move lockout state to Postgres

Add `failedLoginAttempts` and `lockedUntil` to `users`. Rewrite `LoginAttemptService` against the
repository, keeping its current interface (`isLocked` / `recordFailure` / `recordSuccess` /
`getLockMinutes`) so `AuthService` is untouched.

The lockout then survives restarts and does not depend on Valkey being up. Rate limiting stays
in-memory until phase 2 — it degrades gracefully, whereas lockout does not.

### 3.4 Tune the polling

In `frontend/src/App.tsx:37`, change the default to `refetchInterval: false`. Then opt in
explicitly, per query:

| Query | Interval |
|---|---|
| tickets list, `tickets/counts` | 15s |
| `TicketDetailPage` | 4s (keep) |
| `NotificationBell` | 10s (keep) |
| everything else (plans, roles, features, templates, audit, analytics, reports, companies, users) | off — invalidate on mutation instead |

Several pages already set `refetchInterval: false` individually; this inverts the default so new
pages are quiet unless someone opts them in. Expect a large drop in both API requests and
auth-path queries — which is why §5.2 is deliberately deferred until after this lands.

### 3.5 Make ticket search indexable

Flyway migration:

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX ix_tickets_title_trgm ON tickets USING gin (lower(title) gin_trgm_ops);
CREATE INDEX ix_tickets_desc_trgm  ON tickets USING gin (lower(description) gin_trgm_ops);
```

The existing `cb.like(cb.lower(...), "%q%")` predicate in `TicketService.list()` then uses the GIN
index instead of scanning. No Java change required for the index to take effect — but confirm with
`EXPLAIN ANALYZE` rather than assuming.

### 3.6 Actuator

Add `spring-boot-starter-actuator`. Expose **only** `health` and `info` publicly; keep `metrics`
and `prometheus` bound to the internal Docker network. Gives Compose a real `healthcheck` target
immediately, and is the precondition for §5.3.

---

## 4. Phase 2 — the Docker stack

Three containers, each earning its place.

### 4.1 Caddy — reverse proxy and TLS

Serves the built React app as static files, proxies `/api/*` to the backend, terminates HTTPS with
automatic certificate management (~10 lines of Caddyfile).

Follow-up config once it is in place:

- `COOKIE_SECURE=true`, `CORS_ALLOWED_ORIGINS` = the real origin
- `app.security.rate-limit.trust-forwarded-for=true` — **required**, or the limiter sees only the
  proxy's IP and one noisy client rate-limits everybody
- `FRONTEND_BASE_URL` = the real origin, so activation and reset links are correct

### 4.2 Valkey — three uses, in this order

1. **Rate limiting.** Replace the two `ConcurrentHashMap`s in `RateLimitFilter` with Bucket4j on
   Valkey, or a plain `INCR` + `EXPIRE` per key. The filter's shape does not change. If Valkey is
   unreachable, **fail open** and log loudly — rate limiting is a mitigation, and losing it must not
   take the app down. (Lockout, the actual defence, is in Postgres from §3.3.)
2. **Reference-data cache.** `@Cacheable` with `spring-boot-starter-data-redis` +
   `spring-boot-starter-cache` on `FeatureService.allFeatures()`, `PlanService.list()`, roles and
   templates. Evict on the corresponding admin mutations. This kills the `PlanService` N+1 recorded
   in `project_audit_cleanup.md` without restructuring the queries.
3. **Not yet:** SSE fan-out (§5.1) and auth-path caching (§5.2).

**Explicitly not used for:** JWT revocation (§3.2 handles it), or session storage (there are no
server-side sessions).

### 4.3 ClamAV — scan uploads

`clamav/clamav` container; call `clamd` over TCP from `MediaStorageService` before the file is
written, or scan asynchronously and quarantine on a hit.

Reject with the existing `BusinessRuleException` → 400 path so the frontend needs no new handling.
Add `app.media.scan-enabled` (default `false`) so local development without the container still
works.

**Judgement call:** worth it if real customers upload attachments; skippable for a coursework demo.

### 4.4 Backups

Not a service so much as a cron container, and the highest regret-if-missing item on this page:

- `pg_dump` nightly to a volume, plus `restic` to an offsite target
- Same for `app.media.base-path`
- **Test a restore.** An untested backup is not a backup.

---

## 5. Phase 3 — conditional / later

### 5.1 SSE to replace polling

`SseEmitter` endpoint (`GET /api/events`), emitters held per user id. Push on ticket create,
status change, assignment, comment and notification create. Frontend uses `EventSource` and calls
`queryClient.invalidateQueries` on each message; polling stays as the fallback for a dropped
connection.

Removes the idle load entirely and cuts update latency from up to 4s to ~50ms. Emitters live in one
process's memory — at >1 replica this needs Valkey pub/sub to fan out, which is the point at which
that dependency becomes structural rather than optional.

### 5.2 Auth-path caching — **measure before building**

Do not start this until §3.4 has shipped and the load has been re-measured. If the two queries per
request are still hot afterwards, the preferred shape is: cache the `user_roles` lookup only, and
leave the user-row/status fetch live so deactivation stays instant.

Caching the full authority list removes both queries but caches an *authorization decision* —
every permission-changing path must evict correctly, and anything missed leaves stale access until
the TTL expires. That risk is only worth taking against a measured problem.

### 5.3 Observability

`spring-boot-starter-actuator` (§3.6) → Prometheus scrape → Grafana dashboards → Loki for logs.
Add incrementally; even Prometheus alone answers "why was it slow at 3pm", which is currently
unanswerable.

**GlitchTip** (OSS, Sentry-API-compatible) is a cheap addition for exception tracking — relevant
because `MailDispatcher` currently catches and logs failures that nobody reads.

### 5.4 Multi-replica prerequisites

Recorded so they are not discovered the hard way. Before ever running two app containers:

- **ShedLock** on `SlaMonitor`, `ContractMonitor`, `AuditRetentionMonitor`,
  `AccountTokenService.purgeExpired` — otherwise every replica sweeps, and breach emails duplicate.
  Can use Postgres as the lock store; no Valkey needed.
- Media must move to shared storage (MinIO or a shared volume).
- SSE needs Valkey pub/sub (§5.1).

### 5.5 Mail outbox

Replace fire-and-forget `@Async` with a `mail_outbox` table: enqueue in the same transaction as the
business change, send from a scheduled worker with retry and backoff. Solves lost mail with no
broker. Low priority unless dropped notifications are actually being observed.

---

## 6. New config keys

Following the existing convention — tunables in `app-config.properties` bound to `AppProperties`,
secrets in `.env`.

```properties
# Valkey (phase 2)
app.cache.enabled=${CACHE_ENABLED:false}
app.cache.reference-ttl-seconds=300
app.security.rate-limit.backend=${RATE_LIMIT_BACKEND:memory}   # memory | valkey

# Upload scanning (phase 2)
app.media.scan-enabled=${CLAMAV_ENABLED:false}
app.media.scan-host=${CLAMAV_HOST:clamav}
app.media.scan-port=${CLAMAV_PORT:3310}
```

`.env` gains `REDIS_HOST` / `REDIS_PORT` (Valkey speaks the Redis protocol, so Spring's
`spring.data.redis.*` keys are used unchanged).

---

## 7. Order of work

| # | Item | Phase | New service | Effort | Value |
|---|---|---|---|---|---|
| 1 | Flyway + baseline | 1 | — | M | **Highest** |
| 2 | `tokenVersion` | 1 | — | S | High (security) |
| 3 | Lockout → Postgres | 1 | — | S | High (security) |
| 4 | Polling default off | 1 | — | S | High (perf) |
| 5 | `pg_trgm` index | 1 | — | S | High (perf) |
| 6 | Actuator | 1 | — | S | Medium |
| 7 | Caddy + TLS | 2 | Caddy | M | High |
| 8 | Valkey rate limit | 2 | Valkey | M | Medium |
| 9 | Valkey ref cache | 2 | — | S | Medium |
| 10 | ClamAV | 2 | ClamAV | M | Medium |
| 11 | Backups + restore test | 2 | cron container | M | **Highest** |
| 12 | SSE | 3 | — | L | Medium |
| 13 | Auth cache (if measured) | 3 | — | M | Conditional |
| 14 | Prometheus / Grafana / Loki | 3 | 3 containers | L | Medium |

Phase 1 is six items, needs no new infrastructure, and covers both real security gaps plus the two
worst performance problems. **If only one thing gets done, do #1.**

---

## 8. Open questions

- Is ClamAV worth a container for this deployment, or is byte-level content-type validation
  considered sufficient?
- Should `docker-compose.yml` + `Caddyfile` be written now, or after phase 1 lands? (Deferred by
  decision — plan doc only for now.)
- Does anything need to survive the host being lost, i.e. is an offsite `restic` target available?

## 9. Verification constraints (carried from project memory)

The sandbox cannot reach Maven Central and the device VM has a JRE only — new dependencies must be
resolved by running `mvnw.cmd -q compile` in Windows cmd. Frontend changes verify with
`tsc -b --force` on the device; `oxlint` and `vite build` need Windows.
