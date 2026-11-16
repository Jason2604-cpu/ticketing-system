#!/usr/bin/env bash
# =============================================================================
#  Konoha HelpDesk — Docker host dependencies for Ubuntu / Debian
#
#  Installs everything the SERVER needs to build and run the 5 containers:
#    - Docker Engine + CLI + containerd (official Docker apt repository)
#    - Docker Compose v2 plugin   (`docker compose ...`)
#    - Docker Buildx plugin       (BuildKit — the Dockerfiles use cache mounts)
#    - git, curl, openssl, ca-certificates, gnupg
#  Optionally opens ports 80/443 in ufw if ufw is active.
#
#  Java, Maven, Node and npm are NOT installed on the host: they run inside the
#  build containers (see DEPENDENCIES.md).
#
#  Usage:   sudo bash scripts/install-docker-host.sh
#  Tested on: Ubuntu 22.04 / 24.04, Debian 12.
# =============================================================================
set -euo pipefail

if [[ $EUID -ne 0 ]]; then
  echo "Run with sudo:  sudo bash $0" >&2
  exit 1
fi

. /etc/os-release
case "${ID}" in
  ubuntu|debian) DISTRO="${ID}" ;;
  *)
    if [[ "${ID_LIKE:-}" == *ubuntu* ]]; then DISTRO=ubuntu
    elif [[ "${ID_LIKE:-}" == *debian* ]]; then DISTRO=debian
    else echo "Unsupported OS: ${PRETTY_NAME}. This script is for Ubuntu/Debian." >&2; exit 1; fi ;;
esac
CODENAME="${UBUNTU_CODENAME:-${VERSION_CODENAME}}"
TARGET_USER="${SUDO_USER:-}"

echo "==> ${PRETTY_NAME} (${DISTRO} ${CODENAME})"

echo "==> Removing distro packages that conflict with Docker's own (safe if absent)"
for pkg in docker.io docker-doc docker-compose docker-compose-v2 podman-docker containerd runc; do
  apt-get remove -y -q "$pkg" >/dev/null 2>&1 || true
done

echo "==> Base tools"
apt-get update -q
DEBIAN_FRONTEND=noninteractive apt-get install -y -q ca-certificates curl gnupg git openssl

echo "==> Docker apt repository"
install -m 0755 -d /etc/apt/keyrings
curl -fsSL "https://download.docker.com/linux/${DISTRO}/gpg" -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/${DISTRO} ${CODENAME} stable" \
  > /etc/apt/sources.list.d/docker.list
apt-get update -q

echo "==> Docker Engine, Buildx and Compose"
DEBIAN_FRONTEND=noninteractive apt-get install -y -q \
  docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

systemctl enable --now docker

if [[ -n "${TARGET_USER}" && "${TARGET_USER}" != "root" ]]; then
  usermod -aG docker "${TARGET_USER}"
  echo "==> Added ${TARGET_USER} to the docker group (log out and back in for it to apply)"
fi

if command -v ufw >/dev/null 2>&1 && ufw status | grep -q "Status: active"; then
  echo "==> ufw is active: allowing HTTP/HTTPS"
  ufw allow 80/tcp  >/dev/null
  ufw allow 443/tcp >/dev/null
fi

echo
echo "==> Installed:"
docker --version
docker compose version
docker buildx version
git --version
echo
echo "Next: DEPLOYMENT.md step 3 (get the code onto this server)."
