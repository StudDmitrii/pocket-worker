package com.pocketworker.app.ui.projectdetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.pocketworker.app.core.git.CloneProgress
import com.pocketworker.app.core.git.GitCredentials
import com.pocketworker.app.core.git.GitEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRepoChoiceSheet(
    onDismiss: () -> Unit,
    onConnectClick: () -> Unit,
    onCreateClick: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Добавить репозиторий",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Репозиторий будет сохранён в папке этого проекта",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onConnectClick),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Подключить репозиторий",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Клонировать существующий Git-репозиторий по ссылке (HTTPS)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCreateClick),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CreateNewFolder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Создать репозиторий",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Создать новую пустую папку (с Git или без него)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CloneRepoDialog(
    isCloning: Boolean,
    cloneProgress: CloneProgress?,
    errorMessage: String?,
    fallbackCredentials: GitCredentials?,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
    onCancelClone: () -> Unit,
    onClone: (
        url: String,
        customName: String,
        branch: String,
        useAuth: Boolean,
        username: String,
        token: String,
        saveForRepo: Boolean
    ) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var customName by remember { mutableStateOf("") }
    var nameManuallyEdited by remember { mutableStateOf(false) }
    var branch by remember { mutableStateOf("") }
    var useAuth by remember { mutableStateOf(fallbackCredentials != null) }
    var username by remember { mutableStateOf(fallbackCredentials?.username ?: "") }
    var token by remember { mutableStateOf(fallbackCredentials?.tokenOrPassword ?: "") }
    var saveCreds by remember { mutableStateOf(true) }
    var showToken by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isCloning) onDismiss() },
        icon = { Icon(Icons.Rounded.CloudDownload, contentDescription = null) },
        title = { Text("Подключить репозиторий") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Pinned error banner so it's immediately visible without closing or scrolling
                if (!errorMessage.isNullOrBlank()) {
                    DialogErrorBanner(
                        message = errorMessage,
                        onDismissError = onClearError
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { newUrl ->
                            url = newUrl
                            onClearError()
                            if (!nameManuallyEdited) {
                                customName = if (newUrl.isBlank()) "" else {
                                    ProjectDetailViewModel.deriveRepoNameFromUrl(newUrl)
                                }
                            }
                        },
                        label = { Text("Ссылка на Git-репозиторий") },
                        placeholder = { Text("https://github.com/user/repo.git") },
                        singleLine = true,
                        enabled = !isCloning,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = customName,
                        onValueChange = {
                            customName = it
                            nameManuallyEdited = it.isNotBlank()
                            onClearError()
                        },
                        label = { Text("Имя папки / репозитория") },
                        placeholder = { Text("Автоматически из ссылки") },
                        singleLine = true,
                        enabled = !isCloning,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = branch,
                        onValueChange = {
                            branch = it
                            onClearError()
                        },
                        label = { Text("Ветка (необязательно)") },
                        placeholder = { Text("По умолчанию (HEAD)") },
                        singleLine = true,
                        enabled = !isCloning,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Авторизация (PAT / пароль)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = useAuth,
                            onCheckedChange = {
                                useAuth = it
                                onClearError()
                            },
                            enabled = !isCloning
                        )
                    }

                    if (useAuth) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = {
                                username = it
                                onClearError()
                            },
                            label = { Text("Пользователь (или пусто для GitHub)") },
                            placeholder = { Text("x-access-token") },
                            singleLine = true,
                            enabled = !isCloning,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = token,
                            onValueChange = {
                                token = it
                                onClearError()
                            },
                            label = { Text("Personal Access Token / Пароль") },
                            singleLine = true,
                            enabled = !isCloning,
                            visualTransformation = if (showToken) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                IconButton(onClick = { showToken = !showToken }) {
                                    Icon(
                                        imageVector = if (showToken) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = "Показать/скрыть токен"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable(enabled = !isCloning) { saveCreds = !saveCreds }
                        ) {
                            Checkbox(
                                checked = saveCreds,
                                onCheckedChange = { saveCreds = it },
                                enabled = !isCloning
                            )
                            Text(
                                text = "Сохранить токен для этого репозитория",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    if (isCloning && cloneProgress != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = cloneProgress.stage,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (cloneProgress.total > 0) {
                            val fraction = (cloneProgress.completed.toFloat() / cloneProgress.total.toFloat())
                                .coerceIn(0f, 1f)
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isCloning) {
                OutlinedButton(onClick = onCancelClone) {
                    Text("Отменить")
                }
            } else {
                Button(
                    onClick = {
                        onClone(
                            url,
                            customName,
                            branch,
                            useAuth,
                            username,
                            token,
                            useAuth && saveCreds
                        )
                    },
                    enabled = url.isNotBlank()
                ) {
                    Text("Клонировать")
                }
            }
        },
        dismissButton = {
            if (!isCloning) {
                TextButton(onClick = onDismiss) {
                    Text("Закрыть")
                }
            }
        }
    )
}

@Composable
fun CreateRepoDialog(
    isCreating: Boolean,
    errorMessage: String?,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
    onCreate: (
        name: String,
        initGit: Boolean,
        initialBranch: String,
        createReadmeAndCommit: Boolean,
        remoteUrl: String,
        authorName: String,
        authorEmail: String
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var initGit by remember { mutableStateOf(true) }
    var initialBranch by remember { mutableStateOf("main") }
    var createReadme by remember { mutableStateOf(true) }
    var remoteUrl by remember { mutableStateOf("") }
    var authorName by remember { mutableStateOf(GitEngine.DEFAULT_AUTHOR_NAME) }
    var authorEmail by remember { mutableStateOf(GitEngine.DEFAULT_AUTHOR_EMAIL) }

    AlertDialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
        icon = { Icon(Icons.Rounded.AddCircleOutline, contentDescription = null) },
        title = { Text("Создать репозиторий") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!errorMessage.isNullOrBlank()) {
                    DialogErrorBanner(
                        message = errorMessage,
                        onDismissError = onClearError
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            onClearError()
                        },
                        label = { Text("Имя репозитория (папки)") },
                        placeholder = { Text("backend-api") },
                        singleLine = true,
                        enabled = !isCreating,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Инициализировать Git", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = initGit,
                            onCheckedChange = { initGit = it },
                            enabled = !isCreating
                        )
                    }

                    if (initGit) {
                        OutlinedTextField(
                            value = initialBranch,
                            onValueChange = { initialBranch = it },
                            label = { Text("Начальная ветка") },
                            singleLine = true,
                            enabled = !isCreating,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("main", "dev", "master").forEach { preset ->
                                FilterChip(
                                    selected = initialBranch == preset,
                                    onClick = { initialBranch = preset },
                                    label = { Text(preset) }
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable(enabled = !isCreating) { createReadme = !createReadme }
                        ) {
                            Checkbox(
                                checked = createReadme,
                                onCheckedChange = { createReadme = it },
                                enabled = !isCreating
                            )
                            Text(
                                text = "Создать README.md и первый коммит",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        OutlinedTextField(
                            value = remoteUrl,
                            onValueChange = { remoteUrl = it },
                            label = { Text("URL удалённого Git (необязательно)") },
                            placeholder = { Text("https://github.com/user/repo.git") },
                            singleLine = true,
                            enabled = !isCreating,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = authorName,
                            onValueChange = { authorName = it },
                            label = { Text("Имя автора (user.name)") },
                            singleLine = true,
                            enabled = !isCreating,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = authorEmail,
                            onValueChange = { authorEmail = it },
                            label = { Text("Email автора (user.email)") },
                            singleLine = true,
                            enabled = !isCreating,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (isCreating) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(
                        name,
                        initGit,
                        initialBranch,
                        createReadme,
                        remoteUrl,
                        authorName,
                        authorEmail
                    )
                },
                enabled = !isCreating && name.isNotBlank()
            ) {
                Text("Создать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isCreating) {
                Text("Отмена")
            }
        }
    )
}

@Composable
private fun DialogErrorBanner(
    message: String,
    onDismissError: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDismissError,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Скрыть ошибку",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
