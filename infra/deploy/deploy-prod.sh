#!/usr/bin/env bash
set -euo pipefail

STACK_NAME="${STACK_NAME:-apofeoz-prod}"
APP_ROOT="${APP_ROOT:-/opt/apofeoz/app}"
CFG_ROOT="${CFG_ROOT:-/opt/apofeoz/config}"
SCRIPTS_ROOT="${SCRIPTS_ROOT:-/opt/apofeoz/scripts}"

COMPOSE_FILE="$CFG_ROOT/docker-compose.prod.yml"
ENV_FILE="$CFG_ROOT/.env.prod"

if [[ ! -f "$COMPOSE_FILE" ]]; then
  echo "ERROR: missing $COMPOSE_FILE"
  exit 1
fi

if [[ ! -f "$ENV_FILE" ]]; then
  echo "ERROR: missing $ENV_FILE"
  exit 1
fi

if [[ ! -d "$APP_ROOT/backend" ]]; then
  echo "ERROR: missing backend sources in $APP_ROOT/backend"
  echo "Clone repository into $APP_ROOT before deploy."
  exit 1
fi

mkdir -p "$SCRIPTS_ROOT"

echo "==> docker compose pull (base images)"
docker compose -p "$STACK_NAME" -f "$COMPOSE_FILE" --env-file "$ENV_FILE" pull || true

echo "==> docker compose up -d --build"
docker compose -p "$STACK_NAME" -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d --build

echo "==> waiting for health endpoint"
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:8080/health" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

if ! curl -fsS "http://127.0.0.1:8080/health" >/dev/null 2>&1; then
  echo "ERROR: backend is not healthy on http://127.0.0.1:8080/health"
  exit 1
fi

echo "==> waiting for web endpoint"
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:3000" >/dev/null 2>&1; then
    echo "OK: backend and web are healthy"
    exit 0
  fi
  sleep 2
done

echo "ERROR: web is not healthy on http://127.0.0.1:3000"
exit 1
