# Архитектура мобильных клиентов Apofeoz

## Обзор системы

Система с точки зрения мобильных клиентов состоит из:
1. **Android приложения** (Kotlin) — основной клиент с офлайн‑режимом
2. **Потенциального iOS‑клиента** (на базе Kotlin Multiplatform Mobile) — общий Kotlin‑код + нативный UI
3. **Планируемой веб‑админки** (браузер) для роли **ADMIN** — тот же API, развёртывание в интернете, адаптив под ПК и мобильный браузер; см. [`ADMIN_WEB.md`](./ADMIN_WEB.md)
4. **Backend API** (Kotlin + Ktor) — REST API сервер
5. **Базы данных** (PostgreSQL на сервере, SQLite/Room локально) — хранение данных
6. **Подсистемы синхронизации** — автоматическая синхронизация при появлении интернета

## Ключевые требования

- ✅ **Вход в приложение:** только **пароль** — саморегистрация и логин (без внешнего SSO).
- ✅ Офлайн: **смены** (старт/стоп) — да, через локальную очередь **батчей**; **добавление рабочего** — **только онлайн** (нет смысла в батчах для рабочих на текущем этапе).
- ✅ Локальное кэширование: данные сохраняются на устройстве
- ✅ Автоматическая синхронизация: при появлении интернета
- ✅ Конфликт-резолюшн: обработка конфликтов при синхронизации
- ✅ Очередь операций: в офлайн‑очередь попадают **только события сессий** (старт/конец смены); добавление, редактирование и деактивация рабочих выполняются только при наличии сети (через API). Очередь и список «неуспешных» батчей хранятся в **Room** (постоянное хранилище), а не в памяти — так они не теряются при закрытии приложения или смерти процесса (см. BACKEND_ARCHITECTURE, раздел про потерю сессии).

## Архитектура мобильных клиентов

### Технологический стек

- **Язык**: Kotlin (общий код) + Kotlin/Java (Android‑часть) + Swift/Compose Multiplatform (iOS‑UI, при необходимости)
- **Архитектура**: MVVM + Clean Architecture
- **Локальная БД (Android)**: Room Database
- **Сеть**: Retrofit + OkHttp (Android) / Ktor client (в общем KMM‑модуле)
- **DI**: Koin / Hilt (Android)
- **Coroutines** и **Flow**: для асинхронных операций и реактивных данных
- **WorkManager**: для фоновой синхронизации на Android
- **Тема интерфейса**: `presentation/theme/ApofeozTheme` — тёмный фон и золотой акцент (`#d4af37`) в духе HTML-мокапов `apofeoz_ui/`; шрифты **Inter** и **Space Grotesk** (variable TTF в `android/app/src/main/res/font/`, см. `android/FONT_NOTICE.md`)
- Папка `Мобильное приложение для АПОФЕОЗ/` используется только как архивный дизайн-референс (без исполняемого фронтенда).

### Логическая структура модулей

> На текущем этапе реализуется Android‑приложение. При переходе к KMM общий код переносится в модуль `:shared`, а Android‑код — в `:androidApp`. Для iOS будет добавлен `iosApp`.

Общая схема:

```text
shared/                         # общий Kotlin Multiplatform модуль (планируется)
├── src/commonMain/kotlin/com/apofeoz/shared/
│   ├── data/                   # источники данных, репозитории, sync‑логика
│   ├── domain/                 # usecases, доменные модели
│   └── core/                   # общие утилиты, ошибки, мапперы
├── src/androidMain/kotlin/…    # платформенный слой для Android (если нужен)
└── src/iosMain/kotlin/…        # платформенный слой для iOS (если нужен)

android/app/src/main/java/com/apofeoz/shiftmanager/
├── data/          # Локальная БД, API, репозитории (пока Android‑специфично)
├── domain/        # Domain модели, use cases (будут выноситься в shared/)
├── presentation/  # UI (Compose), ViewModels
└── core/          # Утилиты, DI, сеть
```

### Локальная база данных (Room, Android)

#### Entities

