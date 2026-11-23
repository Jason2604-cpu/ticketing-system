# Deploying Konoha HelpDesk with Docker

Five containers, one published port, on one Ubuntu/Debian server:

```
                    ┌──────────────── web network ────────────────┐   ┌──── data network (internal) ────┐
browser ──:80/443──►│ nginx ──/api/*──► backend :8080 ────────────┼──►│ db     (PostgreSQL 17 :5432)    │
                    │   └────/*──────► frontend :80 (built SPA)   │   │ redis  (Redis 7 :6379)          │
                    └─────────────────────────────────────────────┘   └─────────────────────────────────┘
```

| Container | What it is | Built from |
|---|---|---|
| `nginx` | Edge proxy — the only thing reachable from outside. gzip, security headers, TLS | `nginx/default.conf` |
| `frontend` | The React app, compiled and served as static files | `frontend/Dockerfile` |
| `backend` | Spring Boot API. Runs DB migrations (Flyway) on start | `backend/Dockerfile` |
| `db` | PostgreSQL 17. Data in the `pgdata` volume | `postgres:17-alpine` |
| `redis` | Rate-limit and login-lockout counters (shared, survive restarts) | `redis:7-alpine` |

`db` and `redis` are on an internal network: only the backend can reach them, and they have no
internet access. Full dependency list: [DEPENDENCIES.md](DEPENDENCIES.md).

---

## Step 1 — Server

