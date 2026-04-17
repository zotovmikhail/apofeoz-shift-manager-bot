package com.apofeoz.shiftmanager.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.apofeoz.shiftmanager.data.remote.dto.ErrorResponseDto
import com.apofeoz.shiftmanager.data.remote.dto.PatchUserRequestDto
import com.apofeoz.shiftmanager.data.remote.dto.UserResponseDto
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import retrofit2.HttpException

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

private val adminRoles = listOf("USER", "FOREMAN", "ADMIN")

@Composable
fun AdminUsersTab(currentUserId: String) {
    var users by remember { mutableStateOf<List<UserResponseDto>>(emptyList()) }
    var filter by remember { mutableStateOf("") }
    var listError by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<UserResponseDto?>(null) }

    LaunchedEffect(refresh) {
        try {
            users = AppContainer.api.users()
            listError = null
        } catch (e: HttpException) {
            listError = e.userVisibleMessage()
        } catch (e: Exception) {
            listError = e.message ?: e.toString()
        }
    }

    val detail = selected
    if (detail != null) {
        AdminUserDetailScreen(
            user = detail,
            currentUserId = currentUserId,
            onBack = { selected = null },
            onSaved = {
                selected = null
                refresh += 1
            },
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeading(
            eyebrow = "Доступ и роли",
            title = "Матрица учётных записей",
        )
        listError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            label = { Text("Поиск email / телефон / имя") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        InlineHint("Здесь меняются роли USER / FOREMAN / ADMIN и статус доступа.")
        val q = filter.trim().lowercase()
        val filtered = if (q.isEmpty()) {
            users
        } else {
            users.filter { u ->
                listOf(u.email, u.phone, u.firstName, u.lastName)
                    .mapNotNull { it?.lowercase() }
                    .any { it.contains(q) }
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(filtered, key = { it.id }) { u ->
                ApofeozPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selected = u },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                IdentityBadge("${u.firstName.firstOrNull()?.uppercaseChar() ?: '?'}${u.lastName.firstOrNull()?.uppercaseChar() ?: '?'}")
                                Column {
                                    Text("${u.firstName} ${u.lastName}", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        u.email ?: u.phone ?: "—",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            StatusChip(roleLabel(u.role), accent = u.role == "ADMIN")
                        }
                        Text(
                            u.id,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusChip(statusLabel(u.status), warning = u.status != "ACTIVE")
                            StatusChip("Открыть", accent = false)
                        }
                    }
                }
            }
        }
    }
}

private fun roleLabel(role: String): String = when (role) {
    "FOREMAN" -> "FOREMAN"
    "ADMIN" -> "ADMIN"
    else -> "USER"
}

private fun statusLabel(status: String): String = when (status) {
    "ACTIVE" -> "Активен"
    "INACTIVE" -> "Заблокирован"
    else -> status
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminUserDetailScreen(
    user: UserResponseDto,
    currentUserId: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var chosenRole by remember(user.id) { mutableStateOf(user.role) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var blockConfirm by remember { mutableStateOf(false) }

    Column {
        Column(
            Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeading(
                eyebrow = "Карточка пользователя",
                title = "${user.firstName} ${user.lastName}",
                trailing = { TextButton(onClick = onBack) { Text("Назад") } },
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            ApofeozPanel(modifier = Modifier.fillMaxWidth(), accent = true) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusChip(roleLabel(user.role), accent = user.role == "ADMIN")
                        StatusChip(statusLabel(user.status), warning = user.status != "ACTIVE")
                    }
                    Text("Имя: ${user.firstName}")
                    Text("Фамилия: ${user.lastName}")
                    user.email?.let { Text("Email: $it") }
                    user.phone?.let { Text("Телефон: $it") }
                }
            }

            ApofeozPanel(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Роль", style = MaterialTheme.typography.titleSmall)
                    adminRoles.forEach { r ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !busy) { chosenRole = r },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = chosenRole == r,
                                onClick = { if (!busy) chosenRole = r },
                            )
                            Text(r)
                        }
                    }
                }
            }

            Button(
                onClick = {
                    if (chosenRole == user.role) {
                        onBack()
                        return@Button
                    }
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            AppContainer.api.patchUser(user.id, PatchUserRequestDto(role = chosenRole))
                            onSaved()
                        } catch (e: HttpException) {
                            error = e.userVisibleMessage()
                        } catch (e: Exception) {
                            error = e.message ?: e.toString()
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Сохранить роль")
            }

            val isSelf = user.id == currentUserId
            if (user.status == "ACTIVE") {
                TextButton(
                    onClick = { if (!isSelf) blockConfirm = true },
                    enabled = !busy && !isSelf,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Заблокировать пользователя")
                }
                if (isSelf) {
                    Text(
                        "Нельзя заблокировать свою учётную запись в приложении.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                TextButton(
                    onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            try {
                                AppContainer.api.patchUser(user.id, PatchUserRequestDto(status = "ACTIVE"))
                                onSaved()
                            } catch (e: HttpException) {
                                error = e.userVisibleMessage()
                            } catch (e: Exception) {
                                error = e.message ?: e.toString()
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Разблокировать")
                }
            }

            Text(
                "Блокировка выставляет статус INACTIVE и отзывает сессии (см. backend).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (blockConfirm) {
        AlertDialog(
            onDismissRequest = { if (!busy) blockConfirm = false },
            title = { Text("Заблокировать?") },
            text = { Text("Пользователь потеряет доступ до разблокировки.") },
            confirmButton = {
                Button(
                    onClick = {
                        busy = true
                        error = null
                        blockConfirm = false
                        scope.launch {
                            try {
                                AppContainer.api.patchUser(user.id, PatchUserRequestDto(status = "INACTIVE"))
                                onSaved()
                            } catch (e: HttpException) {
                                error = e.userVisibleMessage()
                            } catch (e: Exception) {
                                error = e.message ?: e.toString()
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy,
                ) { Text("Заблокировать") }
            },
            dismissButton = {
                TextButton(onClick = { blockConfirm = false }, enabled = !busy) {
                    Text("Отмена")
                }
            },
        )
    }
}
