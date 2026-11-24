package com.pocketworker.app.ui.projects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Key
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
fun AddProjectChoiceSheet(
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
                text = "Добавить проект",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Выберите способ добавления проекта на устройство",
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
                            text = "Подключить проект",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Клонировать Git-репозиторий по ссылке (HTTPS) на устройство",
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
                            text = "Создать проект",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Создать новый пустой проект с локальным Git-репозиторием",
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
fun CloneProjectDialog(
    isCloning: Boolean,
    cloneProgress: CloneProgress?,
    defaultCredentials: GitCredentials?,
    onDismiss: () -> Unit,
    onCancelClone: () -> Unit,
    onClone: (
        url: String,
        customName: String,
        branch: String,
        username: String,
        token: String,
        saveForProject: Boolean
    ) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var customName by remember { mutableStateOf("") }
    var nameManuallyEdited by remember { mutableStateOf(false) }
    var branch by remember { mutableStateOf("") }
    var useAuth by remember { mutableStateOf(defaultCredentials != null) }
    var username by remember { mutableStateOf(defaultCredentials?.username ?: "") }
    var token by remember { mutableStateOf(defaultCredentials?.tokenOrPassword ?: "") }
    var saveCreds by remember { mutableStateOf(true) }
    var showToken by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isCloning) onDismiss() },
        icon = { Icon(Icons.Rounded.CloudDownload, contentDescription = null) },
        title = { Text("Подключить проект") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { newUrl ->
                        url = newUrl
                        if (!nameManuallyEdited) {
                            customName = if (newUrl.isBlank()) "" else {
                                ProjectsViewModel.deriveRepoNameFromUrl(newUrl)
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
                    },
                    label = { Text("Название проекта") },
                    placeholder = { Text("Автоматически из ссылки") },
                    singleLine = true,
                    enabled = !isCloning,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = branch,
                    onValueChange = { branch = it },
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
                        onCheckedChange = { useAuth = it },
                        enabled = !isCloning
                    )
                }

                if (useAuth) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Пользователь (или пусто для GitHub)") },
                        placeholder = { Text("x-access-token") },
                        singleLine = true,
                        enabled = !isCloning,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
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
                            text = "Сохранить токен для этого проекта",
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
                            if (useAuth) username else "",
                            if (useAuth) token else "",
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
fun CreateProjectDialog(
    isCreating: Boolean,
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
        title = { Text("Создать пустой проект") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название проекта") },
                    placeholder = { Text("my-awesome-project") },
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
fun DefaultCredentialsDialog(
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
        title = { Text("Токен Git по умолчанию") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Хранится в зашифрованном Android Keystore и используется, если у проекта не задан отдельный токен.",
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
