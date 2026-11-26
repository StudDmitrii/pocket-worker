package com.pocketworker.app.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pocketworker.app.core.git.GitCredentials
import com.pocketworker.app.ui.theme.LocalIsDarkTheme
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
fun AnimatedRefreshIconButton(
    isRefreshing: Boolean,
    onClick: () -> Unit,
    contentDescription: String = "Обновить",
    modifier: Modifier = Modifier
) {
    val rotation = remember { Animatable(0f) }
    var tapCount by remember { mutableIntStateOf(0) }
    var completedTapCount by remember { mutableIntStateOf(0) }
    val currentRefreshing by rememberUpdatedState(isRefreshing)

    LaunchedEffect(isRefreshing, tapCount) {
        val needsManualSpin = tapCount > completedTapCount
        if (currentRefreshing || needsManualSpin) {
            while (currentRefreshing) {
                val start = rotation.value % 360f
                rotation.snapTo(start)
                val remaining = 360f - start
                val duration = ((remaining / 360f) * 700f).toInt().coerceAtLeast(120)
                rotation.animateTo(
                    targetValue = 360f,
                    animationSpec = tween(durationMillis = duration, easing = LinearEasing)
                )
                rotation.snapTo(0f)
                completedTapCount = tapCount
            }

            if (tapCount > completedTapCount) {
                val start = rotation.value % 360f
                rotation.snapTo(start)
                val remaining = 360f - start
                val duration = ((remaining / 360f) * 580f).toInt().coerceAtLeast(220)
                rotation.animateTo(
                    targetValue = 360f,
                    animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing)
                )
                rotation.snapTo(0f)
                completedTapCount = tapCount
            } else if (rotation.value % 360f > 0.5f) {
                val start = rotation.value % 360f
                val remaining = 360f - start
                val duration = ((remaining / 360f) * 420f).toInt().coerceAtLeast(100)
                rotation.animateTo(
                    targetValue = 360f,
                    animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing)
                )
                rotation.snapTo(0f)
            }
        } else if (rotation.value % 360f > 0.5f) {
            val start = rotation.value % 360f
            val remaining = 360f - start
            val duration = ((remaining / 360f) * 420f).toInt().coerceAtLeast(100)
            rotation.animateTo(
                targetValue = 360f,
                animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing)
            )
            rotation.snapTo(0f)
        }
    }

    IconButton(
        onClick = {
            tapCount++
            onClick()
        },
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Rounded.Refresh,
            contentDescription = contentDescription,
            modifier = Modifier.graphicsLayer {
                rotationZ = rotation.value
            }
        )
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

val FormFieldShape = RoundedCornerShape(14.dp)

@Composable
fun appSwitchColors(): SwitchColors {
    val isDark = LocalIsDarkTheme.current
    return if (isDark) {
        SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color(0xFFCBD5E1),
            uncheckedTrackColor = Color(0xFF1E293B),
            uncheckedBorderColor = Color(0xFF475569)
        )
    } else {
        SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = Color(0xFFCBD5E1),
            uncheckedBorderColor = Color(0xFF94A3B8)
        )
    }
}

@Composable
fun formTextFieldColors(): TextFieldColors {
    val isDark = LocalIsDarkTheme.current
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = if (isDark) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        unfocusedContainerColor = if (isDark) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    )
}

@Composable
fun FormOptionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
fun PocketFormDialog(
    title: String,
    icon: ImageVector,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    dismissEnabled: Boolean = true,
    pinnedBanner: (@Composable () -> Unit)? = null,
    footerActions: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = { if (dismissEnabled) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 6.dp,
            shadowElevation = 14.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 460.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        enabled = dismissEnabled
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Закрыть",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                if (pinnedBanner != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 14.dp)
                    ) {
                        pinnedBanner()
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 430.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    content = footerActions
                )
            }
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

    PocketFormDialog(
        title = title,
        subtitle = "Шифруется в Android Keystore",
        icon = Icons.Rounded.Key,
        onDismiss = onDismiss,
        footerActions = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
            Button(
                onClick = {
                    onSave(username, token)
                    onDismiss()
                },
                shape = FormFieldShape,
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                Text("Сохранить")
            }
        }
    ) {
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
            shape = FormFieldShape,
            colors = formTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Personal Access Token (PAT)") },
            singleLine = true,
            shape = FormFieldShape,
            colors = formTextFieldColors(),
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
}
