# От Spring/Java к Kotlin + Ktor + Android в этом проекте

Краткий ориентир, если вы привыкли к **Java + Spring Boot** и **мобильный стек** для вас новый. Документ привязан к репозиторию **Apofeoz** (`backend/` + `android/`).

---

## 1. Общая карта «что за что отвечает»

| Привычно (Spring) | Здесь |
|-------------------|--------|
| `@SpringBootApplication` + авто-конфигурация | `Application.kt` → `fun Application.module()` — **вручную** вызываются `configureX()` и создаются репозитории/сервисы |
| `@RestController` + `@GetMapping` | `plugins/Routing.kt` — `route { get { } post { } }`, без аннотаций |
| `@Service` / `@Component` | Обычные классы `*Service`, передаются в функции конструктором (без контейнера DI, кроме Android — см. ниже) |
| `application.yml` + `@ConfigurationProperties` | `resources/application.conf` (HOCON) + **переменные окружения** в `AppConfig.load()` |
| Spring Data JPA / Hibernate | **Exposed** (Kotlin DSL поверх JDBC) + **Flyway** миграции |
| `Optional<T>` | Nullable-типы `T?` и операторы `?.`, `?:`, `!!` (последний — «я уверен, не null», избегать в новом коде) |
| Синхронные контроллеры | **`suspend`**-функции в сервисах и транзакциях Exposed — под капотом корутины |

---

## 2. Kotlin: минимум, чтобы читать и править код

### 2.1 Синтаксис, который встречается чаще всего

- **`val`** — неизменяемая ссылка, **`var`** — изменяемая (как `final` / не `final` для локальных полей).
- **Строковые шаблоны:** `"userId=$userId"` вместо конкатенации.
- **Data class** — автоматически `equals`/`hashCode`/`copy`; часто DTO в `api/Dtos.kt`.
- **Функции можно объявлять вне класса** (top-level) — например хелперы в файле `Routing.kt`.
- **`when`** — мощный `switch` (может быть выражением и возвращать значение).
- **`?.`** — безопасный вызов; **`?:`** — значение по умолчанию если null.
- **`!!`** — принудительно «не null»; при ошибке — `KotlinNullPointerException`. В прод-коде лучше явные проверки или `requireNotNull`.

### 2.2 Корутины и `suspend`

- **`suspend fun`** — функция, которая может **приостановиться** (ожидание I/O) без блокировки потока.
- В Ktor-обработчиках и в `newSuspendedTransaction { }` (Exposed) это нормальный стиль.
- Аналогия: не путать с «просто асинхронностью Spring WebFlux» — здесь нет обязательного `Mono`/`Flux`; часто просто цепочка `suspend`-вызовов.

Если нужно вызвать блокирующий JDBC/старый API из корутины — оборачивают в `withContext(Dispatchers.IO) { }` (в проекте это уже сделано внутри репозиториев через `newSuspendedTransaction`).

### 2.3 Объекты и компаньоны

- **`object X`** — синглтон (как enum из одного элемента, но не enum), например `DatabaseFactory`.
- **`companion object`** — статические члены класса в стиле Kotlin.

---

## 3. Backend: Ktor вместо Spring MVC

### 3.1 Точка входа и «сборка приложения»

Файл: `backend/src/main/kotlin/.../Application.kt`

- `EngineMain.main` — стандартный вход Ktor.
- В `module()` создаются репозитории и сервисы **в явном порядке**, затем `configureRouting(...)`, `configureSecurity(...)`, и т.д.
- Это ближе к **ручной сборке графа зависимостей**, чем к `@Autowired`.

### 3.2 Маршруты

Файл: `plugins/Routing.kt`

- Нет `@RequestMapping`: дерево `route("/api/v1") { post("/auth/login") { ... } }`.
- Тело: `call.receive<LoginRequest>()`, ответ: `call.respond(...)`, код: `call.respond(HttpStatusCode.Created, ...)`.
- Защита: `authenticate("auth-jwt") { ... }`, принципал — `call.principal<JwtUserPrincipal>()`.

### 3.3 Конфигурация

- `application.conf` — порт, хост, блок `app { jdbcUrl ... }`.
- Прод-переопределение: **env** (см. `AppConfig.kt`): `JDBC_URL`, `JWT_SECRET`, и т.д.

### 3.4 Ошибки API

- `ApiException` + `StatusPages` — единый формат ответа (аналог `@ControllerAdvice`, но проще и вручную).

### 3.5 БД: Exposed + Flyway

- **Flyway:** SQL-файлы в `resources/db/migration/` — как в Spring, миграции на старте в `DatabaseFactory.init()`.
- **Exposed:** таблицы описаны объектами (`Tables.kt`: `object Users : UUIDTable(...)`).
- Запросы: `Users.selectAll().where { ... }`, `insert`, `update` — DSL, не JPQL.
- Транзакции: `newSuspendedTransaction(Dispatchers.IO) { ... }` в репозиториях.

