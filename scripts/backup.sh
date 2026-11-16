#!/usr/bin/env bash
# =============================================================================
#  Konoha HelpDesk — backup the database and uploaded media
#
#  Run from the project folder (where docker-compose.yml is):
#      bash scripts/backup.sh               -> ./backups/<timestamp>/
#  Keeps the newest 14 backups. Schedule it daily with cron, e.g.:
#      15 2 * * *  cd /opt/konoha-helpdesk && mkdir -p backups && bash scripts/backup.sh >> backups/backup.log 2>&1
#
#  Restore: see DEPLOYMENT.md, "Backup and restore".
# =============================================================================
set -euo pipefail
cd "$(dirname "$0")/.."

KEEP=${KEEP:-14}
STAMP=$(date +%Y%m%d-%H%M%S)
DEST="backups/${STAMP}"
mkdir -p "${DEST}"
# A failed run must not leave a half-written folder that counts as one of the kept backups.
trap 'echo "Backup FAILED - removing ${DEST}" >&2; rm -rf "${DEST}"' ERR

# Read DB name/user from .env (same defaults as docker-compose.yml).
POSTGRES_DB=$(grep -E '^POSTGRES_DB=' .env | cut -d= -f2- || true)
POSTGRES_USER=$(grep -E '^POSTGRES_USER=' .env | cut -d= -f2- || true)
POSTGRES_DB=${POSTGRES_DB:-konoha_helpdesk}
POSTGRES_USER=${POSTGRES_USER:-konoha}

echo "==> Database -> ${DEST}/db.dump"
docker compose exec -T db pg_dump -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -Fc > "${DEST}/db.dump"

echo "==> Media -> ${DEST}/media.tar.gz"
docker compose exec -T backend tar -czf - -C /app/media . > "${DEST}/media.tar.gz"

ls -lh "${DEST}"

echo "==> Keeping the newest ${KEEP} backups"
ls -1dt backups/*/ 2>/dev/null | tail -n +$((KEEP + 1)) | xargs -r rm -rf
echo "Done."
