# Apofeoz — Android

MVP-клиент: **Jetpack Compose**, **Retrofit**, **Room** (очередь исходящих батчей смен), **DataStore** (токены и локальная активная смена), **WorkManager** (синхронизация при сети).

## Требования

- **Android Studio** Ladybug+ или новее (рекомендуется)
- **JDK 17+** (или 21 — как в `app/build.gradle.kts`)
- **Android SDK** (см. `compileSdk` в `app/build.gradle.kts`), эмулятор **API 26+**

## Первый запуск

1. Скопируйте `local.properties.example` → `local.properties` и укажите `sdk.dir` (путь к SDK как в Android Studio → *SDK Location*).
2. Откройте каталог **`android/`** как проект в Android Studio (или корень монорепозитория, если IDE поддерживает composite).
3. Дождитесь Gradle Sync и соберите **Run** на эмуляторе или устройстве.

Командная строка (после настройки `local.properties` и `ANDROID_HOME`):

```bat
cd android
gradlew.bat assembleDebug
```

## Backend URL (на какой сервер ходит приложение)

Базовый URL задаётся в **`app/build.gradle.kts`** → `buildConfigField("String", "API_BASE_URL", "...")`.  
В рантайме он читается в [`AppContainer.kt`](./app/src/main/java/com/apofeoz/shiftmanager/core/di/AppContainer.kt) и передаётся в Retrofit.

**Формат:** корень API-сервера **без** суффикса `api/v1` — в интерфейсе [`ApofeozApi`](./app/src/main/java/com/apofeoz/shiftmanager/data/remote/ApofeozApi.kt) пути уже вида `api/v1/...`.

Примеры:

| Сценарий | Пример `API_BASE_URL` |
|----------|------------------------|
| Эмулятор, backend на ПК | `http://10.0.2.2:8080/` (**уже стоит в `defaultConfig`**) |

### Локально: эмулятор + backend на этом же компьютере

1. Запусти **Docker Desktop**, затем в каталоге `backend` подними API (см. `backend/scripts/start-local.ps1` или `docker compose up -d` + `gradlew.bat run`). Сервер слушает **8080** на всех интерфейсах (`0.0.0.0`).
2. В Android Studio собери **debug** — `API_BASE_URL` по умолчанию **`http://10.0.2.2:8080/`** (эмулятор так достучится до `localhost` хоста).
3. Если используешь скрипт `start-local.ps1`, залогинься админом: **`admin@local.test` / `AdminPass123!`** (после первого старта на пустой БД).
| Телефон в той же Wi‑Fi, backend на ПК | `http://192.168.x.x:8080/` |
| Тестовый стенд по HTTPS | `https://api-test.example.com/` |
| Прод | `https://api.example.com/` |

**Как поменять**

1. **Debug / по умолчанию** — блок `defaultConfig { buildConfigField(..., "http://...") }`.
2. **Release** — блок `buildTypes { release { ... buildConfigField ... } }` (сейчас заглушка `https://api.example.com/`).
3. **Несколько сред (рекомендуется)** — завести [product flavors](https://developer.android.com/build/build-variants), например `staging` и `prod`, у каждого свой `API_BASE_URL`, и собирать нужный вариант в Android Studio (*Build Variants*).

**HTTP и HTTPS**

- В манифесте включён **`usesCleartextTraffic="true"`** — чтобы работали `http://` для локальной разработки и теста.
- Для **продакшена** обычно только **HTTPS**; при желании отключите cleartext для `release` (отдельный манифест или `networkSecurityConfig`).

Поднимите PostgreSQL и backend (`backend/README.md`); первого админа — через `SEED_ADMIN_*` или вручную.

## Возможности MVP

| Роль | Экраны |
|------|--------|
| USER | Профиль, выход |
| FOREMAN | Рабочие, смены (старт/конец → очередь батчей), список ошибок sync с сервера |
| ADMIN | То же + отчёт «часы за день», создание рабочего с полем **UUID бригадира** |

### Сеть и токены

- **OkHttp Authenticator**: при **401** на защищённых запросах выполняется `POST /api/v1/auth/refresh`, токены обновляются в DataStore, запрос повторяется (один общий lock на refresh).
- При сетевой ошибке refresh токены не очищаются. Очистка токенов и `sessionExpired` выполняются только при явном отказе авторизации (`401/403`).
- При старте без сети приложение использует последнего сохранённого пользователя и owner-scoped кэш рабочих из DataStore. После явного `401/403` cached-вход блокируется до следующего успешного логина. Ручной выход очищает токены, cached profile и cached workers, но не удаляет локальные смены и очередь.

### Неуспешные батчи

- Список с сервера; по нажатию — **детали** (`GET .../failed-batches/{id}`) с **pretty JSON** `eventsSnapshot`.
- Кнопка **«Удалить запись на сервере»** (`DELETE .../failed-batches/{id}`).

Очередь **`outbound_batches`** в Room отправляется **WorkManager** при появлении сети (head-of-line по `id`) и хранит owner/device/worker/session metadata. После **409/400/403** проблемная сущность блокируется локально, запись сохраняется в Room `local_failed_batches`, а следующие pending batch того же работника атомарно переносятся из автоочереди в локальные deferred-ошибки. Batch других работников продолжают синхронизироваться.

## Тесты (unit)

```bash
cd android
gradlew.bat test
```

- `ApiDtoSerializationTest` — round-trip JSON для `SyncBatchRequestDto`.
- `OutboundBatchClaimRobolectricTest` — порядок `claimNextBatch()` в Room (Robolectric).

## Структура

```
app/src/main/java/com/apofeoz/shiftmanager/
├── core/di/AppContainer.kt
├── core/network/
├── data/local/          # Room, DataStore
├── data/remote/
├── data/repository/
├── presentation/
├── work/                # OutboundSyncWorker
├── MainActivity.kt
└── ShiftManagerApplication.kt
```

## Документация

- [MOBILE_ARCHITECTURE.md](../docs/MOBILE_ARCHITECTURE.md)
- [BACKEND_ARCHITECTURE.md](../docs/BACKEND_ARCHITECTURE.md)
