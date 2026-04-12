# Runtime Deploy Files (Prod)

Файлы для ручного деплоя backend после `terraform apply`.

## Файлы

- `docker-compose.prod.yml` — backend + postgres.
- `env.example.prod` — пример переменных для `/opt/apofeoz/config/.env.prod`.
- `nginx.api.conf` — шаблон nginx-конфига (`__API_FQDN__` заменяется скриптом).
- `deploy-prod.sh` — сборка/запуск стека и проверка `/health`.
- `setup-tls.sh` — выпуск сертификата Let's Encrypt через certbot.
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
/opt/apofeoz/scripts/setup-tls.sh api.example.com you@example.com
/opt/apofeoz/scripts/install-backup-timer.sh
```

