# Runtime Deploy Files (Prod)

Файлы для ручного деплоя backend после `terraform apply`.

## Файлы

- `docker-compose.prod.yml` — backend + web + postgres.
- `env.example.prod` — пример переменных для `/opt/apofeoz/config/.env.prod`.
- `nginx.api.conf` — шаблон nginx-конфига (`__API_FQDN__` заменяется скриптом).
- `nginx.admin.conf` — шаблон nginx-конфига (`__ADMIN_FQDN__` заменяется скриптом).
- `deploy-prod.sh` — сборка/запуск стека и проверка `/health`.
- `setup-tls.sh` — выпуск сертификата Let's Encrypt для API и admin web через certbot.
- `backup-postgres.sh` — ручной backup.
- `restore-postgres.sh` — restore из `.sql.gz`.
- `install-backup-timer.sh` — systemd timer (ежедневно в 03:30).
- `sync-runtime-files.sh` — копирование файлов в `/opt/apofeoz/{config,scripts}`.

## Рекомендуемый порядок на сервере

```bash
/opt/apofeoz/app/infra/deploy/sync-runtime-files.sh /opt/apofeoz/app
cp /opt/apofeoz/app/infra/deploy/env.example.prod /opt/apofeoz/config/.env.prod
nano /opt/apofeoz/config/.env.prod
/opt/apofeoz/scripts/deploy-prod.sh
/opt/apofeoz/scripts/setup-tls.sh api.example.com admin.example.com you@example.com
/opt/apofeoz/scripts/install-backup-timer.sh
```

Критично для web:
- `NEXT_PUBLIC_API_BASE_URL=https://api.<domain>`
- `CORS_ALLOWED_ORIGINS=https://admin.<domain>`
