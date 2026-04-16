# Apofeoz — Backend API

Ktor + PostgreSQL: JWT (access/refresh), пользователи, рабочие, пакетная синхронизация смен, отчёты.

Подробнее: [BACKEND_ARCHITECTURE.md](../docs/BACKEND_ARCHITECTURE.md).

## Требования

- **JDK 17+**
- **PostgreSQL 14+** (для тестов с Docker — см. ниже)
- **Docker** (опционально: БД и интеграционные тесты)

## Технологии

| Компонент   | Стек |
|------------|------|
| HTTP       | Ktor 2.3 (Netty) |
| БД         | PostgreSQL, HikariCP |
| Миграции   | Flyway (`src/main/resources/db/migration`) |
| ORM/DSL    | Exposed |
| Сериализация | kotlinx.serialization (JSON) |
| Пароли     | jBCrypt |
| Токены     | Auth0 java-jwt |

## Быстрый старт

### 1. Поднять PostgreSQL

```bash
cd backend
docker compose up -d
```

**Windows (одним шагом):** при запущенном Docker Desktop из каталога `backend` выполни `.\scripts\start-local.ps1` — поднимет Postgres и запустит `gradlew run` с дефолтным seed-админом `admin@local.test` / `AdminPass123!` (если переменные `SEED_ADMIN_*` не заданы).

Либо свой инстанс. Для **Docker Compose из репозитория** Postgres с хоста на порту **15432**: `jdbc:postgresql://localhost:15432/apofeoz`, пользователь/пароль `apofeoz`. Если порт снова «зарезервирован» Windows, в `docker-compose.yml` поменяй маппинг (например `25432:5432`) и выставь `JDBC_URL`. Свой Postgres на **5432** — `JDBC_URL=jdbc:postgresql://localhost:5432/apofeoz`.

### 2. Переменные окружения (опционально)

| Переменная | Назначение |
|------------|------------|
| `JDBC_URL` | JDBC URL (перекрывает `application.conf`) |
| `DB_USER` / `DB_PASSWORD` | Учётные данные БД |
| `JWT_SECRET` | HMAC секрет access-токена (**≥ 32 символов** в проде) |
| `JWT_ISSUER` / `JWT_AUDIENCE` | Проверка JWT |
| `REPORT_TIME_ZONE` | Зона календарных дней во всех отчётах и табеле; по умолчанию **`Europe/Moscow`** |
| `CORS_ALLOWED_ORIGINS` | Список origin через запятую для CORS (например `https://admin.example.com`); если пусто — `anyHost` |
| `ACCESS_TOKEN_MINUTES` | Срок жизни access JWT (по умолчанию из HOCON, обычно 15) |
| `REFRESH_TOKEN_DAYS` | Срок жизни refresh (по умолчанию из HOCON, обычно 30) |
| `SEED_ADMIN_EMAIL` / `SEED_ADMIN_PASSWORD` | Однократное создание администратора при старте |
| `SEED_ADMIN_PHONE` | Телефон для seed-админа (опционально) |

Для **локальных интеграционных тестов** можно задать системные свойства с теми же именами (`-DJDBC_URL=...`), если удобнее, чем env.

### 3. Запуск приложения

```bash
cd backend
./gradlew run          # Linux/macOS
.\gradlew.bat run      # Windows
```

Сервер слушает **8080** (см. `ktor.deployment` в `application.conf`).

- **Health:** `GET http://localhost:8080/health`
- **OpenAPI (черновик):** `GET http://localhost:8080/openapi.yaml`
- **API:** префикс `http://localhost:8080/api/v1/...`

Миграции Flyway выполняются при старте.

Логи: **SLF4J/Logback** (stdout). Для `POST /api/v1/sync/batch` пишутся **INFO/WARN/ERROR** с `userId`, `batchUid`, числом событий (см. `SyncService`). Необработанные исключения → **500** с телом `{"code":"internal_error",...}`.

## Сборка JAR

**Тонкий JAR** (нужны зависимости на classpath — для разработки неудобен):

```bash
./gradlew jar
```

**Fat JAR (Shadow)** — один файл со всеми зависимостями, удобен для деплоя:

```bash
./gradlew shadowJar
# build/libs/*-all.jar
```

Запуск:

```bash
java -jar build/libs/apofeoz-backend-0.1.0-SNAPSHOT-all.jar
```

(имя из `rootProject.name` в `settings.gradle.kts`, см. `build/libs/*-all.jar`)

## Docker-образ API

Из каталога `backend` (используется официальный образ **Gradle** для сборки внутри Docker, отдельный Unix `gradlew` не обязателен):

```bash
docker build -t apofeoz-backend .
docker run --rm -p 8080:8080 \
  -e JDBC_URL=jdbc:postgresql://host.docker.internal:15432/apofeoz \
  -e DB_USER=apofeoz \
  -e DB_PASSWORD=apofeoz \
  -e JWT_SECRET=your-32-plus-character-secret-here \
  apofeoz-backend
```

Подставьте свой `JDBC_URL` (например имя сервиса Postgres в `docker network`).

## Тесты

```bash
./gradlew test
```

- **`JwtServiceTest`** — без Docker, всегда выполняется.
- **`BackendIntegrationTest`** — Testcontainers + PostgreSQL; класс помечен `disabledWithoutDocker = true`, поэтому **без Docker тесты пропускаются**, а не падают. С установленным Docker проверяются сценарии: health, регистрация/логин, sync batch (идемпотентность), конфликт 409, **GET детали failed-batch** + DELETE, отчёт, запрет sync для роли USER.

## Структура кода

```
backend/src/main/kotlin/com/apofeoz/backend/
├── Application.kt       # Точка входа, wiring сервисов
├── AppConfig.kt         # Конфиг из HOCON + env
├── api/                 # DTO и ApiException
├── data/                # Exposed-таблицы, репозитории, пул БД
├── domain/              # Enums
├── plugins/             # Routing, Security, StatusPages, CORS, JSON
├── security/            # JWT
└── service/             # Бизнес-логика
```

## API (кратко)

| Метод | Путь | Доступ |
|-------|------|--------|
| POST | `/api/v1/auth/register` | публично |
| POST | `/api/v1/auth/login` | публично |
| POST | `/api/v1/auth/refresh` | публично |
| POST | `/api/v1/auth/logout` | JWT |
| GET | `/api/v1/users/me` | JWT |
| GET | `/api/v1/users` | ADMIN |
| PATCH | `/api/v1/users/{id}` | владелец (профиль) / ADMIN |
| GET/POST/PATCH | `/api/v1/workers` | FOREMAN / ADMIN |
| POST | `/api/v1/sync/batch` | FOREMAN (активный) |
| GET | `/api/v1/sync/failed-batches` | JWT (свои записи) |
| GET | `/api/v1/sync/failed-batches/{id}` | JWT (снимок `events[]` для экрана правки) |
| DELETE | `/api/v1/sync/failed-batches/{id}` | JWT |
| GET | `/api/v1/reports/hours-by-worker-previous-day?date=` | ADMIN |
| GET | `/api/v1/reports/hours-by-worker-range?from=&to=` | ADMIN |
| GET | `/api/v1/reports/timesheet.xlsx?from=&to=` | ADMIN (скачивание Excel-табеля) |

При **409** `foreman_has_workers` в `details.workerIds` приходит массив UUID подчинённых, которых нужно переназначить.

## Документация репозитория

- [Архитектура backend](../docs/BACKEND_ARCHITECTURE.md)
- [Отчёты и табель](../docs/REPORTING.md)
- [Мокап UI](../docs/MOBILE_UI_MOCKUP.md)
