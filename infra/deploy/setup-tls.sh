#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 2 ]]; then
  echo "Usage: $0 <api_fqdn> <letsencrypt_email>"
  exit 1
fi

API_FQDN="$1"
LE_EMAIL="$2"

CFG_ROOT="${CFG_ROOT:-/opt/apofeoz/config}"
NGINX_AVAIL="/etc/nginx/sites-available/apofeoz-api.conf"
NGINX_ENABLED="/etc/nginx/sites-enabled/apofeoz-api.conf"
TEMPLATE_FILE="$CFG_ROOT/nginx.api.conf"

if [[ ! -f "$TEMPLATE_FILE" ]]; then
  echo "ERROR: missing $TEMPLATE_FILE"
  exit 1
fi

sudo cp "$TEMPLATE_FILE" "$NGINX_AVAIL"
sudo sed -i "s/__API_FQDN__/$API_FQDN/g" "$NGINX_AVAIL"
sudo ln -sf "$NGINX_AVAIL" "$NGINX_ENABLED"

if [[ -f /etc/nginx/sites-enabled/default ]]; then
  sudo rm -f /etc/nginx/sites-enabled/default
fi

sudo nginx -t
sudo systemctl reload nginx

sudo certbot --nginx --non-interactive --agree-tos -m "$LE_EMAIL" -d "$API_FQDN" --redirect
sudo systemctl reload nginx

echo "TLS configured for https://$API_FQDN"

