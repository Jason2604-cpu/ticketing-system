# Konoha HelpDesk

Ticketing / helpdesk system — Spring Boot 4 (Java 21) API, React 19 + Vite frontend,
PostgreSQL 17, Redis 7, nginx.

| Doc | For |
|---|---|
| [DEPLOYMENT.md](DEPLOYMENT.md) | Step-by-step Docker deployment (5 containers), HTTPS, backups, updates |
| [DEPENDENCIES.md](DEPENDENCIES.md) | Everything installed on the host, in the images, and in the builds |
| [scripts/README.md](scripts/README.md) | Local dev launchers (Windows) and server scripts |

**First sign-in** on a fresh install: `admin@admin.com` / `admin` — you are asked to choose a new
password before anything else works. No admin credentials live in `.env`.

Local development: `scripts\dev.bat` (backend on :8080, frontend on :5173).
