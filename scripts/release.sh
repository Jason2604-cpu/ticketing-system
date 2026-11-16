#!/usr/bin/env bash
# Build the backend + frontend images and push them to Docker Hub.
#
#   bash scripts/release.sh <dockerhub-repo> <version>
#   bash scripts/release.sh youruser/konoha-helpdesk 1.0.0
#
# Pushes four tags into the ONE repository:
#   <repo>:backend-<version>   <repo>:backend-latest
#   <repo>:frontend-<version>  <repo>:frontend-latest
# Run `docker login` once first. Servers then deploy with docker-compose.prod.yml.
set -euo pipefail

REPO="${1:-${DOCKER_REPO:-}}"
VERSION="${2:-${APP_VERSION:-}}"
if [[ -z "$REPO" || -z "$VERSION" ]]; then
  echo "Usage: bash scripts/release.sh <dockerhub-repo> <version>   e.g. youruser/konoha-helpdesk 1.0.0" >&2
  exit 1
fi
if [[ "$VERSION" == "latest" ]]; then
  echo "Give a real version (1.0.0, 1.1.0 ...). 'latest' is pushed automatically as well." >&2
  exit 1
fi

cd "$(dirname "$0")/.."

echo "==> Building backend  $REPO:backend-$VERSION"
docker build --pull -t "$REPO:backend-$VERSION" -t "$REPO:backend-latest" ./backend

echo "==> Building frontend $REPO:frontend-$VERSION"
docker build --pull --build-arg VITE_API_BASE_URL=/api \
  -t "$REPO:frontend-$VERSION" -t "$REPO:frontend-latest" ./frontend

for tag in "backend-$VERSION" "backend-latest" "frontend-$VERSION" "frontend-latest"; do
  echo "==> Pushing $REPO:$tag"
  docker push "$REPO:$tag"
done

echo
echo "Done. On the server set APP_VERSION=$VERSION in .env, then:"
echo "  docker compose -f docker-compose.prod.yml pull && docker compose -f docker-compose.prod.yml up -d"
