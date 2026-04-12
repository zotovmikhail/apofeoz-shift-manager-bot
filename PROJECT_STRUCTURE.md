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
│   ├── IMPLEMENTATION_DEPLOYMENT_PLAN.md
│   ├── MOBILE_UI_MOCKUP.md
│   └── ...
│
├── apofeoz_ui/             # HTML-мокапы экранов (дизайн-референс)
│
├── Мобильное приложение для АПОФЕОЗ/  # reference-only: ссылка на Figma + токены, без runtime-кода
│
├── .gitignore
├── README.md
└── PROJECT_STRUCTURE.md
```
