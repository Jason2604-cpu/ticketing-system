# Dependencies — Konoha HelpDesk

Updated 2026-09-30. Everything the system needs, grouped by **where it gets installed**.
Only section 1 is installed by hand (one script); sections 2–4 are pulled or built
automatically by `docker compose up -d --build`.

| Layer | Installed by | File that pins it |
|---|---|---|
| 1. Docker host (Ubuntu/Debian) | `sudo bash scripts/install-docker-host.sh` | that script |
| 2. Container images | `docker compose` (pulled from Docker Hub) | `docker-compose.yml`, `*/Dockerfile` |
| 3. Backend libraries | Maven, inside the backend build container | `backend/pom.xml` |
| 4. Frontend libraries | npm, inside the frontend build container | `frontend/package-lock.json` |

---

## 1. Docker host — `scripts/install-docker-host.sh`

| Package | Source | Why |
|---|---|---|
| `docker-ce`, `docker-ce-cli`, `containerd.io` | download.docker.com apt repo | Docker Engine (use Docker's repo, not the distro's `docker.io`) |
| `docker-compose-plugin` | same | `docker compose` v2 — runs `docker-compose.yml` |
| `docker-buildx-plugin` | same | BuildKit — the Dockerfiles use `RUN --mount=type=cache` |
| `git` | apt | fetch / update the code |
| `openssl` | apt | generate the secrets in `.env` |
| `curl`, `ca-certificates`, `gnupg` | apt | add Docker's signed repository |

Minimum: Docker Engine **24+**, Compose **v2.20+**. Hardware for a small team: 2 vCPU, 4 GB RAM,
20 GB disk (+ whatever attachments you expect). The host does **not** need Java, Maven, Node,
npm, PostgreSQL, Redis or nginx installed — they all run in containers.

Outbound access the host needs while building: `registry-1.docker.io` (images),
`repo.maven.apache.org` (backend libraries), `registry.npmjs.org` (frontend libraries).

---

## 2. Container images

| Service | Image | Role | Published port |
|---|---|---|---|
| `nginx` | `nginx:1.27-alpine` | Edge proxy: `/api/*` → backend, `/*` → frontend, gzip, TLS | `HTTP_PORT` (80), optional 443 |
| `frontend` | built from `frontend/Dockerfile` → `nginx:1.27-alpine` | Serves the compiled React app | none |
| `backend` | built from `backend/Dockerfile` → `eclipse-temurin:21-jre-alpine` | Spring Boot API | none |
| `db` | `postgres:17-alpine` | PostgreSQL 17 | none (internal network) |
| `redis` | `redis:7-alpine` | Rate-limit + login-lockout counters | none (internal network) |

Build-only images: `maven:3.9-eclipse-temurin-21` (compiles the backend),
`node:22-alpine` (builds the frontend). They are not part of the running system.

| Volume | Holds | Back it up? |
|---|---|---|
| `pgdata` | the database | **yes** — `scripts/backup.sh` |
| `media` | uploaded photos, logos, attachments | **yes** — `scripts/backup.sh` |
| `redisdata` | Redis AOF (counters/locks, all short-lived) | no |

---

## 3. Backend — `backend/pom.xml` (Java 21, Maven)

Parent BOM `org.springframework.boot:spring-boot-starter-parent:`**`4.0.8`** manages every version
not written out below. (4.0.8 is the newest 4.0.x patch; the 4.0 line is supported until
**2026-12-31** — move to 4.1.x before then.)

