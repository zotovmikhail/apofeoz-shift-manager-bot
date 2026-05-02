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
import androidx.compose.ui.platform.LocalContext
import com.apofeoz.shiftmanager.core.di.AppContainer
import com.apofeoz.shiftmanager.data.local.toUserResponseDto
import com.apofeoz.shiftmanager.data.remote.dto.UserResponseDto
import com.apofeoz.shiftmanager.work.OutboundSyncScheduler
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf<String?>(null) }
    var user by remember { mutableStateOf<UserResponseDto?>(null) }

    LaunchedEffect(Unit) {
        val token = AppContainer.tokenRepository.getAccessToken()
        val cached = AppContainer.cachedUserRepository.get()
        val authRejected = AppContainer.authStateRepository.isAuthRejected()
        if (token.isNullOrBlank()) {
            user = if (authRejected) null else cached?.toUserResponseDto()
            screen = if (user != null) "main" else "login"
        } else {
            try {
                val me = AppContainer.api.me()
                AppContainer.authStateRepository.setAuthRejected(false)
                AppContainer.cachedUserRepository.save(me)
                user = me
                screen = "main"
            } catch (e: HttpException) {
                if (e.code() == 401 || e.code() == 403) {
                    AppContainer.tokenRepository.clear()
                    AppContainer.authStateRepository.setAuthRejected(true)
                    user = null
                    screen = "login"
                } else {
                    user = cached?.toUserResponseDto()
                    screen = if (user != null) "main" else "login"
                }
            } catch (_: IOException) {
                user = cached?.toUserResponseDto()
                screen = if (user != null) "main" else "login"
            } catch (_: Exception) {
                user = cached?.toUserResponseDto()
                screen = if (user != null) "main" else "login"
            }
        }
    }

    LaunchedEffect(Unit) {
        AppContainer.sessionExpired.collect {
            AppContainer.tokenRepository.clear()
            AppContainer.authStateRepository.setAuthRejected(true)
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
                    user = runCatching { AppContainer.api.me() }
                        .onSuccess {
                            AppContainer.authStateRepository.setAuthRejected(false)
                            AppContainer.cachedUserRepository.save(it)
                            if (AppContainer.batchQueue.unblockAuthForCurrentUser() > 0) {
                                OutboundSyncScheduler.schedule(context)
                            }
                        }
                        .getOrNull()
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
