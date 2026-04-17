# AGENTS Quickstart

Краткий вход для Codex/других агентов. Цель: быстро понять, что в проекте является истиной, и где править документацию.

## Read Order (обязательно)

1. `docs/CODEX_PROJECT_STATE.md` — каноничный контракт текущего состояния.
2. `docs/SYSTEM_ARCHITECTURE.md` — общая архитектурная картина.
3. `docs/BACKEND_ARCHITECTURE.md` + `docs/MOBILE_ARCHITECTURE.md` — детальные правила по слоям.
4. `docs/REPORTING.md` — специфика отчётов/табеля.
5. `docs/DOC_MAINTENANCE_CHECKLIST.md` — что обновлять в доках при изменениях кода.

## Authority Rules

- При конфликте документов: `CODEX_PROJECT_STATE.md` выше остальных.
- Папка `docs/archive/` не авторитетна для реализации, только исторический контекст.
- `docs/ui-reference/` и `docs/ui-mockups/` — референсы дизайна, не runtime-код.

## Fast Path by Task

- Изменение API/ролей/auth/sync: сначала `CODEX_PROJECT_STATE.md`, затем `BACKEND_ARCHITECTURE.md`, потом `backend/src/main/resources/openapi.yaml`.
- Изменение офлайн-синхронизации Android: `CODEX_PROJECT_STATE.md`, `MOBILE_ARCHITECTURE.md`, затем `android/.../work` и `android/.../data/local`.
- Изменение отчётов/табеля: `REPORTING.md`, `CODEX_PROJECT_STATE.md`, затем `ReportService`/`TimesheetXlsxWriter`.
- Изменение UI-темы: сначала `CODEX_PROJECT_STATE.md`, затем `docs/ui-reference/THEME_TOKENS.md`, затем текущая реализация `web/`, и только потом `android/.../presentation/theme`.
- Изменение инфраструктуры/деплоя: `infra/terraform/README.md`, затем `infra/terraform/*.tf` и `infra/deploy/*`.
- Изменение web-админки: `web/README.md`, затем `web/app`, `web/components`, `web/lib`.

## Do-Not-Assume

- Не предполагать отдельные REST CRUD для смен: смены идут через `POST /api/v1/sync/batch`.
- Не предполагать офлайн-очередь для workers: workers только онлайн API.
- Не считать документы из `docs/archive/` актуальным контрактом.
- Не считать старые мобильные мокапы финальным visual source of truth: главный актуальный стиль сейчас задаёт `web/`.