```kotlin
// User.kt
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: Int,
    val username: String?,
    val firstName: String,
    val lastName: String,
    val phone: String?,
    val role: String, // "admin", "foreman", "worker"
    val isActive: Boolean,
    val createdAt: Long,
    val syncedAt: Long? = null, // Время последней синхронизации
    val localId: String? = null // Временный ID для офлайн операций
)

// Worker.kt
@Entity(
    tableName = "workers",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class WorkerEntity(
    @PrimaryKey val id: Int? = null,
    val userId: Int,
    val firstName: String,
    val lastName: String?,
    val position: String?,
    val isActive: Boolean,
    val createdAt: Long,
    val syncedAt: Long? = null,
    val localId: String? = null
)

// WorkSession.kt
@Entity(
    tableName = "work_sessions",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = WorkerEntity::class,
            parentColumns = ["id"],
            childColumns = ["workerId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class WorkSessionEntity(
    @PrimaryKey val id: Int? = null,
    val userId: Int,
    val workerId: Int,
    val startTime: Long, // Unix timestamp
    val endTime: Long?,
    val totalHours: Double,
    val notes: String?,
    val createdAt: Long,
    val syncedAt: Long? = null,
    val localId: String? = null,
    val syncStatus: SyncStatus = SyncStatus.PENDING // PENDING, SYNCED, ERROR
)

enum class SyncStatus {
    PENDING,    // Ожидает синхронизации
    SYNCING,    // В процессе синхронизации
    SYNCED,     // Синхронизировано
    ERROR       // Ошибка синхронизации
}

// SyncQueue.kt - Очередь операций для синхронизации (только сессии; управление рабочими — только через онлайн API)
@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val operation: String, // "START_SESSION", "END_SESSION" — только события сессий
    val entityType: String, // "WorkSession"
    val entityId: String, // ID сущности (может быть localId)
    val data: String, // JSON данные операции
    val createdAt: Long,
    val retryCount: Int = 0,
    val status: SyncStatus = SyncStatus.PENDING
)
```

#### DAO

```kotlin
@Dao
interface WorkSessionDao {
    @Query("SELECT * FROM work_sessions WHERE userId = :userId ORDER BY startTime DESC")
    fun getSessionsByUser(userId: Int): Flow<List<WorkSessionEntity>>
    
    @Query("SELECT * FROM work_sessions WHERE workerId = :workerId AND endTime IS NULL")
    suspend fun getActiveSession(workerId: Int): WorkSessionEntity?
    
    @Query("SELECT * FROM work_sessions WHERE syncStatus = :status")
    suspend fun getPendingSessions(status: SyncStatus): List<WorkSessionEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: WorkSessionEntity): Long
    
    @Update
    suspend fun updateSession(session: WorkSessionEntity)
    
    @Query("UPDATE work_sessions SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: Int, status: SyncStatus)
}
```

### Синхронизация

#### SyncManager

Офлайн‑очередь и батч содержат только события сессий (START_SESSION, END_SESSION). Предпочтительный способ — формировать один батч из накопившихся событий и отправлять `POST /sync/batch` (формат см. в BACKEND_ARCHITECTURE). Ниже — упрощённый вариант по одной сессии.

Чтобы не было гонок (двойной старт/стоп, параллельная запись в очередь), доступ к очереди и локальной БД синхронизации должен быть **сериализован**:

- все операции «Старт/Стоп» и запуск синхронизации проходят через один `SyncManager`;
- внутри `SyncManager` используется `Mutex` или single-thread dispatcher (однопоточный scope) — это стандартный и распространённый подход в Kotlin/Android для таких задач (аналог serial queue/actor в других языках);
- в результате в любой момент времени изменением состояния сессий/очереди занимается только один поток.

Также важно правило **head-of-line blocking** для консистентности:

