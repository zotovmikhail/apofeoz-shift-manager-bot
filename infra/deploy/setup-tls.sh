#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 3 ]]; then
  echo "Usage: $0 <api_fqdn> <admin_fqdn> <letsencrypt_email>"
  exit 1
fi

API_FQDN="$1"
ADMIN_FQDN="$2"
LE_EMAIL="$3"

CFG_ROOT="${CFG_ROOT:-/opt/apofeoz/config}"
NGINX_API_AVAIL="/etc/nginx/sites-available/apofeoz-api.conf"
NGINX_API_ENABLED="/etc/nginx/sites-enabled/apofeoz-api.conf"
NGINX_ADMIN_AVAIL="/etc/nginx/sites-available/apofeoz-admin.conf"
NGINX_ADMIN_ENABLED="/etc/nginx/sites-enabled/apofeoz-admin.conf"
API_TEMPLATE_FILE="$CFG_ROOT/nginx.api.conf"
ADMIN_TEMPLATE_FILE="$CFG_ROOT/nginx.admin.conf"

if [[ ! -f "$API_TEMPLATE_FILE" ]]; then
  echo "ERROR: missing $API_TEMPLATE_FILE"
  exit 1
fi

if [[ ! -f "$ADMIN_TEMPLATE_FILE" ]]; then
  echo "ERROR: missing $ADMIN_TEMPLATE_FILE"
  exit 1
fi

sudo cp "$API_TEMPLATE_FILE" "$NGINX_API_AVAIL"
sudo sed -i "s/__API_FQDN__/$API_FQDN/g" "$NGINX_API_AVAIL"
sudo ln -sf "$NGINX_API_AVAIL" "$NGINX_API_ENABLED"

sudo cp "$ADMIN_TEMPLATE_FILE" "$NGINX_ADMIN_AVAIL"
sudo sed -i "s/__ADMIN_FQDN__/$ADMIN_FQDN/g" "$NGINX_ADMIN_AVAIL"
sudo ln -sf "$NGINX_ADMIN_AVAIL" "$NGINX_ADMIN_ENABLED"

if [[ -f /etc/nginx/sites-enabled/default ]]; then
  sudo rm -f /etc/nginx/sites-enabled/default
fi

sudo nginx -t
sudo systemctl reload nginx

sudo certbot --nginx --non-interactive --agree-tos -m "$LE_EMAIL" -d "$API_FQDN" -d "$ADMIN_FQDN" --redirect
sudo systemctl reload nginx

echo "TLS configured for https://$API_FQDN and https://$ADMIN_FQDN"
