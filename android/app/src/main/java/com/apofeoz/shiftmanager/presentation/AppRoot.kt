package com.apofeoz.shiftmanager.presentation

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import com.apofeoz.shiftmanager.core.di.AppContainer
import com.apofeoz.shiftmanager.data.remote.dto.UserResponseDto
import kotlinx.coroutines.launch

@Composable
fun AppRoot() {
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf<String?>(null) }
    var user by remember { mutableStateOf<UserResponseDto?>(null) }

    LaunchedEffect(Unit) {
        val token = AppContainer.tokenRepository.getAccessToken()
        if (token.isNullOrBlank()) {
            screen = "login"
        } else {
            user = runCatching { AppContainer.api.me() }.getOrNull()
            screen = if (user != null) "main" else "login"
        }
    }

    LaunchedEffect(Unit) {
        AppContainer.sessionExpired.collect {
            AppContainer.sessionStateRepository.setActiveSessions(emptyList())
            AppContainer.tokenRepository.clear()
            user = null
            screen = "login"
        }
    }

    when (screen) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        "login" -> LoginScreen(
            onSuccess = {
                scope.launch {
                    user = runCatching { AppContainer.api.me() }.getOrNull()
                    screen = if (user != null) "main" else "login"
                }
            },
        )
        "main" -> {
            val u = user
            if (u != null) {
                MainScreen(
                    user = u,
                    onLoggedOut = {
                        screen = "login"
                        user = null
                    },
                )
            } else {
                Text("Не удалось загрузить профиль")
            }
        }
        else -> Text("Неизвестное состояние")
    }
}
