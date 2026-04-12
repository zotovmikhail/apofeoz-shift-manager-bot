# План реализации, разработки и деплоя (Apofeoz)

Документ описывает **порядок работ**, **среды (test / prod)** на твоей **виртуальной машине с Docker**, и **как проектировать Android** под текущую архитектуру из `BACKEND_ARCHITECTURE.md` и `MOBILE_ARCHITECTURE.md`.

> **Связанные документы:** `SYSTEM_ARCHITECTURE.md`, `BACKEND_ARCHITECTURE.md`, `MOBILE_ARCHITECTURE.md`, `MOBILE_UI_MOCKUP.md`.  
> Устаревшие детали в `archive/MOBILE_IMPLEMENTATION_PLAN.md` (старые пути API) не использовать как истину — ориентируйся на `/api/v1/...` и батчи только для сессий.

---

## 1. Цели и принципы

1. **Сначала контракт и данные:** PostgreSQL + Flyway, API `/api/v1`, JWT, `POST /api/v1/sync/batch`.
2. **Офлайн только для смен:** очередь в Room, head-of-line, ретраи 5xx, 409 → `failed_sync_batches` + локальный FAILED.
3. **Рабочие — только онлайн:** `GET/POST/PATCH /api/v1/workers` без батчей.
4. **Две изолированные среды на одной VM:** `test` и `prod` — разные compose-проекты, разные порты/volumes, разные секреты.
5. **Android:** два `buildType` (или `productFlavor`) с разными `BASE_URL`; релизная подпись и отдельная сборка под prod.

---

## 2. Фазы разработки (рекомендуемый порядок)

### Фаза A — Backend skeleton (1 этап, 3–7 дней в зависимости от темпа)

- Gradle Ktor-проект в `backend/` (или актуальная структура репозитория).
- Подключение **PostgreSQL** (локально или контейнер), **Exposed/JDBC**, **Flyway** — миграции по DDL из `BACKEND_ARCHITECTURE.md` (`users` с `first_name`/`last_name`, `workers` с `user_id`/`foreman_id`, `sessions`, `sync_batches`, `sync_events`, `failed_sync_batches`).
- Конфиг через env: `JDBC_URL`, `JWT_SECRET` (или ключи подписи), `PORT`.
- Health: `GET /health` (для Docker/reverse proxy).
- Структурированные JSON-логи + корреляция `batch_uid` (как в архитектуре).

### Фаза B — Auth и пользователи (MVP)

- `POST /api/v1/auth/register` — email/phone, **обязательные** `first_name`, `last_name`, password → `USER`.
- `POST /api/v1/auth/login`, refresh, logout; refresh в БД.
- `GET /api/v1/users/me`.
- Админ: `GET/PATCH /api/v1/users/...` (минимум для выдачи роли `FOREMAN` / создания первого админа вручную в БД — как уже зафиксировано в архитектуре).

### Фаза C — Workers (онлайн)

- `GET /api/v1/workers` — для `FOREMAN`: `foreman_id = текущий user`.
- `POST /api/v1/workers` — создание с привязкой к текущему прорабу.
- `PATCH /api/v1/workers/{id}` — редактирование + **переназначение** `foreman_id` («забрать себе»).
- При первом назначении роли `FOREMAN` (или при создании пользователя-прораба): **автоматически** создать строку `workers` с `user_id` и `foreman_id` = этому пользователю, поля `workers.first_name` / `workers.last_name` = `users.first_name` / `users.last_name`.

### Фаза D — Сессии и синхронизация

- Онлайн-эндпоинты сессий (если остаются в MVP) или сразу опора на батчи — **источник истины для офлайна:** `POST /api/v1/sync/batch`.
- Реализация: идемпотентность `batch_uid`, транзакция на весь батч, **409** с `failedEventIndex`/`reason`, запись в `failed_sync_batches` отдельной транзакцией после отката (как в документе).
- Валидация: один активный `session` на `worker_id` (уникальный partial index).

