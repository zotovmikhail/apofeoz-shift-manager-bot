#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <backup_file.sql.gz>"
  exit 1
fi

BACKUP_FILE="$1"
STACK_NAME="${STACK_NAME:-apofeoz-prod}"
CFG_ROOT="${CFG_ROOT:-/opt/apofeoz/config}"
COMPOSE_FILE="$CFG_ROOT/docker-compose.prod.yml"
ENV_FILE="$CFG_ROOT/.env.prod"

if [[ ! -f "$BACKUP_FILE" ]]; then
  echo "ERROR: backup file does not exist: $BACKUP_FILE"
  exit 1
fi

if [[ ! -f "$COMPOSE_FILE" || ! -f "$ENV_FILE" ]]; then
  echo "ERROR: missing compose or env file in $CFG_ROOT"
  exit 1
fi

set -a
source "$ENV_FILE"
set +a

echo "WARNING: this will overwrite database data in container postgres"
echo "Database: $POSTGRES_DB"
read -r -p "Type 'restore' to continue: " answer
if [[ "$answer" != "restore" ]]; then
  echo "Restore cancelled."
  exit 0
fi

gunzip -c "$BACKUP_FILE" | docker compose -p "$STACK_NAME" -f "$COMPOSE_FILE" --env-file "$ENV_FILE" exec -T postgres \
  psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"

echo "Restore completed from: $BACKUP_FILE"

