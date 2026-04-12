# Doc Maintenance Checklist (Mandatory)

Чеклист обязателен при изменениях кода. Если меняется контракт поведения, документация обновляется в том же PR/коммите.

## 1. API / Roles / Auth / Sync Changes

- Обновить `docs/CODEX_PROJECT_STATE.md` (раздел backend contract).
- Обновить `docs/BACKEND_ARCHITECTURE.md` (если меняются бизнес-правила/эндпоинты).
- Обновить `backend/src/main/resources/openapi.yaml`.
- При затрагивании клиентского поведения синка — проверить `docs/MOBILE_ARCHITECTURE.md`.

## 2. Android Offline/Sync Changes

- Обновить `docs/CODEX_PROJECT_STATE.md` (раздел Android contract).
- Обновить `docs/MOBILE_ARCHITECTURE.md` (потоки офлайн/онлайн и ограничения).
- Если меняется UX статусов/ошибок синка — обновить `docs/MOBILE_UI_MOCKUP.md` (или зафиксировать почему не нужно).

## 3. Reporting / Timesheet Changes

- Обновить `docs/CODEX_PROJECT_STATE.md` (reporting rules).
- Обновить `docs/REPORTING.md`.
- Проверить описание timezone/округлений/ограничений.

## 4. Config / Deploy Changes

- Обновить `docs/CODEX_PROJECT_STATE.md` (конфиг по умолчанию/ожидания).
- Обновить `docs/DEPLOYMENT_CHECKLIST.md`.
- При необходимости обновить `docs/IMPLEMENTATION_DEPLOYMENT_PLAN.md`.

## 5. UI Source-of-Truth Changes

- Если меняется источник референс-макетов, обновить:
  - `docs/CODEX_PROJECT_STATE.md`
  - `docs/README.md`
  - `README.md`
  - `PROJECT_STRUCTURE.md`

## Done Criteria

- Все затронутые ссылки валидны (нет битых путей).
- Новое поведение описано в `CODEX_PROJECT_STATE.md`.
- Нет противоречий между canonical и архитектурными документами.
- Если изменён исторический документ, он находится в `docs/archive/` и имеет архивный баннер.

