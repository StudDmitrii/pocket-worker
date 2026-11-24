package com.pocketworker.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pocketworker.app.core.git.GitCredentials
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatShortTimestamp(timestampMillis: Long): String {
    val sdf = SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestampMillis))
}

fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes Б"
        bytes < 1024 * 1024 -> "${bytes / 1024} КБ"
        else -> String.format(Locale.getDefault(), "%.1f МБ", bytes / (1024f * 1024f))
    }
}

fun formatGitTransportError(error: Throwable): String {
    val raw = error.localizedMessage ?: error.message ?: error.javaClass.simpleName
    val lower = raw.lowercase()
    return when {
        "not authorized" in lower || "authentication is required" in lower || "401" in lower ->
            "Ошибка авторизации: проверьте логин и Personal Access Token (PAT).\nДетали: $raw"
        "403" in lower || "forbidden" in lower ->
            "Доступ запрещён (403): убедитесь, что токен имеет права на этот репозиторий.\nДетали: $raw"
        "not found" in lower || "404" in lower ->
            "Репозиторий или ветка не найдены (404). Проверьте ссылку и права доступа.\nДетали: $raw"
        "unknownhost" in lower || "cannot open git-upload-pack" in lower || "connect" in lower ->
            "Не удалось подключиться к серверу. Проверьте интернет и адрес репозитория.\nДетали: $raw"
        else -> raw
    }
}

@Composable
fun OnLifecycleResume(onResume: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnResume by rememberUpdatedState(onResume)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                currentOnResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

@Composable
fun CredentialsEditorDialog(
    title: String,
    description: String,
    currentCredentials: GitCredentials?,
    onDismiss: () -> Unit,
    onSave: (username: String, token: String) -> Unit
) {
    var username by remember { mutableStateOf(currentCredentials?.username ?: "") }
    var token by remember { mutableStateOf(currentCredentials?.tokenOrPassword ?: "") }
    var showToken by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Key, contentDescription = null) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Имя пользователя") },
                    placeholder = { Text("x-access-token") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Personal Access Token (PAT)") },
                    singleLine = true,
                    visualTransformation = if (showToken) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(
                                imageVector = if (showToken) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(username, token)
                onDismiss()
            }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
