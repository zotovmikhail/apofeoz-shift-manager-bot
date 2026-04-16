# Документация Apofeoz

Документация и мокапы для проекта Apofeoz.

## Canonical (для агентов и разработки)

- **[AGENTS.md](./AGENTS.md)** — стартовая точка для Codex/других агентов (read order, правила, fast-path).
- **[CODEX_PROJECT_STATE.md](./CODEX_PROJECT_STATE.md)** — каноничный контракт текущего состояния.
- **[DOC_MAINTENANCE_CHECKLIST.md](./DOC_MAINTENANCE_CHECKLIST.md)** — обязательный чеклист обновления документации при изменении кода.

## Architecture

- **[SYSTEM_ARCHITECTURE.md](./SYSTEM_ARCHITECTURE.md)** — общая архитектура системы.
- **[BACKEND_ARCHITECTURE.md](./BACKEND_ARCHITECTURE.md)** — backend контракт/правила.
- **[MOBILE_ARCHITECTURE.md](./MOBILE_ARCHITECTURE.md)** — мобильная архитектура и офлайн‑синхронизация.
- **[REPORTING.md](./REPORTING.md)** — отчёты, таймзона, округления, табель.
- **[PROJECT_STRUCTURE.md](../PROJECT_STRUCTURE.md)** — структура всего проекта.

## Reference / UI

- **[ui-mockups/](./ui-mockups/)** — HTML-мокапы экранов.
- **[mobile_mockup.html](./mobile_mockup.html)** — интерактивный прототип.
- **[mobile_presentation.html](./mobile_presentation.html)** — презентация экранов.
- **[MOBILE_UI_MOCKUP.md](./MOBILE_UI_MOCKUP.md)** — описание UI-экранов и сценариев.
- **[ui-reference/README.md](./ui-reference/README.md)** — архивный дизайн-референс (Figma/токены).

## Planning / Deployment

- **[IMPLEMENTATION_DEPLOYMENT_PLAN.md](./IMPLEMENTATION_DEPLOYMENT_PLAN.md)** — план реализации и деплоя (не заменяет canonical docs).
- **[DEPLOYMENT_CHECKLIST.md](./DEPLOYMENT_CHECKLIST.md)** — сверка готовности test/prod.
- **[infra/terraform/README.md](../infra/terraform/README.md)** — Terraform для Timeweb Cloud (prod MVP).
- **[infra/deploy/README.md](../infra/deploy/README.md)** — runtime-скрипты деплоя/backup/restore.
- **[web/README.md](../web/README.md)** — веб-админка (Next.js).
- **[JAVA_SPRING_TO_KOTLIN_KTOR_ANDROID.md](./JAVA_SPRING_TO_KOTLIN_KTOR_ANDROID.md)** — мост для Java/Spring разработчика.

## Archive (Non-Authoritative)

- **[archive/README.md](./archive/README.md)** — правила архива.
- **[archive/MOBILE_IMPLEMENTATION_PLAN.md](./archive/MOBILE_IMPLEMENTATION_PLAN.md)** — исторический детальный план.
- **[archive/CODE_EXAMPLES.md](./archive/CODE_EXAMPLES.md)** — исторические примеры кода.

## 🎨 Мокапы

Откройте `mobile_mockup.html` или `mobile_presentation.html` в браузере для просмотра интерактивных мокапов.

### Основные экраны:
1. Авторизация
2. Главный экран (Бригадир) — список рабочих
3. Управление сменами — одно нажатие
4. Добавление рабочего
5. Перенос рабочего
6. Синхронизация
7. Статистика и история смен

