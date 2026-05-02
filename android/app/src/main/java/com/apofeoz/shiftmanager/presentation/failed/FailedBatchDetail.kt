package com.apofeoz.shiftmanager.presentation.failed

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.apofeoz.shiftmanager.core.di.AppContainer
import com.apofeoz.shiftmanager.data.remote.dto.FailedBatchDetailDto
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import retrofit2.HttpException

@Composable
fun FailedBatchDetailScreen(
    id: String,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<FailedBatchDetailDto?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        err = null
        detail = runCatching { AppContainer.api.failedBatchDetail(id) }
            .onFailure { e ->
                err = when (e) {
                    is HttpException -> "HTTP ${e.code()}: ${e.message()}"
                    else -> e.message ?: e.toString()
                }
            }
            .getOrNull()
    }

    val prettyJson = remember(detail) {
        detail?.let { d ->
            Json {
                prettyPrint = true
                ignoreUnknownKeys = true
            }.encodeToString(JsonElement.serializer(), d.eventsSnapshot)
        } ?: ""
    }

    Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
            Text("Неуспешный батч", style = MaterialTheme.typography.titleLarge)
        }
        err?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
        }
        detail?.let { d ->
            Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("batchUid: ${d.batchUid}", style = MaterialTheme.typography.bodyMedium)
                    Text("submittedAt: ${d.submittedAt}", style = MaterialTheme.typography.bodySmall)
                    Text("failedIndex: ${d.failedIndex}", style = MaterialTheme.typography.bodySmall)
                    Text("reason: ${d.reason}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("События (JSON)", style = MaterialTheme.typography.titleSmall)
            Text(
                prettyJson,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
            Button(
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val r = AppContainer.api.deleteFailedBatch(id)
                            if (r.isSuccessful) onDeleted()
                            else err = "Удаление: код ${r.code()}"
                        } catch (e: Exception) {
                            err = e.message
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text("Удалить запись на сервере")
            }
        }
    }
}
