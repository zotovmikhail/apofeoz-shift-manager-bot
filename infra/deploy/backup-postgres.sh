#!/usr/bin/env bash
set -euo pipefail

STACK_NAME="${STACK_NAME:-apofeoz-prod}"
CFG_ROOT="${CFG_ROOT:-/opt/apofeoz/config}"
BACKUP_DIR="${BACKUP_DIR:-/opt/apofeoz/backups}"
RETENTION_DAYS="${RETENTION_DAYS:-7}"

COMPOSE_FILE="$CFG_ROOT/docker-compose.prod.yml"
ENV_FILE="$CFG_ROOT/.env.prod"

if [[ ! -f "$COMPOSE_FILE" || ! -f "$ENV_FILE" ]]; then
  echo "ERROR: missing compose or env file in $CFG_ROOT"
  exit 1
fi

mkdir -p "$BACKUP_DIR"
TIMESTAMP="$(date +%F_%H%M%S)"
TARGET_FILE="$BACKUP_DIR/postgres_${TIMESTAMP}.sql.gz"

set -a
source "$ENV_FILE"
set +a

docker compose -p "$STACK_NAME" -f "$COMPOSE_FILE" --env-file "$ENV_FILE" exec -T postgres \
  pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB" | gzip >"$TARGET_FILE"

find "$BACKUP_DIR" -type f -name "postgres_*.sql.gz" -mtime +"$RETENTION_DAYS" -delete

echo "Backup created: $TARGET_FILE"

