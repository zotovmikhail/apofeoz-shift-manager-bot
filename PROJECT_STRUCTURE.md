# Структура проекта Apofeoz

```
apofeoz-shift-manager/
├── android/                # Android (Compose, Room, Retrofit, WorkManager)
│   ├── app/
│   │   ├── build.gradle.kts
│   │   └── src/main/
│   │       ├── AndroidManifest.xml
│   │       ├── java/com/apofeoz/shiftmanager/
│   │       │   ├── core/        # DI, сеть
│   │       │   ├── data/        # API DTO, Room, DataStore, репозитории
│   │       │   ├── presentation/# Compose UI
│   │       │   ├── work/        # WorkManager
│   │       │   ├── MainActivity.kt
│   │       │   └── ShiftManagerApplication.kt
│   │       └── res/
│   ├── gradle/wrapper/
│   ├── gradlew.bat
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   ├── local.properties.example
│   └── README.md
│
├── backend/                # Ktor API (PostgreSQL, Flyway, Exposed)
│   ├── src/main/kotlin/com/apofeoz/backend/
│   ├── src/main/resources/   # application.conf, db/migration, openapi.yaml
│   ├── docker-compose.yml
│   ├── Dockerfile
│   ├── env.example
│   └── README.md
│
├── docs/                   # Документация
│   ├── ADMIN_WEB.md        # план: веб-админка ADMIN (интернет, responsive)
│   ├── SYSTEM_ARCHITECTURE.md
│   ├── BACKEND_ARCHITECTURE.md
│   ├── MOBILE_ARCHITECTURE.md
│   ├── CODEX_PROJECT_STATE.md # каноничный current-state контракт для агентов
│   ├── AGENTS.md              # старт для Codex/агентов (read order, правила)
│   ├── DOC_MAINTENANCE_CHECKLIST.md
│   ├── IMPLEMENTATION_DEPLOYMENT_PLAN.md
│   ├── MOBILE_UI_MOCKUP.md
│   ├── ui-mockups/         # HTML-мокапы экранов (дизайн-референс)
│   ├── ui-reference/       # архивный UI-референс (Figma, токены, атрибуции)
│   ├── archive/            # неавторитетные исторические документы
│   └── ...
│
├── .gitignore
├── README.md
└── PROJECT_STRUCTURE.md
```
