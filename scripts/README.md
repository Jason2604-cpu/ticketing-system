# Dev scripts

- `dev.bat` — starts the backend (`mvnw.cmd spring-boot:run`, port 8080) and the
  frontend (`npm run dev`, port 5173) each in their own window. Double-click it,
  or run it from a terminal at the project root.
- `stop.bat` — closes the two windows `dev.bat` opened.

Requires `backend\.env` to already be set up (DB, mail, etc.) — this just launches
the two dev servers, it doesn't configure anything.

# Server scripts (Linux Docker host)

- `install-docker-host.sh` — installs Docker Engine, Compose v2, Buildx, git and openssl on
  Ubuntu/Debian: `sudo bash scripts/install-docker-host.sh`. See DEPLOYMENT.md step 2.
- `backup.sh` — dumps the database and the uploaded media into `backups/<timestamp>/`
  (keeps 14). Run from the project folder: `bash scripts/backup.sh`.
- `update.sh` — updates the server from GitHub: fetch, back up, `git pull --ff-only`, rebuild,
  restart (migrations run on backend start). Does nothing if already up to date:
  `bash scripts/update.sh`. See DEPLOYMENT.md, "Deploy a new version".

# Release scripts (publish images to Docker Hub)

- `release.sh` / `release.bat` — build the backend and frontend images and push them to one
  Docker Hub repository: `bash scripts/release.sh youruser/konoha-helpdesk 1.0.0` (Windows:
  `scripts\release.bat youruser/konoha-helpdesk 1.0.0`). Servers then run
  `docker-compose.prod.yml`. See DEPLOYMENT.md, "Deploy from Docker Hub images".
