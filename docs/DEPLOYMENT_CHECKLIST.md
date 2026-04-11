# Чеклист: готовность к test / prod

Краткая сверка с кодом репозитория (Ktor backend + Android). Подробный план сред — [IMPLEMENTATION_DEPLOYMENT_PLAN.md](./IMPLEMENTATION_DEPLOYMENT_PLAN.md).

## Что уже готово в репозитории

| Область | Статус |
|--------|--------|
| API `/api/v1/*` (auth, users, workers, sync, failed-batches, отчёт) | Реализовано |
| PostgreSQL + Flyway (`V1__init.sql`) при старте | Да |
| JWT access + refresh (хеш refresh в БД) | Да |
| Docker-образ API (`backend/Dockerfile`, shadow JAR) | Да |
| `docker-compose.yml` в backend | **Только PostgreSQL**; JAR/API запускаются отдельно или добавьте сервис в свой compose |
| Health | `GET /health` |
| Переменные окружения для БД и JWT | `AppConfig` + таблица в `backend/README.md` |
| Android: базовый URL через `BuildConfig.API_BASE_URL` | `app/build.gradle.kts` |

## Перед тестовым стендом

1. **PostgreSQL** — отдельная БД/ volume, не смешивать с prod.
2. **Обязательные env** (минимум):
   - `JDBC_URL`, `DB_USER`, `DB_PASSWORD`
   - `JWT_SECRET` — **случайная строка ≥ 32 символов** (не дефолт из `application.conf`).
3. **Первый админ** — задать `SEED_ADMIN_EMAIL` + `SEED_ADMIN_PASSWORD` **один раз** на пустой БД (или создать админа вручную).
4. **`REPORT_TIME_ZONE`** — например `Europe/Moscow`, если отчёты должны считать «вчера» по локали компании.
5. Опционально: `ACCESS_TOKEN_MINUTES`, `REFRESH_TOKEN_DAYS`.
6. Проверка: `GET /health`, затем register/login, выдача FOREMAN, малый `POST /api/v1/sync/batch`.

## Перед продакшеном (дополнительно)

1. **TLS** — Ktor слушает HTTP; наружу выдавать **HTTPS** через reverse proxy (Caddy, Traefik, nginx) с валидным сертификатом.
2. **Секреты** — только из секрет-хранилища / env на хосте, не в git.
3. **CORS** — сейчас в коде `anyHost()` ([`HTTP.kt`](../backend/src/main/kotlin/com/apofeoz/backend/plugins/HTTP.kt)). Для **нативного Android** CORS не используется; если появится веб-клиент — сузить до конкретных origin.
4. **Логи** — сейчас текстовый Logback в stdout; для проды при необходимости подключить JSON/агрегатор (Loki, ELK) — отдельная настройка.
5. **Бэкапы БД** — расписание `pg_dump` (или снапшоты облака), проверка восстановления.
6. **Мониторинг** — алерты по `/health`, метрики JVM/HTTP по желанию.
7. **Rate limiting / WAF** — в MVP нет; при публичном интернете рассмотреть на уровне proxy.

## Android и прод

- В **release** по умолчанию `https://api.example.com/` — **заменить** на реальный URL.
- Для HTTPS с доверенным CA **дополнительная настройка не нужна**.
- Сейчас в манифесте **`usesCleartextTraffic=true`** — удобно для dev/test по HTTP; для **prod-only** лучше отключить cleartext и оставить только HTTPS (отдельный манифест / buildType или `networkSecurityConfig`).

## Несоответствия «идеальной» архитектуре (не блокеры MVP)

- Compose в репозитории поднимает только Postgres; полный «один файлом» стек API+БД — на стороне вашего деплоя.
- Структурированные JSON-логи из `BACKEND_ARCHITECTURE.md` — пока не включены в `logback.xml`.