- Ubuntu 22.04/24.04 or Debian 12, **2 vCPU / 4 GB RAM / 20 GB disk** minimum.
- A user with `sudo`.
- Ports **80** (and **443** if you'll use HTTPS) open to your users. Nothing else needs to be open
  except SSH.
- Optional: a DNS name pointing at the server (needed for a real HTTPS certificate).

## Step 2 — Install Docker (host dependencies)

Copy `scripts/install-docker-host.sh` to the server (or do step 3 first — it's in the repo), then:

```bash
sudo bash scripts/install-docker-host.sh
```

It installs Docker Engine, the Compose v2 and Buildx plugins, git and openssl from Docker's
official repository, adds your user to the `docker` group and, if `ufw` is on, opens 80/443.
**Log out and back in** so the group change applies, then check:

```bash
docker run --rm hello-world
docker compose version        # v2.x
```

## Step 3 — Put the code on the server

**Option A — Git remote (recommended, makes updates a `git pull`).** Commit on your PC, push to a
private GitHub/GitLab repo, then on the server:

```bash
sudo mkdir -p /opt/konoha-helpdesk && sudo chown $USER: /opt/konoha-helpdesk
git clone <your-repo-url> /opt/konoha-helpdesk
cd /opt/konoha-helpdesk
```

**Option B — Copy an archive from Windows.** Commit first (only committed files are included — and
that also keeps `.env` secrets, `node_modules`, `target` and `media` out). In PowerShell:

```powershell
cd "C:\Users\user\Documents\Ticketing System"
git archive --format=tar.gz -o konoha.tar.gz HEAD
scp konoha.tar.gz <user>@<server>:/tmp/
```

On the server:

```bash
sudo mkdir -p /opt/konoha-helpdesk && sudo chown $USER: /opt/konoha-helpdesk
tar -xzf /tmp/konoha.tar.gz -C /opt/konoha-helpdesk
cd /opt/konoha-helpdesk
```

## Step 4 — Create `.env`

```bash
cp .env.docker.example .env
# three secrets (letters/digits only, so they're safe everywhere):
for v in POSTGRES_PASSWORD REDIS_PASSWORD JWT_SECRET; do
  sed -i "s|^$v=.*|$v=$(openssl rand -base64 48 | tr -d '\n=/+')|" .env
done
chmod 600 .env
nano .env
```

In the editor set at least:

| Key | Set to |
|---|---|
| `FRONTEND_BASE_URL` | the address users type, e.g. `http://192.168.1.50` or `https://helpdesk.example.com` (used in emailed links) |
| `CORS_ALLOWED_ORIGINS` | the same address |
| `HTTP_PORT` | `80` unless something else already uses it |
| `COOKIE_SECURE` | `false` over plain http, `true` once HTTPS is on (step 9) |
| `MAIL_*` | your SMTP server, and `MAIL_ENABLED=true`, if you want invitation / reset / SLA emails |

There are **no admin credentials** in `.env` — see step 7.

> `POSTGRES_PASSWORD` is only applied when the `pgdata` volume is created. Changing it later in
> `.env` does not change the database's password — see Troubleshooting.

## Step 5 — Build and start

> Until you finish step 7, anyone who can reach the server can sign in as `admin@admin.com` /
> `admin`. On an internet-facing server, keep port 80 closed in the firewall (or reach it through
> an SSH tunnel) until you have set the new password.

```bash
docker compose up -d --build
```

The first build takes a few minutes (Maven and npm download their libraries inside the build
containers; later builds reuse a cache). Compose starts things in order: `db` and `redis` →
`backend` (waits until both are healthy) → `frontend` → `nginx`.

On its first start the backend:

1. runs the Flyway migrations in `backend/src/main/resources/db/migration` (V1 creates all tables,
   V2 adds indexes and the password-change flag),
2. checks the entities match the tables (`DDL_AUTO=validate`),
3. seeds roles, features and the three subscription plans,
4. creates the **default admin** `admin@admin.com` / `admin`.

Follow it:

```bash
docker compose logs -f backend
# … Successfully applied 2 migrations …
# … Created the default ADMIN account admin@admin.com / admin …
# … Started KonohaHelpdeskApplication …
```

## Step 6 — Check every container

```bash
docker compose ps
```

All five should show `Up … (healthy)`. Individually:

```bash
curl -s http://localhost/nginx-health                                   # nginx      -> ok
docker compose exec frontend wget -qO- http://127.0.0.1/healthz         # frontend   -> ok
docker compose exec backend wget -qO- http://localhost:8080/actuator/health/liveness   # {"status":"UP"}
docker compose exec db pg_isready -U konoha                            # accepting connections
docker compose exec redis sh -c 'redis-cli --no-auth-warning -a "$REDIS_PASSWORD" ping'   # PONG
curl -s http://localhost/api/auth/me                                    # {"status":401,...} = API reachable
```

## Step 7 — First sign-in

1. Open `http://<server>` (your `FRONTEND_BASE_URL`).
2. Sign in with **`admin@admin.com`** / **`admin`**.
3. You land on **Set a new password**. Until you save one, the API refuses everything else
   (`403 Password change required`). The new password must have 10+ characters with upper and
   lower case, a digit and a symbol.
4. You're in as the system administrator. Saving the new password also signs out every other
   session of that account (true for every password change or reset, for every user).

Recommended once you're in: **Admin → Users → add yourself** as an Admin with your real email,
activate that account from the email link, sign in with it, then deactivate `admin@admin.com`.
(The default admin is only re-created if the database ever has *no* admin at all.)

## Step 8 — (Optional) Bring your existing local data

Do this **before the first `docker compose up`** (or after `docker compose down -v`, which
deletes the Docker database), so the backend meets your data instead of an empty database.

Your local PostgreSQL must not be newer than the container's (17): check `pg_dump --version`. If
it says 18, change `postgres:17-alpine` to `postgres:18-alpine` in `docker-compose.yml` first.

On your PC (PowerShell, local Postgres):

```powershell
pg_dump -U postgres -h localhost -d konoha_helpdesk -Fc -f konoha.dump
cd "C:\Users\user\Documents\Ticketing System\backend"
tar -czf media.tar.gz -C media .
scp konoha.dump media.tar.gz <user>@<server>:/tmp/
```

On the server:

```bash
cd /opt/konoha-helpdesk
docker compose up -d db                                  # database only
docker compose cp /tmp/konoha.dump db:/tmp/konoha.dump
docker compose exec db pg_restore -U konoha -d konoha_helpdesk --no-owner --no-privileges /tmp/konoha.dump
docker compose up -d --build                             # everything else
docker compose cp /tmp/media.tar.gz backend:/tmp/media.tar.gz
docker compose exec -u root backend sh -c 'tar -xzf /tmp/media.tar.gz -C /app/media && chown -R app:app /app/media && rm /tmp/media.tar.gz'
```

Flyway sees a database without its history table, records it as V1 ("baseline") and applies V2
only. Your existing admin accounts keep working, so no default admin is created.

## Step 9 — HTTPS

With your certificate files (`fullchain.pem`, `privkey.pem`):

```bash
cp /path/to/fullchain.pem /path/to/privkey.pem nginx/certs/
cp nginx/default.conf nginx/default.conf.http-only
cp nginx/https.conf.example nginx/default.conf
sed -i 's/helpdesk.example.com/<your.domain>/' nginx/default.conf
```

With Let's Encrypt instead (DNS must already point at the server):

```bash
sudo apt-get install -y certbot
docker compose stop nginx
sudo certbot certonly --standalone -d <your.domain>
sudo cp -L /etc/letsencrypt/live/<your.domain>/{fullchain,privkey}.pem nginx/certs/
sudo chown $USER: nginx/certs/*.pem
# then the cp/sed lines above
```

Then in `docker-compose.yml` un-comment the `"${HTTPS_PORT:-443}:443"` line under `nginx`, and in
`.env` set `COOKIE_SECURE=true`, `FRONTEND_BASE_URL=https://<your.domain>`,
`CORS_ALLOWED_ORIGINS=https://<your.domain>`:

```bash
docker compose up -d        # recreates nginx and backend with the new settings
```

Let's Encrypt certificates last 90 days; renew with
`docker compose stop nginx && sudo certbot renew && sudo cp -L … && docker compose start nginx`
(a monthly cron job).

---

## Day-to-day operations

### Deploy a new version

If the server folder is a git checkout, one command does all of the steps below (back up, pull,
rebuild, restart) and does nothing when there is nothing new:

```bash
bash /opt/konoha-helpdesk/scripts/update.sh
```

The manual steps:

```bash
cd /opt/konoha-helpdesk
bash scripts/backup.sh            # always back up first
git pull                          # or extract a new archive (step 3, option B)
docker compose build --pull       # rebuild, also refreshing the base images (security patches)
docker compose up -d              # restarts only what changed (nginx follows the backend)
docker compose logs -f backend    # new migrations (V3, V4…) are applied on start
docker image prune -f             # remove old image layers
```

### Deploy from Docker Hub images (no build on the server)

Instead of copying the source to every server and building there, publish the two app images
once and let servers pull them.

**1. Publish a version (on your PC or any machine with the source and Docker):**

```bash
docker login                                              # once
bash scripts/release.sh youruser/konoha-helpdesk 1.0.0    # Windows: scripts\release.bat youruser/konoha-helpdesk 1.0.0
```

Both images go into one Docker Hub repository as tags `backend-1.0.0` and `frontend-1.0.0`
(plus `backend-latest` / `frontend-latest`). Create the repository on Docker Hub first and mark
it **private** if the code should not be public.

**2. On a server** (steps 1, 2 and 4 above still apply; step 3 shrinks to copying three things:
`docker-compose.prod.yml`, `.env`, and the `nginx/` folder):

```bash
# in .env:
#   DOCKER_REPO=youruser/konoha-helpdesk
#   APP_VERSION=1.0.0
#   COMPOSE_FILE=docker-compose.prod.yml      # so plain `docker compose ...` uses the prod file
docker login                                  # once, only if the repository is private
docker compose pull
docker compose up -d
```

**Update:** release a new version (`1.1.0`), change `APP_VERSION` in the server's `.env`, back up
(`bash scripts/backup.sh`), then `docker compose pull && docker compose up -d`.
**Roll back:** set `APP_VERSION` back to the previous version and run the same two commands.
(A rollback does not undo database migrations the newer version applied; restore the backup if
the old version cannot start against the newer schema.)

The images contain no secrets and no data: `.env` is per server, and tickets/attachments live in
the Docker volumes (`pgdata`, `media`, `redisdata`), moved with backup and restore.

### Logs

```bash
docker compose logs -f --tail=200 backend      # or nginx / frontend / db / redis
docker compose logs db | grep duration         # queries slower than 500 ms
```

Each container keeps at most 3 × 10 MB of logs (`x-logging` in `docker-compose.yml`).

### Backup and restore

```bash
bash scripts/backup.sh     # -> backups/<timestamp>/db.dump + media.tar.gz, keeps the newest 14
```

Daily at 02:15 (`crontab -e`):

```
15 2 * * * cd /opt/konoha-helpdesk && mkdir -p backups && bash scripts/backup.sh >> backups/backup.log 2>&1
```

Copy `backups/` off the server as well. Restore a backup:

```bash
docker compose stop backend nginx
docker compose cp backups/<stamp>/db.dump db:/tmp/db.dump
docker compose exec db pg_restore -U konoha -d konoha_helpdesk --clean --if-exists --no-owner /tmp/db.dump
docker compose cp backups/<stamp>/media.tar.gz backend:/tmp/media.tar.gz
docker compose start backend
docker compose exec -u root backend sh -c 'tar -xzf /tmp/media.tar.gz -C /app/media && chown -R app:app /app/media'
docker compose start nginx
```

### Database console

```bash
docker compose exec db psql -U konoha -d konoha_helpdesk
```

### Changing the database schema later

Hibernate no longer changes tables. When an entity changes, add a migration next to the others:

```
backend/src/main/resources/db/migration/V3__add_ticket_due_reason.sql
```

```sql
ALTER TABLE tickets ADD COLUMN due_reason varchar(200);
```

Rules: never edit a `V` file that has already run anywhere; one change = one new file with the
next number. Adding a value to a Java enum stored as text also needs a migration, because the
column has a `CHECK (… IN (…))` constraint:

```sql
ALTER TABLE tickets DROP CONSTRAINT tickets_status_check;
ALTER TABLE tickets ADD CONSTRAINT tickets_status_check
  CHECK (status IN ('OPEN','IN_PROGRESS','ON_HOLD','RESOLVED','CLOSED'));
```

If the backend then refuses to start with `Schema-validation: missing column …`, the entity and
the migrations disagree — add the missing migration.

### Local development on Windows (unchanged workflow)

`scripts\dev.bat` still works. The first local start runs Flyway against your local database: it
is recorded as V1 and V2 is applied (adds `users.must_change_password` and the new indexes).
Local runs keep `APP_STORE_TYPE=memory`, so no Redis is needed on your PC. The
`BOOTSTRAP_ADMIN_*` lines in `backend\.env` are no longer read.

### Scaling

One backend container is right for a helpdesk. The rate-limit and lockout counters are already
shared through Redis, so a second backend is possible later — but the scheduled jobs (SLA sweep,
contract sweep, audit prune) must run on exactly one of them, so it has to be a separate service
in `docker-compose.yml` with `SCHEDULING_ENABLED=false`, not `--scale backend=2`.

---

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `backend` stays `unhealthy` / restarts | `docker compose logs backend`. Usually one of the rows below. |
| `password authentication failed for user "konoha"` | `POSTGRES_PASSWORD` in `.env` was changed after the `pgdata` volume was created. Put the old value back, or change it inside Postgres: `docker compose exec db psql -U konoha -d konoha_helpdesk -c "ALTER USER konoha PASSWORD '<new>'"`. |
| `Schema-validation: missing column/table …` | Database older than the code. Add the missing migration (see above). One-off rescue for an imported local DB: start once with `DDL_AUTO=update` in `.env`, then set it back to `validate`. |
| `app.security.jwt.secret must be at least 32 bytes` / `must not be blank` | `JWT_SECRET` short or empty — re-run the step 4 loop. |
| `NOAUTH` / Redis errors in the backend log | `REDIS_PASSWORD` changed while the `redis` container kept the old one: `docker compose up -d --force-recreate redis backend`. |
| Browser shows `502 Bad Gateway` | The backend isn't up (yet) — check its logs. If it is healthy, `docker compose restart nginx`. |
| Signs in, then bounced back to the login page | `COOKIE_SECURE=true` while using `http://`. Set it `false` (or finish HTTPS), `docker compose up -d`. |
| Everything answers `403 Password change required` | Expected until the default admin chooses a new password (step 7). |
| `port is already allocated` on start | Something else uses port 80: change `HTTP_PORT` in `.env`. |
| Lost the admin password, no email configured | Reset `admin@admin.com` back to `admin` (with the forced change):<br>`docker compose exec -T db psql -U konoha -d konoha_helpdesk <<'SQL'`<br>`UPDATE users SET password = '$2a$10$lNsjCRfZgY/ZurY3aU6uyuMqlVqnl7/GCuzxwHT53L0Sa.y9qjL1i', must_change_password = true, status = 'ACTIVE' WHERE email = 'admin@admin.com';`<br>`SQL`<br>A lock from failed attempts clears after 15 minutes. |
| Build fails downloading packages | The server needs outbound HTTPS to Docker Hub, Maven Central and the npm registry while building. |

## Reset everything (destroys all data)

```bash
docker compose down -v      # -v deletes pgdata, media and redisdata
```
