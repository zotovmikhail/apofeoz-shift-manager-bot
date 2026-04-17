package com.apofeoz.shiftmanager.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apofeoz.shiftmanager.core.di.AppContainer
import com.apofeoz.shiftmanager.data.remote.dto.LoginRequestDto
import com.apofeoz.shiftmanager.data.remote.dto.RegisterRequestDto
import com.apofeoz.shiftmanager.presentation.theme.ApofeozColors
import com.apofeoz.shiftmanager.presentation.theme.apofeozGridBackground
import com.apofeoz.shiftmanager.work.OutboundSyncScheduler
import kotlinx.coroutines.launch
import java.util.Locale
import retrofit2.HttpException

@Composable
fun LoginScreen(onSuccess: () -> Unit) {
    val context = LocalContext.current.applicationContext
    var isRegister by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("Иван") }
    var lastName by remember { mutableStateOf("Иванов") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val api = AppContainer.api
    val tokens = AppContainer.tokenRepository
    val scheme = MaterialTheme.colorScheme
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = scheme.onSurface,
        unfocusedTextColor = scheme.onSurface,
        focusedBorderColor = scheme.primary,
        unfocusedBorderColor = scheme.outline,
        cursorColor = scheme.primary,
        focusedLabelColor = scheme.onSurfaceVariant,
        unfocusedLabelColor = scheme.onSurfaceVariant,
        focusedLeadingIconColor = scheme.primary,
        unfocusedLeadingIconColor = scheme.onSurfaceVariant,
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(scheme.background)
            .apofeozGridBackground(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            ApofeozPanel(
                modifier = Modifier.fillMaxWidth(),
                accent = true,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Card(
                        modifier = Modifier.size(72.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = scheme.surface),
                        border = BorderStroke(1.dp, scheme.outline),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.Business,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = scheme.primary,
                            )
                        }
                    }
                    Text(
                        "Апофеоз".uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.displaySmall.copy(letterSpacing = 2.4.sp),
                        color = scheme.onBackground,
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(
                        "Единый операторский контур для смен, ролей и табеля.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusChip("ANDROID", accent = true)
                        StatusChip("JWT + REFRESH")
                        StatusChip("OFFLINE READY")
                    }
                }
            }
            ApofeozPanel(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionHeading(
                        eyebrow = if (isRegister) "Новый доступ" else "Авторизация",
                        title = if (isRegister) "Регистрация оператора" else "Вход в мобильный терминал",
                    )
                    HorizontalDivider(color = scheme.outline.copy(alpha = 0.8f))

                    if (isRegister) {
                        OutlinedTextField(
                            value = firstName,
                            onValueChange = { firstName = it },
                            label = { Text("ИМЯ") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = fieldColors,
                        )
                        OutlinedTextField(
                            value = lastName,
                            onValueChange = { lastName = it },
                            label = { Text("ФАМИЛИЯ") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = fieldColors,
                        )
                    }
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(if (isRegister) "EMAIL" else "EMAIL ИЛИ ТЕЛЕФОН") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = fieldColors,
                        leadingIcon = {
                            Icon(Icons.Filled.Person, contentDescription = null)
                        },
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("КОД ДОСТУПА") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = fieldColors,
                        visualTransformation = PasswordVisualTransformation(),
                        leadingIcon = {
                            Icon(Icons.Filled.Lock, contentDescription = null)
                        },
                    )
                    error?.let {
                        Text(
                            it,
                            color = scheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    InlineHint(
                        if (isRegister) {
                            "Регистрация сразу создаёт доступ в мобильный терминал."
                        } else {
                            "Используйте тот же аккаунт и роли, что и в web-консоли."
                        },
                    )
                    Button(
                        onClick = {
                            error = null
                            loading = true
                            scope.launch {
                                try {
                                    if (isRegister) {
                                        val t = api.register(
                                            RegisterRequestDto(
                                                email = email.trim().takeIf { it.isNotEmpty() },
                                                firstName = firstName.trim(),
                                                lastName = lastName.trim(),
                                                password = password,
                                            ),
                                        )
                                        tokens.save(t.accessToken, t.refreshToken)
                                    } else {
                                        val t = api.login(LoginRequestDto(login = email.trim(), password = password))
                                        tokens.save(t.accessToken, t.refreshToken)
                                    }
                                    OutboundSyncScheduler.schedule(context)
                                    onSuccess()
                                } catch (e: HttpException) {
                                    error = "Ошибка ${e.code()}: ${e.message()}"
                                } catch (e: Exception) {
                                    error = e.message ?: e.toString()
                                } finally {
                                    loading = false
                                }
                            }
                        },
                        enabled = !loading && email.isNotBlank() && password.length >= 8 &&
                            (!isRegister || (firstName.isNotBlank() && lastName.isNotBlank())),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = scheme.primary,
                            contentColor = scheme.onPrimary,
                            disabledContainerColor = scheme.surfaceVariant,
                            disabledContentColor = scheme.onSurfaceVariant,
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = scheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(
                                if (isRegister) "РЕГИСТРАЦИЯ" else "АВТОРИЗАЦИЯ",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                    TextButton(onClick = { isRegister = !isRegister; error = null }) {
                        Text(
                            if (isRegister) "Уже есть аккаунт? Войти" else "Нет аккаунта? Регистрация",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.primary,
                        )
                    }
                }
            }
        }
    }
}
