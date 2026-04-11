# Примеры кода (устаревшие черновики)

> Этот файл содержит **черновые/исторические** примеры и не является источником истины для текущей реализации.
> Актуальный контракт синхронизации смен — в `docs/BACKEND_ARCHITECTURE.md` (батчи `START_SESSION`/`END_SESSION` через `POST /api/v1/sync/batch`, идентификатор смены — клиентский `sessionId`).

## Android приложение

### 1. Entity (Room)

```kotlin
// WorkSessionEntity.kt
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
    ],
    indices = [Index(value = ["userId"]), Index(value = ["workerId"])]
)
data class WorkSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int? = null,
    val userId: Int,
    val workerId: Int,
    val startTime: Long,
    val endTime: Long? = null,
    val totalHours: Double = 0.0,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val syncedAt: Long? = null,
    val localId: String? = UUID.randomUUID().toString(),
    val syncStatus: SyncStatus = SyncStatus.PENDING
)

enum class SyncStatus {
    PENDING,
    SYNCING,
    SYNCED,
    ERROR
}

@TypeConverter
class SyncStatusConverter {
    @TypeConverter
    fun fromSyncStatus(status: SyncStatus): String = status.name
    
    @TypeConverter
    fun toSyncStatus(status: String): SyncStatus = SyncStatus.valueOf(status)
}
```

### 2. DAO

```kotlin
// WorkSessionDao.kt
@Dao
interface WorkSessionDao {
    @Query("SELECT * FROM work_sessions WHERE userId = :userId ORDER BY startTime DESC")
    fun getSessionsByUser(userId: Int): Flow<List<WorkSessionEntity>>
    
    @Query("SELECT * FROM work_sessions WHERE workerId = :workerId AND endTime IS NULL")
    suspend fun getActiveSession(workerId: Int): WorkSessionEntity?
    
    @Query("SELECT * FROM work_sessions WHERE syncStatus = :status")
    suspend fun getPendingSessions(status: SyncStatus): List<WorkSessionEntity>
    
    @Query("SELECT * FROM work_sessions WHERE id = :id")
    suspend fun getSessionById(id: Int): WorkSessionEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: WorkSessionEntity): Long
    
    @Update
    suspend fun updateSession(session: WorkSessionEntity)
    
    @Query("UPDATE work_sessions SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: Int, status: SyncStatus)
    
    @Query("UPDATE work_sessions SET syncStatus = :status WHERE localId = :localId")
    suspend fun updateSyncStatusByLocalId(localId: String, status: SyncStatus)
    
    @Delete
    suspend fun deleteSession(session: WorkSessionEntity)
}
```

### 3. Database

```kotlin
// AppDatabase.kt
@Database(
    entities = [
        UserEntity::class,
        WorkerEntity::class,
        WorkSessionEntity::class,
        SyncQueueEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(SyncStatusConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun workerDao(): WorkerDao
    abstract fun workSessionDao(): WorkSessionDao
    abstract fun syncQueueDao(): SyncQueueDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "apofeoz_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
```

### 4. Repository

```kotlin
// WorkSessionRepository.kt
class WorkSessionRepository(
    private val localDataSource: WorkSessionDao,
    private val remoteDataSource: SessionApi,
    private val syncManager: SyncManager,
    private val networkMonitor: NetworkMonitor
) {
    suspend fun startSession(
        userId: Int,
        workerId: Int,
        notes: String?
    ): Result<WorkSession> {
        return try {
            val session = WorkSessionEntity(
                userId = userId,
                workerId = workerId,
                startTime = System.currentTimeMillis(),
                endTime = null,
                totalHours = 0.0,
                notes = notes,
                syncStatus = SyncStatus.PENDING,
                localId = UUID.randomUUID().toString()
            )
            
            val id = localDataSource.insertSession(session)
            val savedSession = session.copy(id = id.toInt())
            
            // Попытка синхронизации, если есть интернет
            if (networkMonitor.isConnected()) {
                syncManager.syncPendingData()
            }
            
            Result.success(savedSession.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun endSession(sessionId: Int, endTime: Long? = null): Result<WorkSession> {
        return try {
            val session = localDataSource.getSessionById(sessionId)
                ?: return Result.failure(Exception("Session not found"))
            
            val actualEndTime = endTime ?: System.currentTimeMillis()
            val duration = actualEndTime - session.startTime
            val totalHours = duration / 3600000.0 // миллисекунды в часы
            
            val updatedSession = session.copy(
                endTime = actualEndTime,
                totalHours = totalHours,
                syncStatus = SyncStatus.PENDING
            )
            
            localDataSource.updateSession(updatedSession)
            
            // Попытка синхронизации
            if (networkMonitor.isConnected()) {
                syncManager.syncPendingData()
            }
            
            Result.success(updatedSession.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun getActiveSessions(userId: Int): Flow<List<WorkSession>> {
        return localDataSource.getSessionsByUser(userId)
            .map { sessions ->
                sessions
                    .filter { it.endTime == null }
                    .map { it.toDomain() }
            }
    }
    
    fun getAllSessions(userId: Int): Flow<List<WorkSession>> {
        return localDataSource.getSessionsByUser(userId)
            .map { it.map { entity -> entity.toDomain() } }
    }
}
```

