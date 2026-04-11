package com.apofeoz.shiftmanager.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.apofeoz.shiftmanager.core.di.AppContainer
import com.apofeoz.shiftmanager.data.remote.dto.CreateWorkerRequestDto
import com.apofeoz.shiftmanager.data.remote.dto.ErrorResponseDto
import com.apofeoz.shiftmanager.data.remote.dto.PatchWorkerRequestDto
import com.apofeoz.shiftmanager.data.remote.dto.UserResponseDto
import com.apofeoz.shiftmanager.data.remote.dto.WorkerResponseDto
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import retrofit2.HttpException

@Composable
fun AdminWorkersTab() {
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var workers by remember { mutableStateOf<List<WorkerResponseDto>>(emptyList()) }
    var foremen by remember { mutableStateOf<List<UserResponseDto>>(emptyList()) }
    var reload by remember { mutableIntStateOf(0) }

    var reassignWorker by remember { mutableStateOf<WorkerResponseDto?>(null) }
    var reassignQuery by remember { mutableStateOf("") }
    var reassignTargetId by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            try {
                workers = AppContainer.api.workers()
                foremen = AppContainer.api.users()
                    .filter { it.role == "FOREMAN" && it.status == "ACTIVE" }
                    .sortedWith(compareBy<UserResponseDto>({ it.lastName }, { it.firstName }))
                error = null
            } catch (e: HttpException) {
                error = e.userVisibleMessage()
            } catch (e: Exception) {
                error = e.message ?: e.toString()
            }
        }
    }

    LaunchedEffect(reload) { load() }

    val foremanNameById = remember(foremen) {
        foremen.associateBy({ it.id }, { "${it.firstName} ${it.lastName}" })
    }

    val groups = remember(workers) {
        workers.groupBy { it.foremanId }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Бригады", style = MaterialTheme.typography.titleMedium)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(foremen, key = { it.id }) { f ->
                val list = groups[f.id].orEmpty().sortedWith(compareBy<WorkerResponseDto>({ it.lastName }, { it.firstName }))
                ForemanSectionCard(
                    foreman = f,
                    workers = list,
                    busy = busy,
                    onCreateWorker = { firstName, lastName ->
                        busy = true
                        error = null
                        scope.launch {
                            try {
                                AppContainer.api.createWorker(
                                    CreateWorkerRequestDto(
                                        firstName = firstName,
                                        lastName = lastName,
                                        foremanId = f.id,
                                    ),
                                )
                                reload += 1
                            } catch (e: HttpException) {
                                error = e.userVisibleMessage()
                            } catch (e: Exception) {
                                error = e.message ?: e.toString()
                            } finally {
                                busy = false
                            }
                        }
                    },
                    onReassign = { w ->
                        reassignWorker = w
                        reassignTargetId = null
                        reassignQuery = ""
                    },
                )
            }

            val unknownForemen = groups.keys
                .filter { fid -> foremen.none { it.id == fid } }
                .sorted()
            items(unknownForemen, key = { "unknown-$it" }) { fid ->
                val list = groups[fid].orEmpty().sortedWith(compareBy<WorkerResponseDto>({ it.lastName }, { it.firstName }))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(foremanNameById[fid] ?: "FOREMAN $fid", style = MaterialTheme.typography.titleSmall)
                        Text("Работников: ${list.size}", style = MaterialTheme.typography.bodySmall)
                        list.forEach { w -> WorkerRow(w, canReassign = false, onReassign = {}) }
                    }
                }
            }
        }
    }

    val rw = reassignWorker
    if (rw != null) {
        val q = reassignQuery.trim().lowercase()
        val candidates = foremen
            .filter { it.id != rw.foremanId }
            .filter {
                q.isEmpty() || listOf(it.firstName, it.lastName, it.email, it.phone)
                    .mapNotNull { x -> x?.lowercase() }
                    .any { it.contains(q) }
            }

        AlertDialog(
            onDismissRequest = { if (!busy) reassignWorker = null },
            title = { Text("Переназначить работника") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${rw.firstName} ${rw.lastName}")
                    OutlinedTextField(
                        value = reassignQuery,
                        onValueChange = { reassignQuery = it },
                        label = { Text("Найти FOREMAN") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    if (candidates.isEmpty()) {
                        Text(
                            "Нет подходящих бригадиров",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        candidates.take(8).forEach { f ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !busy) { reassignTargetId = f.id }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = reassignTargetId == f.id,
                                    onClick = { if (!busy) reassignTargetId = f.id },
                                )
                                Column {
                                    Text("${f.firstName} ${f.lastName}")
                                    Text(
                                        f.email ?: f.phone ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        if (candidates.size > 8) {
                            Text(
                                "Найдено: ${candidates.size}. Уточните поиск.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetId = reassignTargetId ?: return@Button
                        busy = true
                        error = null
                        scope.launch {
                            try {
                                AppContainer.api.patchWorker(rw.id, PatchWorkerRequestDto(foremanId = targetId))
                                reassignWorker = null
                                reload += 1
                            } catch (e: HttpException) {
                                error = e.userVisibleMessage()
                            } catch (e: Exception) {
                                error = e.message ?: e.toString()
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy && reassignTargetId != null,
                ) { Text("Переназначить") }
            },
            dismissButton = {
                TextButton(onClick = { reassignWorker = null }, enabled = !busy) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun ForemanSectionCard(
    foreman: UserResponseDto,
    workers: List<WorkerResponseDto>,
    busy: Boolean,
    onCreateWorker: (firstName: String, lastName: String) -> Unit,
    onReassign: (WorkerResponseDto) -> Unit,
) {
    var addFn by remember(foreman.id) { mutableStateOf("") }
    var addLn by remember(foreman.id) { mutableStateOf("") }
    var addOpen by remember(foreman.id) { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${foreman.firstName} ${foreman.lastName}", style = MaterialTheme.typography.titleSmall)
            Text(
                (foreman.email ?: foreman.phone ?: foreman.id),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Работников: ${workers.size}", style = MaterialTheme.typography.bodySmall)

            if (workers.isEmpty()) {
                Text(
                    "Нет рабочих",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                workers.forEach { w ->
                    val isForemanSelfCard = w.userId != null && w.userId == w.foremanId
                    WorkerRow(
                        w = w,
                        canReassign = !busy && !isForemanSelfCard,
                        onReassign = onReassign,
                        extraLabel = if (isForemanSelfCard) " (бригадир)" else null,
                    )
                }
            }

            Spacer(Modifier.padding(top = 4.dp))
            if (!addOpen) {
                TextButton(onClick = { if (!busy) addOpen = true }, enabled = !busy) { Text("Добавить рабочего") }
            } else {
                OutlinedTextField(
                    value = addFn,
                    onValueChange = { addFn = it },
                    label = { Text("Имя") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !busy,
                )
                OutlinedTextField(
                    value = addLn,
                    onValueChange = { addLn = it },
                    label = { Text("Фамилия") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !busy,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onCreateWorker(addFn.trim(), addLn.trim())
                            addFn = ""
                            addLn = ""
                            addOpen = false
                        },
                        enabled = !busy && addFn.isNotBlank() && addLn.isNotBlank(),
                    ) { Text("Добавить") }
                    TextButton(onClick = { addOpen = false }, enabled = !busy) { Text("Отмена") }
                }
            }
        }
    }
}

@Composable
private fun WorkerRow(
    w: WorkerResponseDto,
    canReassign: Boolean,
    onReassign: (WorkerResponseDto) -> Unit,
    extraLabel: String? = null,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text("${w.firstName} ${w.lastName}${extraLabel.orEmpty()}")
            Text(
                "Статус: ${w.status}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = { onReassign(w) },
            enabled = canReassign,
        ) { Text("Переназначить") }
    }
}

private fun HttpException.userVisibleMessage(): String {
    val raw = response()?.errorBody()?.use { it.string() }?.trim().orEmpty()
    if (raw.isEmpty()) return "Ошибка ${code()}: ${message()}"
    return runCatching {
        AppContainer.jsonFormat.decodeFromString<ErrorResponseDto>(raw)
    }.getOrNull()?.let { e ->
        val extra = e.details.entries.joinToString("\n") { "${it.key}: ${detailText(it.value)}" }
        if (extra.isNotEmpty()) "${e.message}\n$extra" else e.message
    } ?: raw
}

private fun detailText(el: JsonElement): String =
    (el as? JsonPrimitive)?.contentOrNull ?: el.toString()