### Фаза E — Android (ниже — отдельный подраздел по слоям и этапам)

### Фаза F — Отчёт / админка

- Один отчёт в данных: часы и эквивалент смен (8 ч) по каждому `workers` за вчера; UI — на главной админа **«Отчёты»** → экран списка отчётов (MVP: один пункт) → экран отчёта.

---

## 3. Тестовая и продовая среда на одной VM (Docker)

### 3.1. Общая схема

| Аспект | Test | Prod |
|--------|------|------|
| Каталог на VM | например `/opt/apofeoz/test` | `/opt/apofeoz/prod` |
| Compose файл | `docker-compose.yml` (или `compose.test.yaml`) | отдельный файл |
| Имя проекта Docker Compose | `-p apofeoz-test` | `-p apofeoz-prod` |
| PostgreSQL | контейнер `postgres`, отдельный volume | отдельный volume |
| Порт API наружу | например `8081` | например `8080` |
| Домен (если есть) | `api-test.example.com` | `api.example.com` |
| Секреты | `.env.test` (не в git) | `.env.prod` (не в git) |
| JWT / пароли БД | **другие** значения | продакшен-сильные |

**Правило:** никогда не шарить volume PostgreSQL между test и prod.

### 3.2. Состав compose-стека (минимум)

- **postgres:** официальный образ, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` из env, volume `pgdata`.
- **backend:** твой образ из `backend/Dockerfile`, env `JDBC_URL` на сервис `postgres`, миграции Flyway при старте или отдельный `migrate` job (одноразовый контейнер) — на выбор, главное — предсказуемый порядок: БД поднялась → миграции → приложение.

Опционально позже:

- **reverse proxy (Caddy или Traefik):** TLS, прокси на контейнер backend.
- Отдельный контейнер для бэкапов (cron + `pg_dump`).

### 3.3. Переменные окружения (чеклист)

- БД: URL, пользователь, пароль.
- JWT: секрет или путь к ключу; время жизни access/refresh.
- `ENVIRONMENT=test|production` — влияет на уровень логов (DEBUG payload только на test).
- CORS: на test можно `*`, на prod — только домены фронта/мобильного приложения (если нужен browser).

### 3.4. Деплой на VM (процесс)

1. Установить Docker + Docker Compose plugin.
2. Склонировать репозиторий (или CI артефакт).
3. Положить `.env.test` / `.env.prod` **вне git**.
4. `docker compose -f docker-compose.test.yml --env-file .env.test up -d --build` (аналогично prod).
5. Проверить `GET /health`, затем smoke: register/login, workers, маленький батч.

### 3.5. Миграции и откаты

- Только **вперёд** через Flyway; откат — новая миграция «down» логикой, а не удаление файлов с prod.
- Перед деплоем на prod: прогон миграций на копии/snapshot или на test.

### 3.6. Резервное копирование (prod)

- Ежедневный `pg_dump` в файл на хосте или S3-совместимое хранилище.
- Хранить минимум N дней; тестировать восстановление раз в квартал.

---

## 4. Android: как спроектировать реализацию

### 4.1. Модули и стек

- **Минимум:** один модуль `app` с пакетами `data`, `domain`, `presentation`, `core`.
- **Позже KMM:** вынести `domain` + контракты API + модели синка в `:shared`.

Стек (согласованно с `MOBILE_ARCHITECTURE.md`):

- UI: **Jetpack Compose** + Material 3.
- DI: Hilt или Koin.
- Сеть: Retrofit + OkHttp; kotlinx.serialization или Moshi.
- Локально: **Room** (workers/sessions cache, `sync_queue`, флаги последней синхронизации).
- Фон: **WorkManager** — отправка очереди батчей при сети (без пользовательского «автосинк» тумблера).

### 4.2. Сборки и окружения

- `debug` → `BASE_URL = https://<vm-ip>:8081/api/v1/` (test) или отдельный flavor `staging`.
- `release` → `BASE_URL = https://api.example.com/api/v1/` (prod).
- Хранить URL в `BuildConfig`, не хардкодить в коде экранов.