### 5. SyncManager (упрощённый пример; в реальном приложении используется батч `POST /sync/batch` и head-of-line blocking, см. `MOBILE_ARCHITECTURE.md`)

```kotlin
// SyncManager.kt
class SyncManager(
    private val apiService: ApiService,
    private val workSessionDao: WorkSessionDao,
    private val workerDao: WorkerDao,
    private val networkMonitor: NetworkMonitor
) {
    suspend fun syncPendingData() {
        if (!networkMonitor.isConnected()) {
            return
        }
        
        try {
            syncWorkSessions()
            syncWorkers()
        } catch (e: Exception) {
            Log.e("SyncManager", "Error syncing data", e)
        }
    }
    
    private suspend fun syncWorkSessions() {
        val pendingSessions = workSessionDao.getPendingSessions(SyncStatus.PENDING)
        
        pendingSessions.forEach { session ->
            try {
                when {
                    session.id == null || session.localId != null -> {
                        // Создание новой сессии
                        val dto = session.toDto()
                        val response = apiService.createWorkSession(dto)
                        
                        workSessionDao.updateSession(
                            session.copy(
                                id = response.id,
                                syncStatus = SyncStatus.SYNCED,
                                syncedAt = System.currentTimeMillis(),
                                localId = null
                            )
                        )
                    }
                    session.endTime != null && session.syncStatus == SyncStatus.PENDING -> {
                        // Обновление завершенной сессии
                        val dto = session.toDto()
                        apiService.updateWorkSession(session.id!!, dto)
                        
                        workSessionDao.updateSyncStatus(
                            session.id!!,
                            SyncStatus.SYNCED
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("SyncManager", "Error syncing session ${session.id}", e)
                if (session.id != null) {
                    workSessionDao.updateSyncStatus(session.id!!, SyncStatus.ERROR)
                }
            }
        }
    }
    
    private suspend fun syncWorkers() {
        // Аналогичная логика для рабочих
    }
}
```

### 6. ViewModel

```kotlin
// SessionViewModel.kt
class SessionViewModel(
    private val sessionRepository: WorkSessionRepository,
    private val workerRepository: WorkerRepository,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()
    
    val activeSessions = sessionRepository.getActiveSessions(
        _uiState.value.currentUserId
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    
    fun startSession(workerId: Int, notes: String?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            val result = sessionRepository.startSession(
                userId = _uiState.value.currentUserId,
                workerId = workerId,
                notes = notes
            )
            
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        message = "Смена начата"
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message
                    )
                }
            )
        }
    }
    
    fun endSession(sessionId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            val result = sessionRepository.endSession(sessionId)
            
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        message = "Смена завершена"
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message
                    )
                }
            )
        }
    }
}

data class SessionUiState(
    val currentUserId: Int = 0,
    val isLoading: Boolean = false,
    val message: String? = null,
    val error: String? = null
)
```

### 7. Compose Screen

```kotlin
// ActiveSessionsScreen.kt
@Composable
fun ActiveSessionsScreen(
    viewModel: SessionViewModel = hiltViewModel(),
    onSessionClick: (Int) -> Unit,
    onEndSession: (Int) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val activeSessions by viewModel.activeSessions.collectAsState()
    val isOnline by networkMonitor.isOnline.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Активные смены") },
                navigationIcon = {
                    IconButton(onClick = { /* Navigate back */ }) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Статус синхронизации
            SyncStatusIndicator(isOnline = isOnline)
            
            // Список активных смен
            if (activeSessions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Нет активных смен")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(activeSessions) { session ->
                        ActiveSessionCard(
                            session = session,
                            onEndClick = { onEndSession(session.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveSessionCard(
    session: WorkSession,
    onEndClick: () -> Unit
) {
    val duration = remember(session.startTime) {
        derivedStateOf {
            System.currentTimeMillis() - session.startTime
        }
    }
    
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
        }
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = session.workerName,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Начало: ${formatTime(session.startTime)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Длительность: ${formatDuration(duration.value)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            session.notes?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onEndClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Stop, "Завершить")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Завершить смену")
            }
        }
    }
}
```

