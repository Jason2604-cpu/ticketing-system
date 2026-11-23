#!/usr/bin/env bash
# =============================================================================
#  Konoha HelpDesk — update the server from GitHub
#
#  Run on the server, from anywhere:
#      bash /opt/konoha-helpdesk/scripts/update.sh
#
#  What it does:
#    1. git fetch; if the server is already on origin/main, stop (nothing is touched)
#    2. back up the database and media (scripts/backup.sh)
#    3. git pull --ff-only   (refuses if local edits on the server would conflict)
#    4. docker compose build, then docker compose up -d
#       (new Flyway migrations run automatically when the backend starts)
#    5. show the container status
#
#  Rollback: no automatic rollback. See DEPLOYMENT.md, "Backup and restore".
# =============================================================================
set -euo pipefail

# Everything lives in a function that is called on the LAST line: `git pull` may replace this
# very file, and bash reads scripts as it runs, so it must already hold the whole script.
main() {
  cd "$(dirname "$0")/.."

  git fetch -q origin
  local branch local_sha remote_sha
  branch=$(git rev-parse --abbrev-ref HEAD)
  local_sha=$(git rev-parse HEAD)
  remote_sha=$(git rev-parse "origin/${branch}")

  if [ "${local_sha}" = "${remote_sha}" ]; then
    echo "Already up to date ($(git rev-parse --short HEAD))."
    return 0
  fi

  echo "==> Updating ${local_sha:0:7} -> ${remote_sha:0:7} (${branch})"
  mkdir -p backups
  bash scripts/backup.sh
  git pull --ff-only
  docker compose build
  docker compose up -d
  docker compose ps --format "table {{.Name}}\t{{.Status}}"
  echo "==> Done. Check that every container shows (healthy); nginx may take a few seconds."
}

main "$@"
exit
