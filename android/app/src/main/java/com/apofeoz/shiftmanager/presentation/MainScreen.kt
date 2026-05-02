package com.apofeoz.shiftmanager.presentation

import android.os.Environment
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.border
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apofeoz.shiftmanager.core.di.AppContainer
import com.apofeoz.shiftmanager.data.local.CachedActiveSessions
import com.apofeoz.shiftmanager.data.local.LocalActiveSession
import com.apofeoz.shiftmanager.data.local.SessionStateRepository
import com.apofeoz.shiftmanager.data.remote.dto.CreateWorkerRequestDto
import com.apofeoz.shiftmanager.data.remote.dto.FailedBatchListItemDto
import com.apofeoz.shiftmanager.data.remote.dto.RefreshRequestDto
import com.apofeoz.shiftmanager.data.remote.dto.SyncBatchRequestDto
import com.apofeoz.shiftmanager.data.remote.dto.SyncEventDto
import com.apofeoz.shiftmanager.data.remote.dto.UserResponseDto
import com.apofeoz.shiftmanager.data.remote.dto.WorkerResponseDto
import com.apofeoz.shiftmanager.presentation.failed.FailedBatchDetailScreen
import com.apofeoz.shiftmanager.presentation.theme.ApofeozColors
import com.apofeoz.shiftmanager.work.OutboundSyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import retrofit2.HttpException
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    user: UserResponseDto,
    onLoggedOut: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    val isForeman = user.role == "FOREMAN"
    val isAdmin = user.role == "ADMIN"
    val tabs = buildList {
        if (isForeman || isAdmin) add("Рабочие")
        if (isAdmin) add("Пользователи")
        if (isAdmin) add("Отчёт")
        add("Профиль")
    }

    val isOnline by AppContainer.networkStatus.isOnlineFlow().collectAsState(initial = true)
    val snackbarHostState = remember { SnackbarHostState() }
    var lastOnline by remember { mutableStateOf<Boolean?>(null) }
    var pendingBatches by remember { mutableStateOf(0) }

    LaunchedEffect(isOnline) {
        val prev = lastOnline
        lastOnline = isOnline
        if (prev != null && prev != isOnline) {
            snackbarHostState.showSnackbar(if (isOnline) "Интернет появился" else "Интернет пропал")
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            pendingBatches = runCatching { AppContainer.batchQueue.pendingCount() }.getOrDefault(0)
            delay(5_000)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        IdentityBadge(userInitials(user))
                        Column {
                            Text(
                                "Апофеоз".uppercase(Locale.getDefault()),
                                style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 2.4.sp),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false,
                            )
                            Text(
                                "мобильный терминал",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    Row(
                        modifier = Modifier.padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.End) {
                            if (pendingBatches > 0) {
                                StatusChip("Очередь: $pendingBatches", accent = true)
                            }
                            if (!isOnline) {
                                StatusChip("OFFLINE", warning = true)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                userRoleTitle(user.role),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                            Text(
                                userShortLine(user),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false,
                                modifier = Modifier.widthIn(max = 128.dp),
                            )
                        }
                        IdentityBadge(userInitials(user), modifier = Modifier.size(40.dp))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            val tabIndex = tab.coerceAtMost(tabs.lastIndex)
            PrimaryScrollableTabRow(
                selectedTabIndex = tabIndex,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onSurface,
                edgePadding = 12.dp,
                divider = {},
            ) {
                tabs.forEachIndexed { i, title ->
                    Tab(
                        selected = tab == i,
                        onClick = { tab = i },
                        text = {
                            Row(
                                modifier = Modifier.wrapContentWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = when (title) {
                                        "Рабочие" -> Icons.Filled.People
                                        "Пользователи" -> Icons.Filled.Shield
                                        "Отчёт" -> Icons.Filled.Assessment
                                        else -> Icons.Filled.Person
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    title,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Visible,
                                )
                            }
                        },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tabs.getOrNull(tab)) {
                    "Рабочие" -> WorkersTab(user, snackbarHostState)
                    "Пользователи" -> AdminUsersTab(user.id)
                    "Отчёт" -> ReportTab()
                    "Профиль" -> ProfileTab(user, isOnline, onLoggedOut)
                }
            }
        }
    }
}

@Composable
private fun ProfileTab(user: UserResponseDto, isOnline: Boolean, onLoggedOut: () -> Unit) {
    val scope = rememberCoroutineScope()
    val queue = AppContainer.batchQueue
    val syncStatus = AppContainer.syncStatusRepository
    val failedLocal = AppContainer.localFailedBatches
    val pendingActions = AppContainer.pendingSessionActions
    val json = AppContainer.jsonFormat
    var pending by remember { mutableStateOf(0) }
    var lastSyncAtText by remember { mutableStateOf("—") }
    var localFailed by remember { mutableStateOf<List<com.apofeoz.shiftmanager.data.local.LocalFailedBatch>>(emptyList()) }
    var localFailedMsg by remember { mutableStateOf<String?>(null) }
    var showClearLocalFailedDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        pending = queue.pendingCount()
        lastSyncAtText = syncStatus.getLastSyncAt()?.toString() ?: "—"
        localFailed = failedLocal.list()
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ApofeozPanel(modifier = Modifier.fillMaxWidth(), accent = true) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeading(
                    eyebrow = "Текущая сессия",
                    title = "${user.firstName} ${user.lastName}",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip(userRoleTitle(user.role), accent = user.role == "ADMIN")
                    StatusChip(if (user.status == "ACTIVE") "Активен" else "Неактивен", warning = user.status != "ACTIVE")
                    StatusChip(if (isOnline) "ONLINE" else "OFFLINE", warning = !isOnline)
                }
                Text("Логин: ${user.email ?: user.phone ?: "—"}")
                user.phone?.let { Text("Телефон: $it") }
            }
        }
        if (user.role == "FOREMAN") {
            val context = LocalContext.current
            val testOverride = AppContainer.testConnectivityOverride
            val forceOffline by testOverride.forceOfflineFlow.collectAsState(initial = false)
            ApofeozPanel(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Тест: симуляция offline", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Только для проверки очереди и UI. Пока включено, отправка батчей на сервер не выполняется.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Считать приложение offline", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = forceOffline,
                            onCheckedChange = { checked ->
                                scope.launch {
                                    testOverride.setForceOffline(checked)
                                    if (!checked) {
                                        OutboundSyncScheduler.schedule(context.applicationContext)
                                    }
                                }
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            ApofeozPanel(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Синхронизация", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusChip("Очередь: $pending", accent = pending > 0)
                        StatusChip("SYNC", accent = true)
                    }
                    Text("Последняя синхронизация (UTC): $lastSyncAtText", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = {
                        scope.launch {
                            pending = queue.pendingCount()
                            lastSyncAtText = syncStatus.getLastSyncAt()?.toString() ?: "—"
                        }
                    }) { Text("Обновить") }
                }
            }
            Spacer(Modifier.height(8.dp))
            SectionHeading(eyebrow = "Сбойные батчи", title = "Ошибки синхронизации")
            FailedTab()

            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Локальные ошибки отправки (400/403/409)",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    enabled = localFailed.isNotEmpty(),
                    onClick = { showClearLocalFailedDialog = true },
                ) { Text("Очистить все") }
            }
            localFailedMsg?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (localFailed.isEmpty()) {
                Text("Нет", style = MaterialTheme.typography.bodyMedium)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    localFailed.forEach { item ->
                        val isDeferred = item.reason?.startsWith("blocked_by_previous_") == true
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("HTTP ${item.httpCode}: ${item.message}", style = MaterialTheme.typography.bodyMedium)
                                item.reason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                Text("submittedAt: ${item.submittedAt}", style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        enabled = !isDeferred,
                                        onClick = {
                                            scope.launch {
                                                localFailedMsg = null
                                                runCatching {
                                                    val dto = json.decodeFromString(SyncBatchRequestDto.serializer(), item.bodyJson)
                                                    queue.enqueue(dto)
                                                    failedLocal.remove(item.id)
                                                    localFailed = failedLocal.list()
                                                }.onFailure { e ->
                                                    localFailedMsg = e.message ?: e.toString()
                                                }
                                            }
                                        },
                                    ) { Text(if (isDeferred) "Отложено" else "Переотправить") }
                                    TextButton(onClick = {
                                        scope.launch {
                                            localFailedMsg = null
                                            runCatching {
                                                // снять блокировки по содержимому батча
                                                val dto = json.decodeFromString(SyncBatchRequestDto.serializer(), item.bodyJson)
                                                dto.events.forEach { ev ->
                                                    when (ev.type) {
                                                        "START_SESSION" -> {
                                                            val wid = ev.payload.jsonObject["workerId"]?.jsonPrimitive?.content
                                                            if (!wid.isNullOrBlank()) pendingActions.clearBlockedForWorker(wid)
                                                        }
                                                        "END_SESSION" -> {
                                                            val sid = ev.payload.jsonObject["sessionId"]?.jsonPrimitive?.content
                                                            if (!sid.isNullOrBlank()) {
                                                                pendingActions.clearBlockedForSession(sid)
                                                                pendingActions.removeEnding(sid)
                                                            }
                                                        }
                                                    }
                                                }
                                                failedLocal.remove(item.id)
                                                localFailed = failedLocal.list()
                                            }.onFailure { e ->
                                                localFailedMsg = e.message ?: e.toString()
                                            }
                                        }
                                    }) { Text("Удалить") }
                                }
                            }
                        }
                    }
                }
            }
            TextButton(onClick = {
                scope.launch { localFailed = failedLocal.list() }
            }) { Text("Обновить список") }
        }
        if (user.role == "USER") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Text(
                    "Для работы с рабочими и сменами обратитесь к администратору, чтобы получить права FOREMAN.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Button(onClick = {
            scope.launch {
                runCatching {
                    val r = AppContainer.tokenRepository.getRefreshToken()
                    if (!r.isNullOrBlank()) {
                        AppContainer.api.logout(RefreshRequestDto(r))
                    }
                }
                AppContainer.tokenRepository.clear()
                AppContainer.authStateRepository.setAuthRejected(false)
                AppContainer.cachedUserRepository.clear()
                AppContainer.cachedWorkersRepository.clear()
                onLoggedOut()
            }
        }) { Text("Выйти") }
    }
    if (showClearLocalFailedDialog) {
        AlertDialog(
            onDismissRequest = { showClearLocalFailedDialog = false },
            title = { Text("Очистить все локальные ошибки?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Будут удалены все сохранённые на этом устройстве записи об ответах сервера с кодами 400, 403 и 409. Восстановить список будет нельзя.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Тела батчей из списка исчезнут — переотправить те же события через «Переотправить» уже не получится, только вручную заново из приложения.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Записи на сервере (экран «Ошибки синхронизации») не затрагиваются.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            localFailedMsg = null
                            runCatching {
                                val snapshot = failedLocal.list()
                                for (item in snapshot) {
                                    runCatching {
                                        val dto = json.decodeFromString(SyncBatchRequestDto.serializer(), item.bodyJson)
                                        dto.events.forEach { ev ->
                                            when (ev.type) {
                                                "START_SESSION" -> {
                                                    val wid = ev.payload.jsonObject["workerId"]?.jsonPrimitive?.content
                                                    if (!wid.isNullOrBlank()) pendingActions.clearBlockedForWorker(wid)
                                                }
                                                "END_SESSION" -> {
                                                    val sid = ev.payload.jsonObject["sessionId"]?.jsonPrimitive?.content
                                                    if (!sid.isNullOrBlank()) {
                                                        pendingActions.clearBlockedForSession(sid)
                                                        pendingActions.removeEnding(sid)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                failedLocal.clear()
                                localFailed = failedLocal.list()
                                showClearLocalFailedDialog = false
                            }.onFailure { e ->
                                localFailedMsg = e.message ?: e.toString()
                            }
                        }
                    },
                ) { Text("Удалить всё") }
            },
            dismissButton = {
                TextButton(onClick = { showClearLocalFailedDialog = false }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun WorkersTab(user: UserResponseDto, snackbarHostState: SnackbarHostState) {
    if (user.role == "ADMIN") {
        AdminWorkersTab()
        return
    }
    val scope = rememberCoroutineScope()
    var workers by remember { mutableStateOf<List<WorkerResponseDto>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    // FOREMAN cannot create workers; only ADMIN does that online.
    var activeSessions by remember { mutableStateOf<List<LocalActiveSession>>(emptyList()) }
    var serverActiveByWorkerId by remember { mutableStateOf<Map<String, String>>(emptyMap()) } // workerId -> sessionId
    var pendingEndingSessionIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var blockedWorkerIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var blockedSessionIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val sessions = AppContainer.sessionStateRepository
    val queue = AppContainer.batchQueue
    val cache = AppContainer.activeSessionsCache
    val pendingActions = AppContainer.pendingSessionActions

    fun mergedWithServer(local: List<LocalActiveSession>): List<LocalActiveSession> {
        if (serverActiveByWorkerId.isEmpty()) return local
        val merged = local.toMutableList()
        serverActiveByWorkerId.forEach { (wid, sid) ->
            if (sid in pendingEndingSessionIds) return@forEach
            if (merged.none { it.workerId == wid }) {
                merged.add(LocalActiveSession(workerId = wid, sessionId = sid, startAt = "", state = SessionStateRepository.STATE_ACTIVE))
            }
        }
        return merged
    }

    fun load() {
        scope.launch {
            try {
                val ownerUserId = AppContainer.cachedUserRepository.get()?.id
                val cachedWorkers = AppContainer.cachedWorkersRepository.get()
                if (cachedWorkers.ownerUserId == ownerUserId && cachedWorkers.items.isNotEmpty()) {
                    workers = cachedWorkers.items
                }
                val remoteWorkers = runCatching { AppContainer.api.workers() }.getOrNull()
                if (remoteWorkers != null) {
                    workers = remoteWorkers
                    AppContainer.cachedWorkersRepository.save(ownerUserId, remoteWorkers)
                } else if (workers.isEmpty()) {
                    error = "Нет сети и нет сохранённого списка рабочих"
                }
                pendingEndingSessionIds = pendingActions.getEndingSessionIds()
                blockedWorkerIds = pendingActions.getBlockedWorkerIds()
                blockedSessionIds = pendingActions.getBlockedSessionIds()
                // 1) персистентный кэш (важно для офлайна/после перезапуска)
                val cached = cache.get()
                serverActiveByWorkerId = cached.byWorkerId
                activeSessions = mergedWithServer(sessions.getActiveSessions())

                // 2) попытка обновить с сервера (если сети нет — остаёмся на кэше)
                val serverActive = runCatching { AppContainer.api.activeSessions() }.getOrNull()
                if (serverActive != null) {
                    val fresh = serverActive
                        .filter { it.status == "ACTIVE" }
                        .associate { it.workerId to it.id }
                    serverActiveByWorkerId = fresh
                    cache.set(CachedActiveSessions(byWorkerId = fresh, fetchedAt = OffsetDateTime.now(ZoneOffset.UTC).toString()))
                    val localBefore = sessions.getActiveSessions()
                    val localReconciled = localBefore.filter { local ->
                        val serverSessionId = fresh[local.workerId]
                        val hasPendingLocalStart = queue.hasPendingStartFor(local.workerId, local.sessionId)
                        when {
                            // Server confirms exactly this active session.
                            serverSessionId == local.sessionId -> true
                            // Local offline START is still waiting for sync. Keep it as the source of truth
                            // until the worker either accepts it or moves it to conflict/deferred state.
                            hasPendingLocalStart -> true
                            // Server has different session for same worker -> local is stale.
                            !serverSessionId.isNullOrBlank() -> false
                            // No server active session: keep only if START is still pending in queue.
                            else -> hasPendingLocalStart
                        }
                    }
                    if (localReconciled.size != localBefore.size) {
                        sessions.setActiveSessions(localReconciled)
                    }
                    activeSessions = mergedWithServer(localReconciled)
                }
                if (remoteWorkers != null || workers.isNotEmpty()) {
                    error = null
                }
            } catch (e: Exception) {
                error = e.message
            }
        }
    }
    LaunchedEffect(Unit) {
        load()
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("Список рабочих", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { load() }) { Text("Обновить") }
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(workers, key = { it.id }) { w ->
                val current = activeSessions.firstOrNull { it.workerId == w.id }
                val isThisActive = current != null
                val cachedServerSessionId = serverActiveByWorkerId[w.id]
                val isServerEndingPending = !isThisActive && cachedServerSessionId != null && cachedServerSessionId in pendingEndingSessionIds
                val isBlockedWorker = w.id in blockedWorkerIds
                val isBlockedSession = current?.sessionId in blockedSessionIds ||
                    (cachedServerSessionId != null && cachedServerSessionId in blockedSessionIds)
                val subtitle = when {
                    w.status != "ACTIVE" -> "Неактивен"
                    isBlockedWorker || isBlockedSession -> "Конфликт синхронизации"
                    isThisActive -> "🔴 Смена идёт (нажмите, чтобы завершить)"
                    isServerEndingPending -> "🟢 Не работает (END в очереди на отправку)"
                    else -> "🟢 Не работает (нажмите, чтобы начать смену)"
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isThisActive) {
                                Modifier.drawBehind {
                                    drawRect(
                                        color = ApofeozColors.Primary,
                                        topLeft = Offset.Zero,
                                        size = Size(width = 4.dp.toPx(), height = size.height),
                                    )
                                }
                            } else {
                                Modifier
                            },
                        )
                        .clickable(enabled = w.status == "ACTIVE" && !isBlockedWorker && !isBlockedSession) {
                            scope.launch {
                                val existing = sessions.getActiveFor(w.id)
                                val cachedServerSessionIdNow = serverActiveByWorkerId[w.id]
                                // Если END уже в очереди для этой серверной сессии, считаем её "логически остановленной"
                                // и разрешаем старт новой смены (например, после обеда) даже в офлайне.
                                val effectiveServerSessionId =
                                    if (cachedServerSessionIdNow != null && cachedServerSessionIdNow in pendingEndingSessionIds) null else cachedServerSessionIdNow

                                if (existing == null && effectiveServerSessionId != null) {
                                    // Смена активна по последнему серверному снимку → можно завершить офлайн,
                                    // т.к. у нас есть sessionId. После этого локально считаем смену закрытой,
                                    // чтобы можно было сразу начать новую смену (например, после обеда) даже офлайн.
                                    val endAt = OffsetDateTime.now(ZoneOffset.UTC).toString()
                                    val batch = SyncBatchRequestDto(
                                        batchUid = UUID.randomUUID().toString(),
                                        submittedAt = endAt,
                                        events = listOf(
                                            SyncEventDto(
                                                type = "END_SESSION",
                                                payload = buildJsonObject {
                                                    put("sessionId", effectiveServerSessionId)
                                                    put("endAt", endAt)
                                                },
                                            ),
                                        ),
                                    )
                                    queue.enqueue(batch)
                                    pendingActions.addEnding(effectiveServerSessionId)
                                    pendingEndingSessionIds = pendingEndingSessionIds + effectiveServerSessionId
                                    // убрать из серверного кэша, иначе merge вернёт "активна" обратно
                                    serverActiveByWorkerId = serverActiveByWorkerId - w.id
                                    cache.set(CachedActiveSessions(byWorkerId = serverActiveByWorkerId, fetchedAt = OffsetDateTime.now(ZoneOffset.UTC).toString()))
                                    // локально считаем смену закрытой сразу
                                    sessions.removeBySessionId(effectiveServerSessionId)
                                    activeSessions = mergedWithServer(sessions.getActiveSessions())
                                    snackbarHostState.showSnackbar("Конец смены добавлен в очередь")
                                } else if (existing == null) {
                                        val sessionId = UUID.randomUUID().toString()
                                        val startAt = OffsetDateTime.now(ZoneOffset.UTC).toString()
                                        val batch = SyncBatchRequestDto(
                                            batchUid = UUID.randomUUID().toString(),
                                            submittedAt = startAt,
                                            events = listOf(
                                                SyncEventDto(
                                                    type = "START_SESSION",
                                                    payload = buildJsonObject {
                                                        put("sessionId", sessionId)
                                                        put("workerId", w.id)
                                                        put("startAt", startAt)
                                                    },
                                                ),
                                            ),
                                        )
                                        queue.enqueue(batch)
                                        sessions.upsert(LocalActiveSession(workerId = w.id, sessionId = sessionId, startAt = startAt))
                                        activeSessions = mergedWithServer(sessions.getActiveSessions())
                                        snackbarHostState.showSnackbar("Старт добавлен в очередь")
                                } else {
                                        if (existing.sessionId in pendingEndingSessionIds) {
                                            snackbarHostState.showSnackbar("Завершение смены уже в очереди")
                                            return@launch
                                        }
                                        val sessionId = existing.sessionId
                                        val endAt = OffsetDateTime.now(ZoneOffset.UTC).toString()
                                        val batch = SyncBatchRequestDto(
                                            batchUid = UUID.randomUUID().toString(),
                                            submittedAt = endAt,
                                            events = listOf(
                                                SyncEventDto(
                                                    type = "END_SESSION",
                                                    payload = buildJsonObject {
                                                        put("sessionId", sessionId)
                                                        put("endAt", endAt)
                                                    },
                                                ),
                                            ),
                                        )
                                        queue.enqueue(batch)
                                        pendingActions.addEnding(sessionId)
                                        pendingEndingSessionIds = pendingEndingSessionIds + sessionId
                                        // убрать из серверного кэша, иначе merge вернёт "активна" обратно
                                        serverActiveByWorkerId = serverActiveByWorkerId - w.id
                                        cache.set(CachedActiveSessions(byWorkerId = serverActiveByWorkerId, fetchedAt = OffsetDateTime.now(ZoneOffset.UTC).toString()))
                                        // локально считаем смену закрытой сразу → можно стартовать новую даже офлайн
                                        sessions.remove(w.id)
                                        activeSessions = mergedWithServer(sessions.getActiveSessions())
                                        snackbarHostState.showSnackbar("Конец смены добавлен в очередь")
                                }
                            }
                        },
                    shape = MaterialTheme.shapes.large,
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    border = if (isThisActive) BorderStroke(1.dp, ApofeozColors.PrimaryBorder) else null,
                    colors = if (isThisActive) {
                        CardDefaults.cardColors(
                            containerColor = ApofeozColors.PrimaryMuted,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        )
                    } else {
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    },
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Text(
                            "${w.firstName} ${w.lastName}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionsTab() {
    val scope = rememberCoroutineScope()
    var workers by remember { mutableStateOf<List<WorkerResponseDto>>(emptyList()) }
    var selected by remember { mutableStateOf<WorkerResponseDto?>(null) }
    var active by remember { mutableStateOf<LocalActiveSession?>(null) }
    var pending by remember { mutableStateOf(0) }
    var msg by remember { mutableStateOf<String?>(null) }
    val sessions = AppContainer.sessionStateRepository
    val queue = AppContainer.batchQueue

    LaunchedEffect(Unit) {
        val ownerUserId = AppContainer.cachedUserRepository.get()?.id
        val cached = AppContainer.cachedWorkersRepository.get()
        workers = if (cached.ownerUserId == ownerUserId) cached.items else emptyList()
        runCatching { AppContainer.api.workers() }
            .onSuccess {
                workers = it
                AppContainer.cachedWorkersRepository.save(ownerUserId, it)
            }
        active = sessions.getActive()
        pending = queue.pendingCount()
    }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Очередь батчей: $pending", style = MaterialTheme.typography.bodyMedium)
        msg?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Text("Выберите рабочего", style = MaterialTheme.typography.titleSmall)
        workers.filter { it.status == "ACTIVE" }.forEach { w ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { selected = w }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected?.id == w.id, onClick = { selected = w })
                Text("${w.firstName} ${w.lastName}")
            }
        }
        val sel = selected
        if (sel != null) {
            val forThisWorker = active?.workerId == sel.id
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            val sessionId = UUID.randomUUID().toString()
                            val startAt = OffsetDateTime.now(ZoneOffset.UTC).toString()
                            val batch = SyncBatchRequestDto(
                                batchUid = UUID.randomUUID().toString(),
                                submittedAt = startAt,
                                events = listOf(
                                    SyncEventDto(
                                        type = "START_SESSION",
                                        payload = buildJsonObject {
                                            put("sessionId", sessionId)
                                            put("workerId", sel.id)
                                            put("startAt", startAt)
                                        },
                                    ),
                                ),
                            )
                            queue.enqueue(batch)
                            sessions.setActive(LocalActiveSession(workerId = sel.id, sessionId = sessionId, startAt = startAt))
                            active = sessions.getActive()
                            pending = queue.pendingCount()
                            msg = "Старт добавлен в очередь синхронизации"
                        }
                    },
                    enabled = !forThisWorker,
                ) { Text("Старт смены") }
                Button(
                    onClick = {
                        scope.launch {
                            val a = sessions.getActive()
                            if (a == null || a.workerId != sel.id) {
                                msg = "Нет активной смены для этого рабочего"
                                return@launch
                            }
                            val endAt = OffsetDateTime.now(ZoneOffset.UTC).toString()
                            val batch = SyncBatchRequestDto(
                                batchUid = UUID.randomUUID().toString(),
                                submittedAt = endAt,
                                events = listOf(
                                    SyncEventDto(
                                        type = "END_SESSION",
                                        payload = buildJsonObject {
                                            put("sessionId", a.sessionId)
                                            put("endAt", endAt)
                                        },
                                    ),
                                ),
                            )
                            queue.enqueue(batch)
                            sessions.setActive(null)
                            active = null
                            pending = queue.pendingCount()
                            msg = "Конец смены добавлен в очередь"
                        }
                    },
                    enabled = forThisWorker,
                ) { Text("Конец смены") }
            }
        }
        TextButton(onClick = {
            scope.launch {
                pending = queue.pendingCount()
                active = sessions.getActive()
            }
        }) { Text("Обновить счётчик") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportTab() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val zoneUtc = remember { ZoneId.of("UTC") }
    val defaultRange = remember {
        val to = LocalDate.now(zoneUtc)
        val from = to.minusDays(7)
        "${from.format(DateTimeFormatter.ISO_LOCAL_DATE)} — ${to.format(DateTimeFormatter.ISO_LOCAL_DATE)}"
    }
    var dateRangeText by remember { mutableStateOf(defaultRange) }
    var report by remember { mutableStateOf<com.apofeoz.shiftmanager.data.remote.dto.HoursReportResponseDto?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var xlsxMsg by remember { mutableStateOf<String?>(null) }
    var sortByHoursDesc by remember { mutableStateOf(true) }
    var rangePickerOpen by remember { mutableStateOf(false) }
    val dateFmt = remember { DateTimeFormatter.ISO_LOCAL_DATE }

    fun parseLocalDateOrNull(raw: String): LocalDate? =
        runCatching { LocalDate.parse(raw.trim(), dateFmt) }.getOrNull()

    fun localDateToMillisUtc(d: LocalDate): Long =
        d.atStartOfDay(zoneUtc).toInstant().toEpochMilli()

    fun millisToLocalDateUtc(ms: Long): LocalDate =
        Instant.ofEpochMilli(ms).atZone(zoneUtc).toLocalDate()

    fun parseDateRange(raw: String): Pair<LocalDate, LocalDate>? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        val twoIso = Regex("""(\d{4}-\d{2}-\d{2})\s+(\d{4}-\d{2}-\d{2})""").find(t)
        if (twoIso != null) {
            val a = parseLocalDateOrNull(twoIso.groupValues[1]) ?: return null
            val b = parseLocalDateOrNull(twoIso.groupValues[2]) ?: return null
            return if (a <= b) a to b else b to a
        }
        val sep = listOf("—", "–", "..", " - ", " — ").firstOrNull { s -> t.contains(s) } ?: return null
        val parts = t.split(sep, limit = 2).map { it.trim() }
        if (parts.size != 2) return null
        val a = parseLocalDateOrNull(parts[0]) ?: return null
        val b = parseLocalDateOrNull(parts[1]) ?: return null
        return if (a <= b) a to b else b to a
    }

    val parsedRange = remember(dateRangeText) { parseDateRange(dateRangeText) }

    if (rangePickerOpen) {
        val initial = parsedRange
            ?: (LocalDate.now(zoneUtc).minusDays(7) to LocalDate.now(zoneUtc))
        val state = androidx.compose.material3.rememberDateRangePickerState(
            initialSelectedStartDateMillis = localDateToMillisUtc(initial.first),
            initialSelectedEndDateMillis = localDateToMillisUtc(initial.second),
        )
        DatePickerDialog(
            onDismissRequest = { rangePickerOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val a = state.selectedStartDateMillis
                        val b = state.selectedEndDateMillis
                        if (a != null && b != null) {
                            val d1 = millisToLocalDateUtc(minOf(a, b))
                            val d2 = millisToLocalDateUtc(maxOf(a, b))
                            dateRangeText = "${d1.format(dateFmt)} — ${d2.format(dateFmt)}"
                        }
                        rangePickerOpen = false
                    },
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { rangePickerOpen = false }) { Text("Отмена") } },
        ) {
            DateRangePicker(modifier = Modifier.height(400.dp), state = state)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ApofeozPanel(modifier = Modifier.fillMaxWidth(), accent = true) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeading(
                    eyebrow = "Табель и аналитика",
                    title = "Отчёт по сменам",
                )
                OutlinedTextField(
                    value = dateRangeText,
                    onValueChange = { dateRangeText = it },
                    label = { Text("Период (YYYY-MM-DD — YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    trailingIcon = {
                        IconButton(onClick = { rangePickerOpen = true }) {
                            Icon(Icons.Filled.DateRange, contentDescription = "Выбрать период")
                        }
                    },
                    supportingText = {
                        Text(
                            "Можно ввести вручную или открыть календарь",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            val pr = parseDateRange(dateRangeText) ?: return@Button
                            val fromApi = pr.first.format(dateFmt)
                            val toApi = pr.second.format(dateFmt)
                            scope.launch {
                                err = null
                                report = null
                                try {
                                    report = AppContainer.api.reportRange(fromApi, toApi)
                                } catch (e: HttpException) {
                                    err = if (e.code() == 403) "Только для ADMIN" else "Ошибка ${e.code()}: ${e.message()}"
                                } catch (e: Exception) {
                                    err = e.message ?: e.toString()
                                }
                            }
                        },
                        enabled = parsedRange != null,
                        modifier = Modifier.weight(1f),
                    ) { Text("Построить") }
                    OutlinedButton(
                        onClick = {
                            val pr = parseDateRange(dateRangeText) ?: return@OutlinedButton
                            val fromApi = pr.first.format(dateFmt)
                            val toApi = pr.second.format(dateFmt)
                            scope.launch {
                                xlsxMsg = null
                                try {
                                    val path = withContext(Dispatchers.IO) {
                                        val body = AppContainer.api.timesheetXlsx(fromApi, toApi)
                                        val bytes = body.use { it.bytes() }
                                        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                                        val f = File(dir, "tabel_${fromApi}_${toApi}.xlsx")
                                        f.writeBytes(bytes)
                                        f.absolutePath
                                    }
                                    xlsxMsg = "Табель сохранён: $path"
                                } catch (e: HttpException) {
                                    xlsxMsg = if (e.code() == 403) "Только для ADMIN" else "Ошибка ${e.code()}: ${e.message()}"
                                } catch (e: Exception) {
                                    xlsxMsg = e.message ?: e.toString()
                                }
                            }
                        },
                        enabled = parsedRange != null,
                        modifier = Modifier.weight(1f),
                    ) { Text("Скачать XLSX") }
                }
            }
        }
        err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        xlsxMsg?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }

        val r = report
        if (r != null) {
            val fmt1: (Double) -> String = { v ->
                val rounded = kotlin.math.round(v * 10.0) / 10.0
                if (kotlin.math.abs(rounded - rounded.toLong()) < 1e-9) rounded.toLong().toString()
                else rounded.toString()
            }
            val fmtShift: (Double) -> String = { String.format(Locale.US, "%.3f", it) }
            val fromStr = r.fromDate ?: parsedRange?.first?.format(dateFmt).orEmpty()
            val toStr = r.toDate ?: parsedRange?.second?.format(dateFmt).orEmpty()

            ApofeozPanel(modifier = Modifier.fillMaxWidth(), accent = true) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Период: $fromStr..$toStr",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusChip(r.timezone)
                        StatusChip("Норма ${r.shiftNormHours}ч", accent = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Часы: ${fmt1(r.totals.hours)}", style = MaterialTheme.typography.bodyMedium)
                        Text("Смены: ${fmtShift(r.totals.shiftEquivalent)}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Сортировка:", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { sortByHoursDesc = true }) { Text("по часам") }
                TextButton(onClick = { sortByHoursDesc = false }) { Text("по ФИО") }
            }

            val sorted = if (sortByHoursDesc) {
                r.rows.sortedWith(
                    compareByDescending<com.apofeoz.shiftmanager.data.remote.dto.ReportRowDto> { it.hours }
                        .thenBy { it.lastName }
                        .thenBy { it.firstName },
                )
            } else {
                r.rows.sortedWith(
                    compareBy<com.apofeoz.shiftmanager.data.remote.dto.ReportRowDto> { it.lastName }
                        .thenBy { it.firstName },
                )
            }

            val groupsMap = sorted.groupBy { it.foremanDisplayName ?: it.foremanId }
            val orderedKeys = if (sortByHoursDesc) {
                groupsMap.entries
                    .sortedWith(
                        compareByDescending<Map.Entry<String, List<com.apofeoz.shiftmanager.data.remote.dto.ReportRowDto>>> { e ->
                            e.value.sumOf { row -> row.hours }
                        }.thenBy { it.key },
                    )
                    .map { it.key }
            } else {
                groupsMap.keys.sorted()
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                orderedKeys.forEach { foremanKey ->
                    val rows = groupsMap.getValue(foremanKey)
                    val foremanTitle =
                        if (foremanKey.matches(Regex("^[0-9a-fA-F\\-]{36}$"))) "Бригадир: $foremanKey" else "Бригадир: $foremanKey"
                    val sumHours = rows.sumOf { it.hours }
                    val sumShifts = rows.sumOf { it.shiftEquivalent }
                    ApofeozPanel(modifier = Modifier.fillMaxWidth(), accent = true) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(foremanTitle, style = MaterialTheme.typography.titleSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("Часы: ${fmt1(sumHours)}", style = MaterialTheme.typography.bodyMedium)
                                Text("Смены: ${fmtShift(sumShifts)}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        rows.forEach { row ->
                            ApofeozPanel(modifier = Modifier.fillMaxWidth()) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("${row.lastName} ${row.firstName}", style = MaterialTheme.typography.titleSmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        Text("Часы: ${fmt1(row.hours)}", style = MaterialTheme.typography.bodyMedium)
                                        Text("Смены: ${fmtShift(row.shiftEquivalent)}", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FailedTab() {
    var items by remember { mutableStateOf<List<FailedBatchListItemDto>>(emptyList()) }
    var err by remember { mutableStateOf<String?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        try {
            items = AppContainer.api.failedBatches()
            err = null
        } catch (e: Exception) {
            err = e.message
        }
    }

    val sid = selectedId
    if (sid != null) {
        FailedBatchDetailScreen(
            id = sid,
            onBack = { selectedId = null },
            onDeleted = {
                selectedId = null
                reload += 1
            },
        )
        return
    }

    Column(Modifier.padding(16.dp)) {
        err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text(
            "Нажмите запись для просмотра events[] и удаления одной записи.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
        if (items.isEmpty()) {
            Text("Нет", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.id }) { row ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedId = row.id },
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(row.batchUid, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Событие #${row.failedIndex}: ${row.reason}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(row.submittedAt, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

private fun userRoleTitle(role: String): String = when (role) {
    "FOREMAN" -> "Бригадир"
    "ADMIN" -> "Админ"
    else -> "Пользователь"
}

private fun userShortLine(user: UserResponseDto): String {
    val f = user.firstName.trim().firstOrNull()
    val last = user.lastName.trim()
    return if (f != null) "$f. $last" else last
}

private fun userInitials(user: UserResponseDto): String {
    val a = user.firstName.trim().firstOrNull()
    val b = user.lastName.trim().firstOrNull()
    return when {
        a == null && b == null -> "?"
        else -> "${a?.uppercaseChar() ?: ""}${b?.uppercaseChar() ?: ""}"
    }
}