### 8. Network Monitor

```kotlin
// NetworkMonitor.kt
class NetworkMonitor @Inject constructor(
    private val context: Context
) {
    private val connectivityManager = context.getSystemService<ConnectivityManager>()!!
    
    val isOnline: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }
            
            override fun onLost(network: Network) {
                trySend(false)
            }
        }
        
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        connectivityManager.registerNetworkCallback(request, callback)
        
        // Проверка текущего состояния
        val currentState = connectivityManager.activeNetwork != null
        trySend(currentState)
        
        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }
    
    fun isConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
```

### 9. WorkManager для синхронизации

```kotlin
// SyncWorker.kt
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        return try {
            val syncManager = (applicationContext as ApofeozApplication)
                .appComponent.syncManager
            
            syncManager.syncPendingData()
            
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
    
    companion object {
        fun startSync(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            
            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}
```

## Backend API (Ktor)

Примеры ниже соответствуют текущей архитектуре:
- backend на Ktor + корутинах;
- синхронизация мобильного клиента с backend происходит через `POST /sync/batch` (батчи событий сессий, START_SESSION/END_SESSION), а не через поштучные вызовы создания/обновления сессий; примеры `/sessions` показаны как иллюстрация слоёв (routing/service/repository).

### 1. Route

```kotlin
// SessionRoutes.kt
fun Route.sessionRoutes(sessionService: SessionService) {
    route("/sessions") {
        authenticate {
            get {
                val userId = call.principal<UserIdPrincipal>()?.name?.toInt() ?: run {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@get
                }
                
                val sessions = sessionService.getSessionsByUser(userId)
                call.respond(sessions)
            }
            
            post {
                val userId = call.principal<UserIdPrincipal>()?.name?.toInt() ?: run {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }
                
                val request = call.receive<CreateSessionRequest>()
                val session = sessionService.startSession(
                    userId = userId,
                    workerId = request.workerId,
                    notes = request.notes
                )
                call.respond(session)
            }
            
            put("/{id}") {
                val sessionId = call.parameters["id"]?.toInt() ?: run {
                    call.respond(HttpStatusCode.BadRequest)
                    return@put
                }
                
                val request = call.receive<UpdateSessionRequest>()
                val session = sessionService.endSession(
                    sessionId = sessionId,
                    endTime = request.endTime
                )
                call.respond(session)
            }
        }
    }
}
```

### 2. Service

```kotlin
// SessionService.kt
class SessionService(
    private val sessionRepository: SessionRepository
) {
    suspend fun startSession(
        userId: Int,
        workerId: Int,
        notes: String?
    ): WorkSessionDto {
        val session = WorkSession(
            userId = userId,
            workerId = workerId,
            startTime = System.currentTimeMillis(),
            endTime = null,
            totalHours = 0.0,
            notes = notes
        )
        
        return sessionRepository.create(session).toDto()
    }
    
    suspend fun endSession(
        sessionId: Int,
        endTime: Long? = null
    ): WorkSessionDto {
        val session = sessionRepository.findById(sessionId)
            ?: throw NotFoundException("Session not found")
        
        val actualEndTime = endTime ?: System.currentTimeMillis()
        val duration = actualEndTime - session.startTime
        val totalHours = duration / 3600000.0
        
        val updatedSession = session.copy(
            endTime = actualEndTime,
            totalHours = totalHours
        )
        
        return sessionRepository.update(updatedSession).toDto()
    }
    
    suspend fun getSessionsByUser(userId: Int): List<WorkSessionDto> {
        return sessionRepository.findByUserId(userId)
            .map { it.toDto() }
    }
}
```

### 3. Repository

```kotlin
// SessionRepository.kt
class SessionRepository(
    private val database: Database
) {
    suspend fun create(session: WorkSession): WorkSession {
        return database.transaction {
            WorkSessions.insert {
                it[userId] = session.userId
                it[workerId] = session.workerId
                it[startTime] = session.startTime
                it[endTime] = session.endTime
                it[totalHours] = session.totalHours
                it[notes] = session.notes
                it[createdAt] = System.currentTimeMillis()
            }.resultedValues?.first()?.toDomain() ?: throw Exception("Failed to create session")
        }
    }
    
    suspend fun findById(id: Int): WorkSession? {
        return database.transaction {
            WorkSessions.select { WorkSessions.id eq id }
                .firstOrNull()
                ?.toDomain()
        }
    }
    
    suspend fun findByUserId(userId: Int): List<WorkSession> {
        return database.transaction {
            WorkSessions.select { WorkSessions.userId eq userId }
                .map { it.toDomain() }
        }
    }
}
```

