#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <repo_root>"
  exit 1
fi

REPO_ROOT="$1"
TARGET_CFG="${TARGET_CFG:-/opt/apofeoz/config}"
TARGET_SCRIPTS="${TARGET_SCRIPTS:-/opt/apofeoz/scripts}"

mkdir -p "$TARGET_CFG" "$TARGET_SCRIPTS"

cp "$REPO_ROOT/infra/deploy/docker-compose.prod.yml" "$TARGET_CFG/docker-compose.prod.yml"
cp "$REPO_ROOT/infra/deploy/nginx.api.conf" "$TARGET_CFG/nginx.api.conf"
cp "$REPO_ROOT/infra/deploy/nginx.admin.conf" "$TARGET_CFG/nginx.admin.conf"
cp "$REPO_ROOT/infra/deploy/env.example.prod" "$TARGET_CFG/.env.prod.example"

cp "$REPO_ROOT/infra/deploy/deploy-prod.sh" "$TARGET_SCRIPTS/deploy-prod.sh"
cp "$REPO_ROOT/infra/deploy/setup-tls.sh" "$TARGET_SCRIPTS/setup-tls.sh"
cp "$REPO_ROOT/infra/deploy/backup-postgres.sh" "$TARGET_SCRIPTS/backup-postgres.sh"
cp "$REPO_ROOT/infra/deploy/restore-postgres.sh" "$TARGET_SCRIPTS/restore-postgres.sh"
cp "$REPO_ROOT/infra/deploy/install-backup-timer.sh" "$TARGET_SCRIPTS/install-backup-timer.sh"

chmod +x "$TARGET_SCRIPTS/"*.sh

echo "Runtime files synced."
echo "Next: create $TARGET_CFG/.env.prod from $TARGET_CFG/.env.prod.example"
