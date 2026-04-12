#!/usr/bin/env bash
set -euo pipefail

SCRIPTS_ROOT="${SCRIPTS_ROOT:-/opt/apofeoz/scripts}"
UNIT_DIR="/etc/systemd/system"
SERVICE_NAME="apofeoz-pg-backup.service"
TIMER_NAME="apofeoz-pg-backup.timer"

if [[ ! -x "$SCRIPTS_ROOT/backup-postgres.sh" ]]; then
  echo "ERROR: missing executable $SCRIPTS_ROOT/backup-postgres.sh"
  exit 1
fi

sudo tee "$UNIT_DIR/$SERVICE_NAME" >/dev/null <<'EOF'
[Unit]
Description=Apofeoz PostgreSQL backup
After=docker.service

[Service]
Type=oneshot
ExecStart=/opt/apofeoz/scripts/backup-postgres.sh
EOF

sudo tee "$UNIT_DIR/$TIMER_NAME" >/dev/null <<'EOF'
[Unit]
Description=Run Apofeoz PostgreSQL backup daily

[Timer]
OnCalendar=*-*-* 03:30:00
Persistent=true

[Install]
WantedBy=timers.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable --now "$TIMER_NAME"
sudo systemctl list-timers "$TIMER_NAME" --no-pager

