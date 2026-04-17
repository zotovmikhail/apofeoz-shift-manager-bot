# CODEX Project State (Canonical)

Этот файл — **каноничный контракт текущего состояния проекта** для Codex/других агентов.

## Authority Order

1. `docs/CODEX_PROJECT_STATE.md` (этот файл)
2. `docs/SYSTEM_ARCHITECTURE.md`
3. `docs/BACKEND_ARCHITECTURE.md`, `docs/MOBILE_ARCHITECTURE.md`, `docs/REPORTING.md`
4. `docs/ui-reference/`, `docs/ui-mockups/`
5. `docs/archive/` (история, не источник истины)

## Current Product State

- Реализованы: Android клиент (`android/`), backend API на Ktor (`backend/`) и базовая web-админка (`web/`).
- Добавлена инфраструктурная заготовка для prod на Timeweb Cloud: `infra/terraform/` + `infra/deploy/`.
- Основной бизнес-поток: старт/стоп смен в мобильном клиенте с офлайн-очередью и фоновой синхронизацией.
- Визуальный baseline проекта теперь задаётся текущей web-админкой: тёмный графитовый интерфейс, золотой акцент, премиальная dashboard-композиция, сильная типографическая иерархия.

## Backend Contract (Implemented)

- Auth: `register/login/refresh/logout` с JWT access + refresh.
- Roles: `USER`, `FOREMAN`, `ADMIN`; действуют ограничения на смену ролей и защита «последнего ADMIN».
- Workers: онлайн-управление через `/api/v1/workers` (без офлайн-батчей workers).
- Sessions: применяются через `POST /api/v1/sync/batch` (START_SESSION/END_SESSION), идемпотентность по `batchUid`.
- Failed sync: `GET/GET by id/DELETE /api/v1/sync/failed-batches`.
- Reporting: `hours-by-worker-previous-day`, `hours-by-worker-range`, `timesheet.xlsx`.
- Источники: `backend/src/main/kotlin/com/apofeoz/backend/plugins/Routing.kt`, `backend/src/main/resources/openapi.yaml`, `backend/src/main/resources/db/migration/V1__init.sql`.

## Android Contract (Implemented)

- Compose UI + theme `ApofeozTheme` (dark + gold).
- Локальная очередь outbound батчей в Room (`outbound_batches`).
- Фоновая отправка WorkManager при сети (`OutboundSyncWorker`, `OutboundSyncScheduler`).
- 401: trigger re-login; 409: удаление локального батча после серверного reject и работа через failed-batches.
- Локальные блокировки/состояния pending действий через DataStore.
- Источники: `android/.../work/`, `android/.../data/local/`, `android/.../presentation/MainScreen.kt`.

## Reporting Rules (Implemented)

- Норма смены: 8 часов.
- `shiftEquivalent` = hours / 8 с округлением до 3 знаков.
- Границы дней зависят от `REPORT_TIME_ZONE` (по умолчанию `Europe/Moscow`).
- Источники: `docs/REPORTING.md`, `backend/.../service/ReportService.kt`, `backend/.../service/TimesheetXlsxWriter.kt`, `backend/src/main/resources/application.conf`.

## UI Source of Truth

- Основной визуальный источник истины: текущая реализация `web/` и зафиксированные токены в `docs/ui-reference/`.
- Android тема в `android/app/src/main/java/com/apofeoz/shiftmanager/presentation/theme/` пока функционально актуальна, но визуально должна эволюционировать в сторону web-консоли, а не наоборот.
- `docs/ui-mockups/` и `docs/MOBILE_UI_MOCKUP.md` считать историческим референсом по структуре экранов, но не по финальному визуальному языку.
- Для новых UI-задач сначала сверяться с web-админкой, затем с `docs/ui-reference/THEME_TOKENS.md`.

## Known Gaps / Not Implemented

- Нет отдельного CI-гейта для обязательной валидации документации.
- Нет автоматического CI/CD деплоя infra+backend (доставка пока manual script на сервере).
- Нет полноценного CI/CD для web-админки (доставка manual script).