Ментально: это ближе к **jOOQ / Querydsl по духу**, чем к JPA-сущностям с ленивыми связями.

### 3.6 Слои (как ориентир)

```
api/Dtos.kt          — контракт JSON (kotlinx.serialization)
service/*Service.kt  — бизнес-правила (как @Service)
data/*Repository.kt  — доступ к БД
plugins/Routing.kt   — «контроллеры»
```

---

## 4. Android: чем отличается от «серверного» Kotlin

### 4.1 Сборка

- **`build.gradle.kts`** — Kotlin DSL для Gradle (синтаксис похож на Kotlin, не на Groovy).
- **`BuildConfig.API_BASE_URL`** — константы сборки (как профили Spring, но на уровне variant’а).

### 4.2 UI: Jetpack Compose

- Не XML-layout’ы и не Activity с `findViewById`, а **декларативное описание UI** функциями с аннотацией `@Composable`.
- Состояние: `remember { mutableStateOf(...) }`, реактивность через пересборку «дерева» composable-функций.
- Навигация: часто `NavHost` (см. `presentation/`).

Для чтения кода: ищите экраны в `presentation/*Screen.kt`, точка входа UI — `MainActivity` + `AppRoot`.

### 4.3 Сеть

- **Retrofit** + интерфейс `ApofeozApi` — как в Java, но методы могут быть **`suspend`**.
- **OkHttp** interceptors: заголовок `Authorization`, логирование, **Authenticator** для refresh при 401 (`TokenRefreshAuthenticator`).

### 4.4 Локальные данные

- **Room** — SQLite + аннотации (`@Entity`, `@Dao`); миграции схемы приложения отдельно от серверной БД.
- **DataStore** — замена SharedPreferences для токенов/флагов (`TokenRepository`).

### 4.5 Фоновая работа

- **WorkManager** — отложенные задачи с учётом сети и перезапуска процесса (очередь исходящих батчей: `OutboundSyncWorker`).

### 4.6 DI

- В этом MVP **нет Hilt/Koin**: зависимости в **`AppContainer`** (object, ручная `init` из `Application`). Для расширения проекта часто подключают Hilt — но текущий код проще для поиска: всё в одном месте.

---

## 5. Как безопасно вносить изменения

1. **Backend:** правка бизнес-логики — в `*Service.kt`; новый эндпоинт — `Routing.kt` + при необходимости DTO в `Dtos.kt`; схема БД — новая миграция `V2__....sql`, не править `V1` на уже развёрнутых стендах.
2. **Контракт API:** если меняете JSON — синхронно обновите DTO в Android (`data/remote/dto/ApiDtos.kt`) и проверьте вызовы в `ApofeozApi`.
3. **Тесты backend:** `./gradlew test` (интеграционные — Docker). **Android:** `gradlew test` при настроенном SDK.
4. **Форматирование:** в Kotlin принят отступ 4 пробела; в проекте местами встречаются лишние пустые строки в файлах — при редактировании можно аккуратно подтянуть под стиль IDE.

---

## 6. Куда смотреть дальше (официально и по делу)

- **Kotlin:** [Kotlin docs — Basics](https://kotlinlang.org/docs/basic-syntax.html), раздел Coroutines — когда начнёте править асинхронность.
- **Ktor:** [Ktor Server — Routing, Authentication, Serialization](https://ktor.io/docs/server.html).
- **Exposed:** [Exposed Wiki](https://github.com/JetBrains/Exposed/wiki).
- **Compose:** [Jetpack Compose pathway](https://developer.android.com/jetpack/compose/tutorial) (достаточно туториала и «State»).
- **Архитектура этого репо:** `docs/BACKEND_ARCHITECTURE.md`, `docs/MOBILE_ARCHITECTURE.md`, `docs/DEPLOYMENT_CHECKLIST.md`.

---

## 7. Шпаргалка «я искал в Spring, где это здесь?»

| Идея | Где в проекте |
|------|----------------|
| Главный класс | `Application.kt` |
| Контроллеры | `plugins/Routing.kt` |
| Фильтр безопасности / JWT | `plugins/Security.kt`, `security/JwtService.kt` |
| Репозитории | `data/*Repository.kt` |
| Миграции | `resources/db/migration/` |
| Глобальные исключения | `plugins/StatusPages.kt`, `api/ApiException.kt` |
| Конфиг | `AppConfig.kt` + `application.conf` |
| Android «точка входа» | `ShiftManagerApplication`, `MainActivity` |
| Сетевой клиент | `core/network/`, `data/remote/ApofeozApi.kt` |

Если нужно, этот документ можно дополнить разделом «типичные ошибки компилятора Kotlin» или примерами рефакторинга с Java на Kotlin на ваших задачах.