| Artifact | Version | Purpose |
|---|---|---|
| `spring-boot-starter-webmvc` | BOM | REST controllers, embedded Tomcat, Jackson |
| `spring-boot-starter-security` | BOM | Filter chain, `@PreAuthorize`, BCrypt |
| `spring-boot-starter-data-jpa` | BOM | Hibernate ORM, Spring Data repositories, HikariCP |
| `spring-boot-starter-validation` | BOM | Bean Validation on request DTOs |
| `spring-boot-starter-mail` | BOM | SMTP (activation, reset, SLA, ticket mail) |
| `spring-boot-starter-actuator` | BOM | `/actuator/health` for the container healthcheck |
| `spring-boot-starter-flyway` | BOM | **new** — runs `db/migration/V*.sql` on startup |
| `org.flywaydb:flyway-database-postgresql` | BOM | **new** — Flyway's PostgreSQL support |
| `spring-boot-starter-data-redis` | BOM | **new** — Lettuce client for the Redis stores |
| `org.postgresql:postgresql` | BOM (runtime) | JDBC driver |
| `io.jsonwebtoken:jjwt-api` / `-impl` / `-jackson` | 0.12.6 | JWT auth cookie |
| `me.paulschwarz:springboot4-dotenv` | 5.1.0 | Reads `backend/.env` for local dev (containers use env vars) |
| `spring-boot-devtools` | BOM (optional) | Local dev only — stripped from the packaged jar |
| test: `spring-boot-starter-{data-jpa,security,webmvc}-test` | BOM | Test slices |

Print the exact resolved versions (e.g. for an audit):

```bash
cd backend && ./mvnw -q dependency:list -DincludeScope=runtime
```

---

## 4. Frontend — `frontend/package.json` / `package-lock.json` (Node 22)

`npm ci` in the build container installs exactly what the lockfile says (`npm audit`:
**0 vulnerabilities** on 2026-10-01). The Knowledge Base added `react-markdown`, `remark-gfm` and
`diff` — about 100 small packages, nearly all from the unified/remark Markdown toolchain. For local
development run `npm install` in `frontend/` once after pulling them. The rich-text article editor (2026-11-16) added the Tiptap packages and `marked` (`npm audit`: 0 vulnerabilities) — run `npm install` once more.

| Package | Installed | Kind | Purpose |
|---|---|---|---|
| `react`, `react-dom` | 19.2.8 | runtime | UI |
| `react-router-dom` | 7.18.3 | runtime | Routing (lazy-loaded pages) |
| `@tanstack/react-query` | 5.102.8 | runtime | Server-state cache / polling |
| `recharts` | 3.10.1 | runtime | Charts (own chunk, loaded only on dashboard/report pages) |
| `lucide-react` | 1.40.0 | runtime | Icons |
| `clsx` | 2.1.1 | runtime | className helper |
| `react-markdown` | 10.1.0 | runtime | Knowledge Base article rendering — no raw HTML (own chunk, loaded only on KB pages) |
| `remark-gfm` | 4.0.1 | runtime | Tables, task lists and strikethrough in articles |
| `diff` | 9.0.0 | runtime | "What changed" between article versions |
| `@tiptap/react`, `@tiptap/core`, `@tiptap/pm`, `@tiptap/starter-kit`, `@tiptap/extension-image`, `@tiptap/extension-table`, `@tiptap/extension-list`, `@tiptap/extensions`, `@tiptap/markdown` | 3.34.0 | runtime | **new (2026-11-16)** — rich-text article editor (photos and attached files shown inline); only in the KB editor chunk |
| `marked` | 17.0.6 | runtime | **new** — Markdown parser the editor uses to load an article (articles are still stored as Markdown) |
| `vite` | 8.2.2 | build | Bundler |
| `@vitejs/plugin-react` | 6.1.1 | build | React support for Vite |
| `typescript` | 6.0.3 | build | Type checking (`tsc -b`) |
| `tailwindcss`, `@tailwindcss/vite` | 4.3.3 | build | CSS |
| `oxlint` | 1.81.0 | dev | Linter |
| `@types/node`, `@types/react`, `@types/react-dom` | 24.13.3 / 19.2.18 / 19.2.7 | dev | Type definitions |

```bash
cd frontend && npm outdated && npm audit
```

---

## Maintenance

| When | What |
|---|---|
| Weekly | `npm audit` in `frontend/` |
| Monthly | `docker compose pull && docker compose build --pull && docker compose up -d` (base-image security patches) |
| Quarterly | Bump the Spring Boot parent to the newest patch of the line in use |
| Before **2026-12-31** | Move off Spring Boot 4.0.x (end of support) to 4.1.x |