**Сетевой доступ с телефона:** VM должна быть достижима по IP/домену из Wi‑Fi устройства; для HTTPS — reverse proxy + сертификат (Let’s Encrypt), иначе на Android придётся доверять user CA только в debug.

### 4.3. Этапы реализации Android (кратко)

1. **Каркас:** навигация, тема, экран логина/регистрации (поля имя, фамилия — обязательные), сохранение токенов (EncryptedSharedPreferences / DataStore).
2. **API слой:** интерцептор JWT, refresh по 401, модели под OpenAPI или ручные DTO.
3. **Room:** сущности под workers/sessions + `SyncQueueEntity` только для `START_SESSION`/`END_SESSION`.
4. **Репозитории:** онлайн workers; смены — запись в Room + постановка в очередь; **Mutex** на отправку батчей (head-of-line).
5. **WorkManager:** периодически и по `NetworkType.CONNECTED` — drain очереди; экспоненциальный backoff на 5xx.
6. **UI прораба:** список карточек (включая «Вы (бригадир)»), тап старт/стоп, экран добавления рабочего, перенос прорабу (вызов PATCH).
7. **Экран синка:** последняя успешная синхронизация, счётчики очереди / неуспешных пакетов, просмотр ошибок 409 (и ссылка на повтор после правок — по мере готовности API).

### 4.4. Тестирование Android

- Unit: use cases синка, разбор 409, формирование `batch_uid`.
- Android instrumented: Room DAO; опционально mock web server для Retrofit.
- Ручной чеклист: офлайн → N смен → онлайн → порядок батчей; конфликт → очередь стопится.

### 4.5. Доставка сборок

- **Внутреннее тестирование:** APK/AAB (Google Drive, внутренний канал) + `release` подписанный keystore в secure storage (не в git).
- **Дальше:** Google Play **Internal testing** для prod и отдельный трек или flavor для test API.

---

## 5. Тестирование backend

- **Unit:** сервис применения батча (порядок событий, идемпотентность, 409).
- **Integration:** Testcontainers PostgreSQL + реальные миграции + HTTP клиент к Ktor.
- **Контракт:** позже openapi + тесты совместимости для Android.

---

## 6. Что положить в репозиторий (артефакты плана)

Рекомендуется добавить по мере готовности (не обязательно всё сразу):

- `backend/Dockerfile`
- `deploy/docker-compose.test.yml`, `deploy/docker-compose.prod.yml` (или в корне с префиксами)
- `deploy/env.example.test`, `deploy/env.example.prod` — **без секретов**, только ключи
- `.github/workflows/ci.yml` (опционально): `gradle test`, `docker build`

---

## 7. Риски и как их закрыть

| Риск | Митигация |
|------|-----------|
| Часы на устройстве сбиты | Требовать корректный timezone; sanity-check на сервере (опционально). |
| Очередь батчей «поехала» | Строгий head-of-line + один поток отправки. |
| Потеря данных при краше | Room для PENDING/FAILED; на сервере `failed_sync_batches`. |
| Одинаковые секреты test/prod | Разные `.env` и разные JWT secret. |
| Недоступность VM с телефона | DNS, firewall, HTTPS. |

---

## 8. Краткий timeline (ориентир)

- **Недели 1–2:** Backend A–D на test Docker + интеграционные тесты батча.
- **Недели 3–5:** Android каркас + workers + офлайн смены + синк.
- **Неделя 6:** Прогон на prod stack, бэкапы, первый релиз APK internal.

Сроки плавают; жёстко завязаны на доступность тебя как единственного разработчика и на объём отчётов в MVP.