- очередь событий/батчей упорядочена; отправка на backend всегда идёт только от «головы» очереди;
- пока для данного потока (минимум — текущий пользователь/прораб, при необходимости можно детализировать до пары «прораб+воркер») существует более старый батч в состоянии `PENDING` или `FAILED`, новые батчи/события этого же потока **не отправляются** на сервер (они могут накапливаться в очереди, но не уходят вперёд);
- это гарантирует, что backend всегда видит события в том порядке, в котором они произошли, и не применяет новые изменения до тех пор, пока не разрулён старый конфликт или не синхронизирована голова очереди.

```kotlin
class SyncManager(
    private val apiService: ApiService,
    private val workSessionDao: WorkSessionDao,
    private val workerDao: WorkerDao,
    private val networkMonitor: NetworkMonitor,
    private val syncQueueDao: SyncQueueDao
) {
    suspend fun syncPendingData() {
        if (!networkMonitor.isConnected()) return
        
        // Отправка батча событий сессий (очередь содержит только START_SESSION / END_SESSION)
        syncSessionBatch()
        // При необходимости — подтянуть актуальный список рабочих с сервера (GET /workers).
        // Добавление/редактирование/деактивация рабочих в батч не входят, только через онлайн API.
    }
    
    private suspend fun syncWorkSessions() {
        val pendingSessions = workSessionDao.getPendingSessions(SyncStatus.PENDING)
        pendingSessions.forEach { session ->
            try {
                when {
                    session.id == null -> {
                        // Создание новой сессии
                        val response = apiService.createWorkSession(session.toDto())
                        workSessionDao.updateSession(session.copy(
                            id = response.id,
                            syncStatus = SyncStatus.SYNCED,
                            syncedAt = System.currentTimeMillis()
                        ))
                    }
                    session.endTime != null && session.syncStatus == SyncStatus.PENDING -> {
                        // Обновление завершенной сессии
                        apiService.updateWorkSession(session.id, session.toDto())
                        workSessionDao.updateSyncStatus(session.id, SyncStatus.SYNCED)
                    }
                }
            } catch (e: Exception) {
                workSessionDao.updateSyncStatus(session.id ?: 0, SyncStatus.ERROR)
            }
        }
    }
}
```

#### WorkManager для фоновой синхронизации

```kotlin
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        return try {
            syncManager.syncPendingData()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
```

### Repository Pattern

```kotlin
class WorkSessionRepository(
    private val localDataSource: WorkSessionDao,
    private val remoteDataSource: ApiService,
    private val syncManager: SyncManager,
    private val networkMonitor: NetworkMonitor
) {
    suspend fun startSession(userId: Int, workerId: Int, notes: String?): Result<WorkSession> {
        return try {
            val session = WorkSessionEntity(
                userId = userId,
                workerId = workerId,
                startTime = System.currentTimeMillis(),
                endTime = null,
                totalHours = 0.0,
                notes = notes,
                createdAt = System.currentTimeMillis(),
                syncStatus = SyncStatus.PENDING,
                localId = UUID.randomUUID().toString()
            )
            
            val id = localDataSource.insertSession(session)
            
            // Попытка синхронизации, если есть интернет
            if (networkMonitor.isConnected()) {
                syncManager.syncPendingData()
            }
            
            Result.success(session.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun getActiveSessions(userId: Int): Flow<List<WorkSession>> {
        return localDataSource.getSessionsByUser(userId)
            .map { it.map { entity -> entity.toDomain() } }
    }
}
```

## Backend API (Kotlin)

### Технологический стек

- **Framework**: Ktor (корутинный сервер)
- **База данных**: PostgreSQL (рекомендуется) или SQLite
- **Миграции**: Flyway
- **DI**: Koin или встроенный сервис‑локатор
- **Validation**: Kotlinx Serialization / custom validation
- **Auth**: JWT токены

### Структура проекта

```
backend/
├── src/main/kotlin/
│   ├── api/
│   │   ├── routes/          # API endpoints
│   │   └── middleware/      # Auth, CORS, etc.
│   ├── domain/
│   │   ├── models/          # Domain модели
│   │   └── services/        # Бизнес-логика
│   ├── data/
│   │   ├── database/        # DB конфигурация
│   │   ├── repositories/    # Репозитории
│   │   └── entities/        # DB entities
│   └── utils/
└── resources/
    └── application.conf     # Конфигурация
```

