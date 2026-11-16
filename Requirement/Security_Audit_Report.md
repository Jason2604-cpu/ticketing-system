# Konoha HelpDesk Backend — Security Audit

Checked against:
- [astoj/vibe-security → web-app-security.md](https://github.com/astoj/vibe-security) (17 areas)
- [Replit security checklist](https://docs.replit.com/learn/security-checklist)

Legend: ✅ done in code · ⚙️ handled by config/ops (you must set it) · ⏳ not applicable yet / future work · ❌ was a real issue, now fixed

---

## Findings in the original code (all fixed)

| # | Issue | Severity | Fix |
|---|-------|----------|-----|
| 1 | `/api/auth/**` was `permitAll`, so `/api/auth/me` ran without auth and NPE'd (500 leak) | Medium | Only `POST /api/auth/login` is anonymous; `/me`, `/logout`, `/change-password` require auth; everything else requires a real role |
| 2 | Temp passwords were `"kanoha@" + Math.random()` (≈9,000 possibilities) and `PasswordGenerator` returned the constant `@konoha123` | **High** | `SecureRandom`, 14 chars, guaranteed to meet policy; length configurable |
| 3 | No rate limiting on login or API | High | Per-IP limiter (120/min API, 10/min login), configurable |
| 4 | No account lockout → unlimited password guessing per account | High | Lock after 5 failures for 15 min (configurable), HTTP 423 |
| 5 | Login timing revealed whether an email exists (bcrypt only ran when user found) | Low | Always runs a bcrypt compare against a dummy hash |
| 6 | "Account deactivated" message confirmed account existence | Low | Same generic "Invalid email or password"; detail goes to the audit log |
| 7 | No security headers (X-Frame-Options, nosniff, HSTS, CSP, Referrer-Policy) | Medium | All set in `SecurityConfig`, tunable in config |
| 8 | No catch-all exception handler → Spring's default error body could leak exception class/messages | Medium | `GlobalExceptionHandler` covers everything; unknown errors → generic 500, full detail logged server-side only; `server.error.include-*` all off |
| 9 | Controllers returned JPA entities (`User`) directly | Low | `UserResponse` / `CompanyResponse` DTOs; no entity leaves the API |
| 10 | `userCode` generated from `count()+1` → duplicate-key crash after any soft delete | Low (correctness) | Loops until an unused code is found |
| 11 | INVITED users could log in but the filter refused them → dead end; no way to change the temp password | Medium (correctness) | INVITED gets `ROLE_INVITED` (only `/me` + `/change-password`), becomes ACTIVE after changing password |
| 12 | JWT secret fallback was silently accepted anywhere | Medium | < 32 bytes → refuses to start; dev default → warning, and **refuses to start under the `prod` profile** |
| 13 | JWT carried the email claim (unnecessary PII in a token) | Low | Removed; only `sub` (id) + role |
| 14 | `CORS allowedHeaders="*"` with credentials | Low | Explicit header list, configurable |
| 15 | Filter registered twice (`@Component` + security chain) — harmless before, but would double-count a rate limiter | Low | Filters constructed in `SecurityConfig` only |
| 16 | Secure cookie flag was `true` by default with no dev profile active → cookie silently dropped on `http://localhost` | Correctness | `COOKIE_SECURE=false` added to your `.env` for local dev |

---

## vibe-security checklist

### 1. Authentication
- ✅ Trusted library — Spring Security + BCrypt + JJWT, no home-grown crypto.
- ✅ Authenticate every API request — `JwtCookieFilter` re-loads the user from DB on every request; `anyRequest().denyAll()` outside `/api/**`.
- ⏳ MFA — not implemented (no requirement yet). Natural next step: TOTP via a second factor on `/api/auth/login`.
- ⏳ Password reset with time-limited tokens — not implemented (needs an email service; the temp-password flow covers first login only).

### 2. Middleware protection
- ✅ `RateLimitFilter` → `JwtCookieFilter` → Spring Security authorization, in that order, for every `/api/**` request.
- ✅ Identity is validated from the DB per request, so deactivating/deleting a user takes effect immediately even with a live token.

### 3. RBAC
- ✅ Roles: ADMIN, ENGINEER, CUSTOMER_ADMIN, CUSTOMER (+ internal ROLE_INVITED).
- ✅ URL-level: `/api/**` requires a real role. Method-level: `@PreAuthorize` on every admin endpoint (`@EnableMethodSecurity`).
- ✅ Object-level (IDOR): `CurrentUser.requireCompanyAccess(companyId)` — customers can only read their own company/subscription; admins/engineers any.
- ✅ Feature-level: `CompanySubscriptionService.hasFeature(companyId, code)` / `requireFeature` — the doc's §8 rule, no hard-coded plan names anywhere.

### 4. Sensitive data handling
- ✅ Secrets in `.env`; `.env` is gitignored (verified with `git status --ignored`); `.env.example` committed with placeholders.
- ✅ Nothing secret is sent to the client: JWT is HttpOnly, password hash is `@JsonIgnore`, DTOs expose only listed fields.
- ✅ `temporaryPassword` in the create-user response is **off by default** and only enabled by `TEMP_PASSWORD_IN_RESPONSE=true` (dev). Turn it off before any shared environment.

### 5. Error handling
- ✅ Generic client messages; full exception logged server-side (`GlobalExceptionHandler`).
- ✅ `server.error.include-stacktrace/message/exception=never/false`.
- ✅ Empty `Server` header.

### 6. Input validation
- ✅ Bean Validation (`@Valid`) on every request body; `@Size` caps on every string field; regex on phone numbers; enum-typed params.
- ✅ Business-bounds validation for limits/SLA against `app.subscription.caps.*` (prevents absurd values).
- ✅ Unknown feature codes are rejected, not silently ignored.
- ✅ SQL injection: 100% JPA derived queries / parameterised JPQL — no string-built SQL.
- ✅ XSS: API returns JSON only; CSP `default-src 'none'` as belt-and-braces. (Frontend must still escape on render.)

### 7. Database security
- ✅ Trusted ORM (Hibernate via Spring Data JPA); no raw queries.
- ⚙️ Row-level security — enforced in the service layer (`requireCompanyAccess`), not in Postgres. Postgres RLS is possible later if you move to per-tenant DB roles.
- ⚙️ `ddl-auto=update` is fine for dev; set `DDL_AUTO=validate` in production and manage the schema with the SQL file or Flyway.
- ⚙️ Use a dedicated DB user with only the needed privileges (not `postgres` superuser) for the app.

### 8. Hosting
- ⚙️ Ops-level: managed platform, firewall, DDoS protection, auto-updates. Nothing in code blocks this.

### 9. Secure communications
- ✅ HSTS (1 year, includeSubDomains) emitted on HTTPS responses; `Secure` + `SameSite` cookies; `X-Frame-Options: DENY`; `X-Content-Type-Options: nosniff`; `Referrer-Policy`; CSP.
- ⚙️ Terminate TLS at your reverse proxy / platform; keep `COOKIE_SECURE=true` there (the default).

### 10. Logging & monitoring
- ✅ Dedicated `AUDIT` logger: login success/failure/lockout, logout, password change, user/company/plan/subscription create-update-delete, rate-limit hits. Never logs passwords or tokens.
- ⚙️ Route `AUDIT` to an append-only sink (file/SIEM) and alert on `LOGIN_LOCKED` / `RATE_LIMIT` spikes. Log tampering protection is an infra concern.

### 11. Security testing & audits
- ✅ Unit tests for password policy, generator, lockout, subscription validation.
- ⚙️ Add dependency scanning (`mvn org.owasp:dependency-check-maven:check`) and a SAST tool (SpotBugs/Semgrep) to CI. Pen-testing is a process item.

### 12. Backup & DR
- ⚙️ Postgres backups (`pg_dump` schedule or managed snapshots) + a tested restore. Not a code concern.

### 13. Dependency management
- ✅ Uses managed Spring Boot BOM versions; only two pinned deps (jjwt 0.12.6, springboot4-dotenv 5.1.0).
- ⚙️ Enable Dependabot/Renovate on the repo.

### 14. Rate limiting & anti-abuse
- ✅ Per-IP API and login limits; per-account lockout; `Retry-After` header on 429.
- ⚙️ Behind a proxy set `app.security.rate-limit.trust-forwarded-for=true`, otherwise all traffic looks like one IP.
- ⏳ CAPTCHA — add to the login form if bot traffic becomes a problem.
- ⚙️ Multi-instance deployments need a shared store (Redis) for the limiter; the filter is written so that's a drop-in swap.

### 15. Data privacy
- ✅ Minimal PII collected (name, email, phone); email removed from JWT.
- ⏳ Consent management / data export / deletion endpoints — soft delete exists; GDPR-style export/erasure is future work if you serve EU users.

### 16. Incident response
- ⚙️ Process item. The audit log gives you the forensic trail to support it.

### 17. IaC security
- ⏳ No infrastructure code in the repo yet.

---

## Replit checklist

| Item | Status |
|------|--------|
| HTTPS everywhere | ⚙️ at proxy/platform; HSTS + Secure cookies in code ✅ |
| Input validation & sanitisation | ✅ |
| No sensitive data in the browser | ✅ HttpOnly cookie; nothing in localStorage |
| CSRF protection | ✅ `SameSite=Lax` cookie (browsers omit it on cross-site POST/PUT/DELETE) + strict CORS origin list + JSON-only API. Set `same-site=Strict` in config for even tighter behaviour. |
| Never expose API keys in frontend | ✅ frontend has only the API base URL |
| Authentication fundamentals | ✅ BCrypt (salted), established libs |
| Authorization checks | ✅ URL + method + object level |
| API endpoint protection | ✅ deny-by-default |
| SQL injection prevention | ✅ ORM only |
| Basic security headers | ✅ |
| DDoS protection | ⚙️ CDN/platform; app-level rate limiting ✅ |
| Keep dependencies updated | ⚙️ |
| Proper error handling | ✅ |
| Secure cookies | ✅ HttpOnly + Secure + SameSite |
| File upload security | ⏳ no uploads yet; multipart size caps already set (5 MB) |
| Rate limiting | ✅ |

---

## What you must do before production (short list)

1. Set `JWT_SECRET` in `.env` to a random 32+ byte string (the app refuses to start under `prod` without it).
2. Run with `SPRING_PROFILES_ACTIVE=prod`, `COOKIE_SECURE=true`, `TEMP_PASSWORD_IN_RESPONSE=false`, `DDL_AUTO=validate`.
3. Put `CORS_ALLOWED_ORIGINS` to your real frontend origin only.
4. Terminate TLS in front of the app; if behind a proxy, set `app.security.rate-limit.trust-forwarded-for=true`.
5. Use a least-privilege Postgres user; schedule backups.
6. Wire an email service for temp passwords (the TODO in `UserService.createUser`).
