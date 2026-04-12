# Terraform: Timeweb Cloud (Prod)

Этот каталог поднимает прод-инфраструктуру для backend на Timeweb Cloud:
- VPS (Ubuntu 22.04, SSH key only)
- Firewall (22/80/443)
- DNS `A` record для `api.<domain>` (опционально)
- Базовый cloud-init (Docker, Nginx, Certbot, каталоги `/opt/apofeoz`)

## 1) Подготовка

1. Установите Terraform `>= 1.3`.
2. Создайте API токен Timeweb и экспортируйте:
   - `export TWC_TOKEN=...` (Linux/macOS)
3. Подготовьте Terraform Cloud backend:
   - `cp backend.hcl.example backend.hcl`
   - заполните organization/workspace
4. Подготовьте переменные:
   - `cp terraform.tfvars.example terraform.tfvars`
   - заполните домен, SSH key name, email.

## 2) Инициализация и apply

```bash
cd infra/terraform
terraform init -backend-config=backend.hcl
terraform plan
terraform apply
```

## 3) Пост-deploy шаги на сервере

1. Зайдите по SSH (`terraform output ssh_connect`).
2. Клонируйте репозиторий в `/opt/apofeoz/app`:
   - `git clone <repo-url> /opt/apofeoz/app`
3. Синхронизируйте runtime-файлы:
   - `/opt/apofeoz/app/infra/deploy/sync-runtime-files.sh /opt/apofeoz/app`
4. Создайте `/opt/apofeoz/config/.env.prod` из `infra/deploy/env.example.prod`.
5. Запустите backend:
   - `/opt/apofeoz/scripts/deploy-prod.sh`
6. Включите TLS:
   - `/opt/apofeoz/scripts/setup-tls.sh api.<domain> <email>`
7. Включите ежедневные бэкапы:
   - `/opt/apofeoz/scripts/install-backup-timer.sh`

## Примечания

- Если DNS не в Timeweb, поставьте `manage_dns = false` и создайте A-запись вручную на `terraform output server_public_ipv4`.
- Для доступа к SSH лучше ставить `ssh_allowed_cidr` как ваш белый IP `/32`, а не `0.0.0.0/0`.
- Для MVP выбран Postgres в Docker на том же VPS; контролируйте место на диске и проверяйте восстановление из бэкапов.