### API Endpoints (MVP, сверка с `BACKEND_ARCHITECTURE.md`)

```
POST   /api/v1/auth/register       # Саморегистрация (единственное создание user в MVP)
POST   /api/v1/auth/login
POST   /api/v1/auth/refresh
POST   /api/v1/auth/logout

GET    /api/v1/users/me
GET    /api/v1/users               # ADMIN
PATCH  /api/v1/users/{id}         # ADMIN (роли, статус; без POST /users)

GET    /api/v1/workers
POST   /api/v1/workers             # Только онлайн
PATCH  /api/v1/workers/{id}       # Перенос foreman_id, status; без редактирования ФИО в MVP

POST   /api/v1/sync/batch          # Единственный способ применить START/END смены
GET    /api/v1/sync/failed-batches # Опционально

GET    /api/v1/reports/hours-by-worker-previous-day   # ADMIN: за вчера, все workers (+бригадиры): hours, shiftEquivalent (8ч=1)
```

Отдельных **`POST/PUT /sessions`** в MVP **нет** — смены только событиями в батче.

### Токены на клиенте

Кратко (детали — в `BACKEND_ARCHITECTURE.md`, раздел «JWT access и refresh»):

- Хранить **access** (короткий TTL) и **refresh** (долгий) безопасно локально.
- **OkHttp interceptor:** при **401** один раз вызвать `POST /auth/refresh`, обновить access, повторить запрос; при неудаче — разлогин.
- Не вызывать параллельно несколько refresh (мьютекс).

### Пример Ktor Route

> Иллюстративный фрагмент в документации может устаревать. Реальная маршрутизация — по `BACKEND_ARCHITECTURE.md` (**нет** отдельного CRUD сессий REST, только `sync/batch`).

## Схема синхронизации

### Офлайн → Онлайн

1. **Пользователь фиксирует старт/стоп смены** (офлайн или онлайн)
   - В очередь батчей попадают события `START_SESSION` / `END_SESSION`; отдельный REST для смены **не вызывается**.
   - Сохраняется в локальную БД с `syncStatus = PENDING` (и при необходимости `localId`).

2. **Появляется интернет (или пользователь уже онлайн)**
   - WorkManager запускает синхронизацию
   - Клиент формирует батч только из событий сессий (START_SESSION, END_SESSION): уникальный `batch_uid` (UUID на батч) + список событий, отправляет `POST /sync/batch`. Управление рабочими в батч не входит — только через онлайн API. При повторе запроса передаётся тот же `batch_uid` (идемпотентность на стороне backend).
   - **Ретраи при проблемах с сетью:** перед тем как считать батч неуспешным, при таймауте, обрыве соединения или 5xx клиент повторяет запрос (например 2–3 попытки с экспоненциальной задержкой). Только после исчерпания ретраев — оставить батч в очереди на следующую синхронизацию или показать ошибку сети. Ответ **409 Conflict** ретраить не нужно (бизнес-ошибка); такой батч сразу помечается как «неуспешный» для экрана редактирования. Успех — **200 OK**.
   - Сервер возвращает реальные ID и актуальное состояние
   - Локальная БД обновляется с серверными ID

3. **Конфликт-резолюшн**
   - Если сервер вернул ошибку (например, дубликат)
   - Локальная запись помечается как `ERROR`
   - Пользователь получает уведомление

### Онлайн → Офлайн

1. **Пользователь работает онлайн**
   - Операции сразу отправляются на сервер
   - Локальная БД обновляется с серверными данными

2. **Пропадает интернет**
   - Все операции сохраняются локально
   - `syncStatus = PENDING`
   - Приложение продолжает работать

## Безопасность

- **JWT токены** для авторизации
- **HTTPS** для всех запросов
- **Certificate Pinning** в Android приложении
- **Валидация данных** на клиенте и сервере
- **Rate limiting** на API

## Производительность

- **Кэширование** часто используемых данных
- **Пагинация** для списков
- **Lazy loading** для больших данных
- **Оптимистичные обновления** UI
