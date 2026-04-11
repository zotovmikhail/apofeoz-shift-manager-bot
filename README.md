# Apofeoz

Система учёта смен для строительной компании: мобильные клиенты на Kotlin и backend API.

## 📦 Структура проекта

```
apofeoz-shift-manager/
├── android/      # Android приложение (Kotlin + Jetpack Compose)
├── backend/      # Backend API (Kotlin + Ktor)
└── docs/         # Документация и мокапы
```

Подробное описание структуры: `PROJECT_STRUCTURE.md`.

## 🚀 Компоненты

### 1. Мобильные приложения (`android/` + KMM)

Основной клиент — Android‑приложение с офлайн‑режимом и автоматической синхронизацией.

**MVP:** вход — **саморегистрация по паролю** (JWT access/refresh); смены на сервер — **только батчами** `sync/batch`; рабочие — **только онлайн**. Подробности в `docs/BACKEND_ARCHITECTURE.md`.

**Технологии (Android):** Kotlin, Jetpack Compose, Room, Retrofit, WorkManager  
**Потенциал:** вынесение бизнес‑логики в общий Kotlin Multiplatform‑модуль для последующей iOS‑версии.

**Документация:** [android/README.md](./android/README.md)  
**Архитектура и офлайн‑синхронизация:** [docs/MOBILE_ARCHITECTURE.md](./docs/MOBILE_ARCHITECTURE.md)  
**Мокапы:** [docs/mobile_mockup.html](./docs/mobile_mockup.html)

### 2. Backend API (`backend/`)
REST API для мобильных клиентов с поддержкой офлайн‑синхронизации и ролей пользователей.

**Технологии:** Kotlin, Ktor, PostgreSQL, Flyway, Exposed

**Документация:** [backend/README.md](./backend/README.md)  
**Архитектура backend и модель данных:** [docs/BACKEND_ARCHITECTURE.md](./docs/BACKEND_ARCHITECTURE.md)

## 📖 Документация

- **Веб-админка (ADMIN, план):** [docs/ADMIN_WEB.md](./docs/ADMIN_WEB.md)
- **Общая архитектура системы:** [docs/SYSTEM_ARCHITECTURE.md](./docs/SYSTEM_ARCHITECTURE.md)
- **Архитектура мобильного приложения и синхронизации:** [docs/MOBILE_ARCHITECTURE.md](./docs/MOBILE_ARCHITECTURE.md)
- **Архитектура backend:** [docs/BACKEND_ARCHITECTURE.md](./docs/BACKEND_ARCHITECTURE.md)
- **Мокапы UI:** [docs/mobile_mockup.html](./docs/mobile_mockup.html)
- **План реализации, деплоя и сред test/prod:** [docs/IMPLEMENTATION_DEPLOYMENT_PLAN.md](./docs/IMPLEMENTATION_DEPLOYMENT_PLAN.md)
- **Чеклист готовности к test/prod и настройка URL в Android:** [docs/DEPLOYMENT_CHECKLIST.md](./docs/DEPLOYMENT_CHECKLIST.md)
- **Для Java/Spring-разработчика:** Kotlin, Ktor и Android в этом репо — [docs/JAVA_SPRING_TO_KOTLIN_KTOR_ANDROID.md](./docs/JAVA_SPRING_TO_KOTLIN_KTOR_ANDROID.md)
- **План реализации мобильного клиента (детали, частично устарел):** [docs/MOBILE_IMPLEMENTATION_PLAN.md](./docs/MOBILE_IMPLEMENTATION_PLAN.md)
- **Примеры кода:** [docs/CODE_EXAMPLES.md](./docs/CODE_EXAMPLES.md)
- **Структура проекта:** [PROJECT_STRUCTURE.md](./PROJECT_STRUCTURE.md)

## 👨‍💻 Для агентского режима (Cursor/AI)

- **Где искать архитектуру и соглашения:**
  - общая картина — `docs/SYSTEM_ARCHITECTURE.md`;
  - мобильные клиенты и офлайн‑синхронизация — `docs/MOBILE_ARCHITECTURE.md`;
  - backend и модель БД — `docs/BACKEND_ARCHITECTURE.md`;
  - структура репозитория — `PROJECT_STRUCTURE.md`;
  - переход с Spring/Java — `docs/JAVA_SPRING_TO_KOTLIN_KTOR_ANDROID.md`.
- **Основные точки входа в код:**
  - backend: `backend/src/main/kotlin/com/apofeoz/backend/…`;
  - android: `android/app/src/main/java/com/apofeoz/shiftmanager/…`.

## 🛠 Быстрый старт

### Android App
```bash
cd android
# Скопируйте local.properties → local.properties, укажите sdk.dir
# Откройте android/ в Android Studio → Run
```

### Backend API
```bash
cd backend
docker compose up -d   # или свой Postgres
.\gradlew.bat run      # Windows; см. backend/README.md
```

## 📝 Лицензия

[Укажите лицензию]

## 👥 Авторы

[Укажите авторов]
