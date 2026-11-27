package com.pocketworker.app.core.i18n

import com.pocketworker.app.core.storage.AiProvider

/**
 * Russian localization catalog for PocketWorker.
 */
object StringsRu : AppStrings {
    // ── Common ──────────────────────────────────────────────────────────────────────────────
    override val commonBack = "Назад"
    override val commonClose = "Закрыть"
    override val commonCancel = "Отмена"
    override val commonSave = "Сохранить"
    override val commonCreate = "Создать"
    override val commonDelete = "Удалить"
    override val commonRename = "Переименовать"
    override val commonEdit = "Изменить"
    override val commonReset = "Сбросить"
    override val commonRefresh = "Обновить"
    override val commonSettings = "Настройки"
    override val commonActions = "Действия"
    override val commonClear = "Очистить"
    override val commonCopy = "Копировать"
    override val commonCopied = "Скопировано"
    override val commonNewBadge = "Новый"
    override val commonUsername = "Имя пользователя"
    override val commonEncryptedInKeystore = "Шифруется в Android Keystore"
    override val commonShowHideToken = "Показать/скрыть токен"
    override val commonHideError = "Скрыть ошибку"
    override val commonNotSet = "Не задан"
    override val commonDone = "Выполнено"
    override val commonNotDone = "Не выполнено"
    override val commonError = "Ошибка"
    override val commonConfirmation = "Подтверждение"
    override val commonAllow = "Разрешить"
    override val commonStop = "Остановить"
    override val commonSend = "Отправить"
    override val commonRetry = "Повторить"
    override val commonSkip = "Пропустить"
    override val commonNext = "Дальше"
    override val commonStartWorking = "К работе"
    override val commonDismissNotification = "Скрыть уведомление"

    override fun formatBytes(bytes: Long) = "$bytes Б"
    override fun formatKilobytes(kb: Long) = "$kb КБ"
    override fun formatMegabytes(mbFormatted: String) = "$mbFormatted МБ"
    override fun gitTransportAuthError(raw: String) =
        "Ошибка авторизации: проверьте логин и Personal Access Token (PAT).\nДетали: $raw"
    override fun gitTransportForbiddenError(raw: String) =
        "Доступ запрещён (403): убедитесь, что токен имеет права на этот репозиторий.\nДетали: $raw"
    override fun gitTransportNotFoundError(raw: String) =
        "Репозиторий или ветка не найдены (404). Проверьте ссылку и права доступа.\nДетали: $raw"
    override fun gitTransportConnectionError(raw: String) =
        "Не удалось подключиться к серверу. Проверьте интернет и адрес репозитория.\nДетали: $raw"

    // ── Home (Projects) ─────────────────────────────────────────────────────────────────────
    override val homeTitle = "Проекты"
    override val homeWorkspaceFolder = "Рабочая папка"
    override fun homeAllProjectsCount(count: Int) = "Все проекты ($count)"
    override val homeRefreshList = "Обновить список"
    override val homeNewProject = "Новый проект"
    override val homeEditProject = "Редактировать проект"
    override fun homeDeleteProjectTitle(name: String) = "Удалить проект «$name»?"
    override fun homeDeleteProjectMessage(repoCount: Int) =
        "Папка проекта и все входящие в неё локальные репозитории ($repoCount) будут удалены с устройства. Удалённые репозитории в облаке не пострадают."
    override val homeEmptyTitle = "Пока нет проектов"
    override val homeEmptySubtitle =
        "Создайте проект-папку, чтобы объединить внутри несколько репозиториев (клиент, бэкенд, админку, сервисы)."
    override val homeCreateFirstProject = "Создать первый проект"
    override val homeProjectMenu = "Меню проекта"
    override val homeRenameOrDescription = "Переименовать / описание"
    override val homeDeleteProjectAction = "Удалить проект"
    override fun homeDirtyReposBadge(count: Int) = "С изменениями: $count"
    override fun homeEmptyProjectHint(formattedTime: String) =
        "Нажмите, чтобы подключить или создать репозитории • $formattedTime"
    override val homeProjectCreateSubtitle = "Папка-контейнер для ваших репозиториев"
    override val homeProjectEditSubtitle = "Измените название или заметку о проекте"
    override val homeProjectNameLabel = "Название проекта"
    override val homeProjectNamePlaceholder = "Например: Shop Platform"
    override val homeProjectDescLabel = "Описание (необязательно)"
    override val homeProjectDescPlaceholder = "Клиент, бэкенд, админка и сервисы"
    override val homeOpenProjectImmediately = "Сразу открыть проект"
    override val homeOpenProjectImmediatelyHint = "Перейти к добавлению репозиториев после создания"
    override fun formatRepoCount(count: Int): String {
        val mod100 = count % 100
        val mod10 = count % 10
        val word = when {
            mod100 in 11..14 -> "репозиториев"
            mod10 == 1 -> "репозиторий"
            mod10 in 2..4 -> "репозитория"
            else -> "репозиториев"
        }
        return "$count $word"
    }
    override val homeErrEnterProjectName = "Введите название проекта"
    override fun homeInfoProjectCreated(name: String) = "Проект «$name» создан"
    override val homeErrCreateProject = "Не удалось создать проект"
    override val homeErrEmptyProjectName = "Название проекта не может быть пустым"
    override val homeInfoProjectUpdated = "Проект обновлён"
    override val homeInfoProjectDeleted = "Проект удалён"
    override val homeInfoDefaultTokenCleared = "Токен по умолчанию очищен"
    override val homeInfoDefaultTokenSaved = "Токен по умолчанию сохранён"

    // ── Project Detail (Repositories) ───────────────────────────────────────────────────────
    override val projectBackToProjects = "К проектам"
    override val projectFallbackTitle = "Проект"
    override val projectFallbackInLocation = "проекте"
    override fun projectRepositoriesCount(count: Int) = "Репозитории ($count)"
    override val projectAddRepo = "Добавить репозиторий"
    override fun projectDeleteRepoTitle(name: String) = "Удалить репозиторий «$name»?"
    override val projectDeleteRepoMessage =
        "Локальная папка репозитория и его история на устройстве будут удалены. Удалённый репозиторий в облаке не пострадает."
    override fun projectEmptyTitle(projectName: String) = "В проекте «$projectName» пока пусто"
    override val projectEmptySubtitle =
        "Добавьте сюда репозитории (например, client, backend, admin, services): клонируйте по Git-ссылке, загрузите группу или создайте новый."
    override val projectDeleteRepoDesc = "Удалить репозиторий"
    override val projectRepoStatusNoCommits = "Нет коммитов"
    override val projectRepoStatusClean = "Чисто"
    override fun projectRepoStatusChanges(count: Int) = "Изменений: $count"
    override val projectRepoNoGit = "Без Git (можно инициализировать внутри)"
    override val projectErrNotFound = "Проект не найден"
    override val projectErrLoadFailed = "Не удалось загрузить проект"
    override val projectErrEnterRepoUrl = "Укажите ссылку на Git-репозиторий"
    override val projectCloneConnecting = "Подключение к репозиторию..."
    override fun projectInfoRepoAdded(name: String) = "Репозиторий «$name» добавлен в проект"
    override val projectCloneCancelled = "Клонирование отменено"
    override val projectErrEnterGroupUrl = "Укажите ссылку на группу GitLab или профиль/организацию GitHub"
    override val projectErrGroupNoRepos = "По этой ссылке не найдено доступных репозиториев"
    override val projectErrSelectAtLeastOneRepo = "Отметьте хотя бы один репозиторий"
    override fun projectGroupConnecting(repoName: String) = "Подключение к $repoName..."
    override fun projectGroupCancelled(succeeded: Int, total: Int) =
        "Групповое клонирование отменено (загружено: $succeeded из $total)"
    override fun projectGroupPartialErrorsHeader(succeeded: Int, total: Int) =
        "Загружено $succeeded из $total. Ошибки:\n"
    override fun projectGroupMoreErrors(remaining: Int) = "\n...и ещё $remaining"
    override fun projectGroupAddedCount(count: Int) = "Добавлено репозиториев: $count"
    override val projectErrEnterRepoName = "Введите название репозитория"
    override fun projectInfoRepoCreated(name: String) = "Репозиторий «$name» создан"
    override val projectErrCreateRepo = "Не удалось создать репозиторий"
    override val projectInfoRepoDeleted = "Репозиторий удалён"
    override val projectInfoProjectTokenCleared = "Токен проекта очищен"
    override val projectInfoProjectTokenSaved = "Токен проекта сохранён"

    // Repo Dialogs
    override val repoAddSheetSubtitle = "Репозиторий будет сохранён в папке этого проекта"
    override val repoConnectTitle = "Подключить репозиторий"
    override val repoConnectSubtitle = "Клонировать существующий Git-репозиторий по ссылке (HTTPS)"
    override val repoAddGroupTitle = "Добавить группу"
    override val repoAddGroupSubtitle = "Загрузить несколько репозиториев по ссылке GitHub или GitLab"
    override val repoCreateTitle = "Создать репозиторий"
    override val repoCreateSubtitle = "Создать новую пустую папку (с Git или без него)"
    override val repoCloneDialogSubtitle = "Клонирование Git-репозитория по HTTPS"
    override val repoCloneAction = "Клонировать"
    override val repoUrlLabel = "Ссылка на Git-репозиторий"
    override val repoFolderNameLabel = "Имя папки / репозитория"
    override val repoFolderNamePlaceholder = "Автоматически из ссылки"
    override val repoBranchOptionalLabel = "Ветка (необязательно)"
    override val repoBranchOptionalPlaceholder = "По умолчанию (HEAD)"
    override val repoSaveTokenForRepo = "Сохранить токен для этого репозитория"
    override val repoCreateDialogSubtitle = "Новая локальная папка внутри проекта"
    override val repoNameLabel = "Имя репозитория (папки)"
    override val repoInitGitTitle = "Инициализировать Git"
    override val repoInitGitSubtitle = "Создать локальный Git-репозиторий сразу"
    override val repoCreateReadmeTitle = "README.md и первый коммит"
    override val repoCreateReadmeSubtitle = "Сразу подготовить стартовую ветку"
    override val repoInitialBranchLabel = "Начальная ветка"
    override val repoRemoteUrlOptionalLabel = "URL удалённого Git (необязательно)"
    override val repoAuthorNameLabel = "Имя автора (user.name)"
    override val repoAuthorEmailLabel = "Email автора (user.email)"
    override val repoAuthTitle = "Авторизация (PAT / пароль)"
    override val repoAuthSubtitle = "Для приватных репозиториев и групп"
    override val repoAuthUserLabel = "Пользователь (или пусто для GitHub)"
    override val repoAuthTokenLabel = "Personal Access Token / Пароль"
    override val repoGroupDialogSubtitle = "Массовое клонирование из GitHub / GitLab"
    override val repoGroupUrlLabel = "Ссылка на группу GitLab или GitHub"
    override val repoGroupUrlPlaceholder = "https://gitlab.com/group или https://github.com/org"
    override val repoGroupSaveTokenLabel = "Сохранять токен для скачанных репозиториев"
    override val repoGroupFindRepos = "Найти репозитории"
    override val repoGroupRefreshList = "Обновить список"
    override val repoGroupSearching = "Поиск репозиториев по ссылке..."
    override fun repoGroupFoundSelected(found: Int, selected: Int) =
        "Найдено: $found • Выбрано: $selected"
    override val repoGroupUnselectAll = "Снять все"
    override val repoGroupSelectAll = "Выбрать все"
    override val repoGroupPrivateDesc = "Приватный"
    override val repoGroupAlreadyAdded = "Уже добавлен"
    override fun repoGroupDownloading(current: Int, total: Int, repoName: String) =
        "Скачивание $current из $total: $repoName"
    override fun repoGroupDownloadSelected(count: Int) = "Скачать выбранные ($count)"

    // ── Workspace (Tabs, Files, Editor, History) ────────────────────────────────────────────
    override val workspaceBackToProjectRepos = "К репозиториям проекта"
    override val workspaceRepoFallbackTitle = "Репозиторий"
    override val workspaceTabFiles = "Файлы"
    override val workspaceTabHistory = "История"
    override val workspaceErrRepoNotFound = "Репозиторий не найден"
    override val workspaceErrLoadRepo = "Ошибка загрузки репозитория"
    override val workspaceErrOpenFile = "Не удалось открыть файл"
    override fun workspaceInfoFileDeletedExternally(path: String) = "Файл «$path» был удалён"
    override fun workspaceInfoFileSaved(path: String) = "Файл сохранён: $path"
    override val workspaceErrSaveFile = "Ошибка сохранения файла"
    override val workspaceBusyCreateFile = "Создание файла..."
    override val workspaceBusyCreateFolder = "Создание папки..."
    override val workspaceBusyRename = "Переименование..."
    override val workspaceBusyDelete = "Удаление..."
    override val workspaceBusyInitGit = "Инициализация Git..."
    override val workspaceInfoGitInitialized = "Git инициализирован"
    override val workspaceBusyStageFile = "Индексация..."
    override val workspaceBusyStageAll = "Индексация всех изменений..."
    override val workspaceBusyUnstageFile = "Снятие индексации..."
    override val workspaceBusyUnstageAll = "Снятие индексации всех файлов..."
    override val workspaceBusyDiscardFile = "Откат изменений..."
    override fun workspaceInfoDiscardFileDone(path: String) = "Изменения в $path отменены"
    override val workspaceBusyDiscardAll = "Откат всех изменений..."
    override val workspaceInfoDiscardAllDone = "Все рабочие изменения отменены"
    override val workspaceBusyCommitAndPush = "Коммит и отправка..."
    override val workspaceBusyCommit = "Создание коммита..."
    override fun workspaceInfoCommitAndPushDone(shortId: String, pushMsg: String) =
        "Коммит $shortId создан. $pushMsg"
    override fun workspaceInfoCommitDone(shortId: String, message: String) =
        "Создан коммит $shortId: $message"
    override val workspaceBusyCreateBranch = "Создание ветки..."
    override fun workspaceInfoBranchCreatedAndCheckedOut(name: String) =
        "Переключено на новую ветку «$name»"
    override fun workspaceInfoBranchCreated(name: String) = "Ветка «$name» создана"
    override val workspaceBusyCheckoutBranch = "Переключение ветки..."
    override val workspaceInfoBranchSwitched = "Текущая ветка изменена"
    override val workspaceBusyRenameBranch = "Переименование ветки..."
    override fun workspaceInfoBranchRenamed(oldName: String, newName: String) =
        "Ветка «$oldName» переименована в «$newName»"
    override val workspaceBusyDeleteBranch = "Удаление ветки..."
    override fun workspaceInfoBranchDeleted(name: String) = "Ветка «$name» удалена"
    override fun workspaceBusyMergeBranch(source: String) = "Слияние ветки «$source»..."
    override fun workspaceMergeConflictsNote(conflicts: String) = " Конфликты: $conflicts"
    override fun workspaceMergeFailedStatus(status: String, conflictNote: String) =
        "Слияние завершилось со статусом $status.$conflictNote"
    override val workspaceBusyAddRemote = "Добавление remote..."
    override fun workspaceInfoRemoteAdded(name: String) = "Remote «$name» добавлен"
    override val workspaceBusyUpdateRemote = "Обновление remote..."
    override fun workspaceInfoRemoteUpdated(name: String) = "URL для «$name» обновлён"
    override val workspaceBusyRemoveRemote = "Удаление remote..."
    override fun workspaceInfoRemoteRemoved(name: String) = "Remote «$name» удалён"
    override fun workspaceBusyFetch(remote: String) = "Fetch из $remote..."
    override fun workspaceBusyPullRebase(remote: String) = "Pull (rebase) из $remote..."
    override fun workspaceBusyPull(remote: String) = "Pull из $remote..."
    override fun workspaceBusyForcePush(remote: String) = "Force Push в $remote..."
    override fun workspaceBusyPush(remote: String) = "Push в $remote..."
    override fun workspaceBusySync(remote: String) = "Синхронизация с $remote..."
    override val workspaceBusyStashCreate = "Сохранение в stash..."
    override val workspaceInfoStashEmpty = "Нет изменений для сохранения в stash"
    override fun workspaceInfoStashCreated(message: String) = "Сохранено в stash: $message"
    override fun workspaceBusyStashApply(index: Int) = "Применение stash@{$index}..."
    override fun workspaceInfoStashApplied(index: Int) = "Применён stash@{$index}"
    override fun workspaceBusyStashPop(index: Int) = "Применение и удаление stash@{$index}..."
    override fun workspaceInfoStashPopped(index: Int) = "Применён и удалён stash@{$index}"
    override fun workspaceBusyStashDrop(index: Int) = "Удаление stash@{$index}..."
    override fun workspaceInfoStashDropped(index: Int) = "Удалён stash@{$index}"
    override val workspaceBusyStashClear = "Очистка всех stash..."
    override val workspaceInfoStashCleared = "Все отложенные изменения (stash) очищены"
    override fun workspaceBusyCreateTag(name: String) = "Создание тега «$name»..."
    override fun workspaceInfoTagCreated(name: String) = "Тег «$name» создан"
    override fun workspaceBusyDeleteTag(name: String) = "Удаление тега «$name»..."
    override fun workspaceInfoTagDeleted(name: String) = "Тег «$name» удалён"
    override fun workspaceBusyPushTags(remote: String) = "Отправка тегов в $remote..."
    override val workspaceInfoRepoTokenCleared =
        "Токен репозитория удалён (будет использоваться токен проекта или по умолчанию)"
    override val workspaceInfoRepoTokenSaved = "Токен доступа репозитория сохранён"
    override val workspaceBusySaveAuthor = "Сохранение настроек автора..."
    override val workspaceInfoAuthorUpdated = "Автор коммитов обновлён"
    override val workspaceDiffEmpty = "Нет текстовых отличий"
    override val workspaceErrDiffBuild = "Не удалось построить diff"
    override val workspaceBusyLoadHistory = "Загрузка истории..."
    override val workspaceErrLoadHistory = "Ошибка загрузки истории"
    override val workspaceErrLoadCommit = "Не удалось загрузить коммит"
    override val workspaceErrDiff = "Ошибка diff"

    // Files tab
    override val filesNavigateUp = "Вверх"
    override val filesSearchDesc = "Поиск файлов"
    override val filesToggleGitDirDesc = "Показать/скрыть .git"
    override val filesNewFile = "Новый файл"
    override val filesNewFolder = "Новая папка"
    override val filesSearchPlaceholder = "Поиск по имени или пути файла..."
    override val filesNothingFound = "Ничего не найдено"
    override val filesFolderEmpty = "Папка пуста"
    override val filesNewFileNameLabel = "Имя файла (например, notes.md)"
    override val filesNewFolderNameLabel = "Имя папки"
    override val filesNewNameLabel = "Новое имя"
    override fun filesDeleteTitle(name: String) = "Удалить «$name»?"
    override val filesDeleteFolderConfirm = "Папка и всё её содержимое будут удалены с устройства."
    override fun filesDeleteFileConfirm(path: String) = "Файл «$path» будет удалён."
    override val filesToolsDesc = "Инструменты"
    override val filesSyntaxCheckTitle = "Проверка синтаксиса"
    override fun filesSyntaxCheckScopeSubtitle(scope: String) = "Область: $scope"
    override val filesSyntaxCheckSingleFile = "Проверить синтаксис"
    override val filesSyntaxChecking = "Проверяю синтаксис файлов..."
    override val filesSyntaxNoErrorsTitle = "Ошибок синтаксиса не найдено"
    override fun filesSyntaxCheckedSummary(checked: Int, skipped: Int) =
        if (skipped > 0) "Проверено файлов: $checked (пропущено: $skipped)" else "Проверено файлов: $checked"
    override fun filesSyntaxIssuesSummary(errors: Int, warnings: Int, filesWithIssues: Int) =
        "Ошибок: $errors • Предупреждений: $warnings • Файлов с проблемами: $filesWithIssues"
    override val filesSyntaxNoFilesToCheck = "В этой папке нет текстовых файлов для проверки."
    override val filesSyntaxOpenFileHint = "Нажмите на файл, чтобы открыть его в редакторе"
    override val filesSyntaxCheckAgain = "Проверить снова"
    override fun filesSyntaxLineCol(line: Int, col: Int) = "Стр. $line, кол. $col"
    override val filesSyntaxErrorBadge = "Ошибка"
    override val filesSyntaxWarningBadge = "Предупреждение"

    // File editor
    override val editorModifiedSuffix = " • изменён"
    override val editorViewModeDesc = "Режим просмотра"
    override val editorEditModeDesc = "Редактировать"
    override fun editorBinaryUnsupported(size: String) =
        "Бинарный файл ($size) не поддерживается текстовым редактором."
    override fun editorTooLarge(size: String) =
        "Файл слишком большой ($size) для открытия в редакторе."
    override fun editorZoomReset(percent: String) = "$percent  •  сброс"
    override val editorUnsavedTitle = "Несохранённые изменения"
    override fun editorUnsavedMessage(path: String) =
        "Сохранить изменения в файле «$path» перед выходом?"
    override val editorExitWithoutSaving = "Выйти без сохранения"

    // History tab
    override val historyNoGit = "Git не инициализирован в этом проекте"
    override val historyBranchLabel = "Ветка истории:"
    override fun historyBranchCurrent(name: String) = "$name (текущая)"
    override val historyEmptyTitle = "История коммитов пуста"
    override val historyEmptySubtitle =
        "Сделайте первый коммит во вкладке Git, и здесь появится история выбранной ветки."
    override val historyLoadOlder = "Загрузить более ранние коммиты"

    // ── Git Control Tab, Action Sheet & Dialogs ─────────────────────────────────────────────
    override val gitNotInitializedTitle = "Git не инициализирован"
    override val gitNotInitializedSubtitle =
        "Этот репозиторий пока без Git. Инициализируйте его, чтобы работать с изменениями, ветками и синхронизацией."
    override val gitInitButton = "Инициализировать Git (ветка main)"
    override val gitSelectBranchDesc = "Выбрать ветку"
    override val gitMenuDesc = "Меню Git"
    override val gitCommitMessagePlaceholder = "Сообщение коммита..."
    override val gitAmendBanner = "Режим Amend (дополнить последний коммит)"
    override val gitDisableAmend = "Отключить Amend"
    override val gitCommitOptionsDesc = "Варианты коммита"
    override val gitCommitStaged = "Commit (подготовленные)"
    override val gitCommitAmend = "Commit (Amend)"
    override val gitStagedSectionTitle = "Подготовлено (Staged)"
    override val gitUnstageAllDesc = "Убрать все из индекса"
    override val gitUnstageDesc = "Убрать из индекса"
    override val gitChangesSectionTitle = "Изменения (Changes)"
    override val gitDiscardAllDesc = "Откатить все изменения"
    override val gitStageAllDesc = "Добавить все в индекс"
    override val gitWorkingTreeClean = "Рабочая директория чиста"
    override val gitNoUnstagedChanges = "Нет неподготовленных изменений"
    override val gitStageDesc = "Добавить в индекс"
    override val gitStashSectionTitle = "Stash (Отложенные)"
    override val gitDeleteStashDesc = "Удалить stash"
    override val gitDiscardFileTitle = "Откатить изменения?"
    override fun gitDiscardFileMessage(path: String) =
        "Все несохранённые изменения в файле «$path» будут потеряны."
    override val gitDiscardAction = "Откатить"
    override val gitDiscardAllTitle = "Откатить все изменения?"
    override val gitDiscardAllMessage =
        "Все локальные изменения в отслеживаемых файлах будут сброшены, а новые (untracked) файлы — удалены."
    override val gitDiscardAllAction = "Откатить всё"
    override val gitForcePushTitle = "Выполнить Force Push?"
    override fun gitForcePushMessage(remote: String) =
        "Удалённая ветка в «$remote» будет принудительно перезаписана вашей локальной историей."
    override fun gitRemoveRemoteTitle(name: String) = "Удалить remote «$name»?"
    override fun gitRemoveRemoteMessage(url: String) =
        "Ссылка $url будет отвязана от локального проекта."
    override fun gitDropStashTitle(index: Int) = "Удалить stash@{$index}?"
    override fun gitDropStashMessage(message: String) =
        "Отложенные изменения «$message» будут безвозвратно удалены."
    override val gitClearStashesTitle = "Очистить все Stash?"
    override fun gitClearStashesMessage(count: Int) =
        "Все сохранённые записи Stash ($count) будут удалены."
    override fun gitDeleteTagTitle(name: String) = "Удалить тег «$name»?"
    override val gitDeleteTagMessage = "Локальный тег будет удалён из репозитория."

    // Git Action Sheet
    override val gitSubmenuRoot = "Действия Git"
    override val gitSubmenuCommit = "Коммит (Commit)"
    override val gitSubmenuChanges = "Изменения (Changes)"
    override val gitSubmenuPullPush = "Синхронизация (Pull, Push)"
    override val gitSubmenuRemote = "Удалённые (Remote)"
    override val gitSubmenuStash = "Отложенные (Stash)"
    override val gitSubmenuTags = "Теги (Tags)"
    override fun gitBranchPrefix(branch: String) = "Ветка: $branch"
    override val gitViewAsTreeTitle = "Вид изменений: дерево"
    override val gitViewAsTreeSubtitleOn = "Группировка по папкам"
    override val gitViewAsTreeSubtitleOff = "Плоский список файлов"
    override val gitMenuRemoteSubtitle = "Управление подключениями Git"
    override val gitMenuStashSubtitle = "Отложить изменения, Pop, Apply, Drop"
    override val gitMenuTagsSubtitle = "Создать тег, удалить, отправить теги"
    override val gitMenuOutputSubtitle = "Журнал выполненных Git-команд"
    override fun gitCommitStagedSubtitleReady(count: Int) = "Создать коммит из индекса ($count)"
    override val gitCommitStagedSubtitleHint = "Введите сообщение и подготовьте файлы"
    override val gitCommitAllSubtitle = "Индексировать все изменения и создать коммит"
    override fun gitCommitPushSubtitleReady(remote: String) =
        "Создать коммит и сразу отправить в $remote"
    override val gitNoRemoteConnected = "Нет подключённого remote"
    override val gitAmendToggleOn = "Выключить режим Amend"
    override val gitAmendToggleOff = "Включить режим Amend"
    override val gitAmendToggleSubtitle = "Дополнить последний коммит вместо создания нового"
    override val gitOnBadge = "ВКЛ"
    override fun gitStageAllSubtitle(count: Int) = "Добавить все изменения в индекс ($count)"
    override fun gitUnstageAllSubtitle(count: Int) = "Убрать все файлы из индекса ($count)"
    override val gitDiscardAllSubtitle =
        "Откатить все несохранённые изменения и удалить untracked файлы"
    override val gitSyncSubtitle = "Сначала получить изменения, затем отправить свои"
    override fun gitPullSubtitle(remote: String) = "Загрузить и объединить изменения из $remote"
    override val gitPullRebaseSubtitle = "Перебазировать локальные коммиты поверх удалённых"
    override val gitPullFromSubtitle = "Выбрать удалённый репозиторий для Pull"
    override fun gitPushSubtitle(remote: String) = "Отправить коммиты в $remote"
    override val gitPushToSubtitle = "Выбрать удалённый репозиторий для Push"
    override val gitForcePushSubtitle = "Принудительно перезаписать удалённую ветку"
    override val gitFetchSubtitle = "Обновить информацию об удалённых ветках без слияния"
    override val gitAddRemoteTitle = "Добавить remote..."
    override val gitAddRemoteSubtitle = "Привязать новый удалённый репозиторий по URL"
    override val gitNoRemotesConfigured = "Удалённые репозитории не настроены."
    override val gitStashIncludeUntrackedTitle = "Stash (включая untracked)..."
    override val gitStashIncludeUntrackedSubtitle = "Отложить все текущие изменения и новые файлы"
    override val gitStashTrackedOnlyTitle = "Stash (только отслеживаемые)..."
    override val gitStashTrackedOnlySubtitle = "Не трогать новые (untracked) файлы"
    override val gitStashPopLatestTitle = "Pop последний Stash"
    override val gitStashPopLatestSubtitle = "Применить stash@{0} и удалить его из списка"
    override val gitStashApplyLatestTitle = "Apply последний Stash"
    override val gitStashApplyLatestSubtitle = "Применить stash@{0}, оставив копию в списке"
    override val gitStashClearAllTitle = "Очистить все Stash"
    override fun gitStashClearAllSubtitle(count: Int) = "Удалить все отложенные записи ($count)"
    override fun gitStashSavedHeader(count: Int) = "Сохранённые Stash ($count)"
    override val gitCreateTagTitle = "Создать тег..."
    override fun gitCreateTagSubtitle(branch: String) = "Отметить текущий коммит ($branch)"
    override val gitPushTagsTitle = "Отправить все теги (Push Tags)"
    override fun gitPushTagsSubtitle(remote: String) = "Отправить локальные теги в $remote"
    override val gitNoTagsYet = "Тегов пока нет."
    override fun gitLocalTagsHeader(count: Int) = "Локальные теги ($count)"
    override val gitDeleteTagDesc = "Удалить тег"

    // Git Dialogs
    override val gitCopyShaDesc = "Копировать SHA"
    override fun gitCommitParents(parents: String) = "Родители: $parents"
    override val gitCreateBranchFromCommit = "Создать ветку от этого коммита"
    override fun gitChangedFilesHeader(count: Int) = "Изменённые файлы ($count)"
    override fun gitRenamedFrom(oldPath: String) = "из $oldPath"
    override val gitNewBranchTitle = "Новая ветка"
    override fun gitNewBranchBase(base: String) = "От базы: $base"
    override val gitBranchNameLabel = "Имя ветки"
    override val gitCheckoutImmediately = "Сразу переключиться на ветку"
    override val gitRenameBranchTitle = "Переименовать ветку"
    override fun gitRenameBranchCurrent(name: String) = "Текущее имя: $name"
    override val gitNewBranchNameLabel = "Новое имя ветки"
    override val gitEditRemoteTitle = "Редактировать remote"
    override val gitRemoteDialogSubtitle = "Подключение к удалённому Git-репозиторию"
    override val gitRemoteNameLabel = "Имя remote"
    override val gitRepoAuthTitle = "Авторизация репозитория"
    override val gitRepoAuthSubtitle = "Для операций fetch, pull и push"
    override val gitRepoAuthFallbackHint =
        "Оставьте поле токена пустым, чтобы использовать токен проекта или токен по умолчанию."
    override val gitRepoAuthDirectHint =
        "Укажите логин и Personal Access Token (PAT) для fetch, pull и push."
    override val gitAuthorTitle = "Автор коммитов"
    override val gitAuthorRepoSubtitle = "Подпись коммитов в этом репозитории"
    override val gitAuthorSaveGlobally = "Сохранить также глобально"
    override val gitStashDialogTitle = "Сохранить в Stash"
    override val gitStashDialogSubtitle = "Временно отложить текущие изменения"
    override val gitStashAction = "В Stash"
    override val gitStashMessageLabel = "Описание (необязательно)"
    override val gitStashMessagePlaceholder = "Например: WIP авторизация"
    override val gitStashIncludeUntrackedOption = "Включать новые (untracked) файлы"
    override val gitTagDialogTitle = "Новый тег"
    override val gitTagDialogSubtitle = "Пометка текущего коммита версией"
    override val gitTagNameLabel = "Имя тега"
    override val gitTagAnnotationLabel = "Аннотация (необязательно)"
    override val gitTagAnnotationPlaceholder = "Релиз новой версии"
    override val gitSwitchBranchTitle = "Переключить ветку"
    override val gitNewBranchShort = "Новая"
    override val gitSearchBranchPlaceholder = "Поиск ветки..."
    override val gitLocalBranches = "Локальные ветки"
    override fun gitRenameBranchDesc(name: String) = "Переименовать ветку $name"
    override val gitRemoteBranches = "Удалённые ветки"
    override val gitUnbornBranchHint = "Ветка создастся автоматически после первого коммита"
    override val gitNoMatchingBranches = "Подходящие ветки не найдены"
    override val gitCopyLogDesc = "Копировать лог"
    override val gitClearLogDesc = "Очистить лог"
    override val gitOutputEmptyHint = "Выполненные Git-команды и их вывод появятся здесь."

    // ── Settings ────────────────────────────────────────────────────────────────────────────
    override val settingsTitle = "Настройки"
    override val settingsTabGeneral = "Общие"
    override val settingsTabAgent = "Агент"
    override val settingsSubtitleAgent = "ИИ-модель, ключ и поведение агента"
    override fun settingsSubtitleRepo(name: String) = "Репозиторий «$name» и приложение"
    override fun settingsSubtitleProject(name: String) = "Проект «$name» и приложение"
    override val settingsSubtitleGeneral = "Тема, язык, фон и доступ Git"
    override val settingsAppearanceTitle = "Внешний вид"
    override val settingsThemeTitle = "Тема оформления"
    override val settingsThemeAuto = "Авто"
    override val settingsThemeLight = "Светлая"
    override val settingsThemeDark = "Тёмная"
    override val settingsLanguageTitle = "Язык приложения"
    override val settingsLanguageSystem = "Системный"
    override val settingsLanguageRussian = "Русский"
    override val settingsLanguageEnglish = "English"
    override val settingsAnimatedBgTitle = "Анимация заднего фона"
    override val settingsAnimatedBgSubtitle =
        "Плавная анимация поверх градиента. Отключите для экономии заряда."
    override val settingsGitAndAuthTitle = "Git и авторизация"
    override val settingsScopeDefault = "По умолчанию"
    override val settingsDefaultTokenTitle = "Токен Git по умолчанию (PAT)"
    override val settingsDefaultTokenDialogTitle = "Токен Git по умолчанию"
    override val settingsDefaultTokenDialogDesc =
        "Хранится в зашифрованном Android Keystore и используется, если у проекта и репозитория не задан отдельный токен."
    override fun settingsTokenSavedInKeystore(user: String) = "Сохранён в Keystore ($user)"
    override val settingsDefaultAuthorTitle = "Автор коммитов по умолчанию"
    override fun settingsScopeProject(name: String) = "Проект «$name»"
    override val settingsProjectTokenTitle = "Токен проекта"
    override fun settingsProjectTokenDialogDesc(name: String) =
        "Общий токен для всех репозиториев проекта «$name». Используется, если у конкретного репозитория не задан свой токен."
    override fun settingsTokenSet(user: String) = "Задан ($user)"
    override val settingsProjectAuthorTitle = "Автор коммитов проекта"
    override fun settingsScopeRepo(name: String) = "Репозиторий «$name»"
    override val settingsRepoTokenTitle = "Токен репозитория"
    override val settingsRepoAuthorTitle = "Автор коммитов репозитория"
    override val settingsAboutTitle = "О приложении"
    override val settingsVersionSubtitle = "Версия 0.1.0 • Локальный Git и AI-ассистент"
    override val settingsGuideTitle = "Инструкция по работе"
    override val settingsGuideSubtitle = "Открыть слайд-гид по проектам, Git и AI-агенту"
    override val settingsGlobalAuthorSubtitle = "Используется по умолчанию во всех репозиториях"
    override fun settingsProjectAuthorSubtitle(name: String) =
        "Используется для всех репозиториев проекта «$name», если у репозитория не задан свой автор."

    // Agent Settings
    override val settingsModelAndConnectionTitle = "Модель и подключение"
    override val settingsProviderLabel = "Провайдер"
    override val settingsProviderLocalDownloadedDesc = "Модель скачана"
    override val settingsProviderKeySetDesc = "Ключ задан"
    override val settingsModelLabel = "Модель"
    override val settingsDownloadedDesc = "Скачана"
    override val settingsSelectedDesc = "Выбрана"
    override val settingsGeminiDirectHint =
        "Прямое подключение к Google AI Studio (aistudio.google.com)"
    override val settingsOpenRouterHint =
        "Бесплатные и облачные модели OpenRouter с поддержкой инструментов (openrouter.ai)"
    override val settingsHideKeyDesc = "Скрыть ключ"
    override val settingsShowKeyDesc = "Показать ключ"
    override val settingsProxyTitle = "Прокси"
    override val settingsProxyLocalSubtitle =
        "Использовать прокси при скачивании локальной модели"
    override val settingsProxyCloudSubtitle = "Отправлять все запросы к ИИ через прокси-сервер"
    override val settingsProxyAddressLabel = "Адрес прокси"
    override val settingsProxyFormatHint = "Формат: http://user:password@ip:port (или socks5://...)"
    override val settingsHideProxyDesc = "Скрыть адрес прокси"
    override val settingsShowProxyDesc = "Показать адрес прокси"
    override val settingsAgentBehaviorTitle = "Поведение агента"
    override val settingsSystemPromptLabel = "Системная инструкция (System Prompt)"
    override val settingsSystemPromptPlaceholder =
        "Отвечай кратко, поясняй изменения перед правкой файлов..."
    override val settingsShareContextTitle = "Передавать контекст папок и файла"
    override val settingsShareContextSubtitle = "Агент видит открытый проект, репозиторий и файл"
    override val settingsPermissionsTitle = "Разрешения"
    override val settingsPermissionsAsk = "Спрашивать"
    override val settingsPermissionsAuto = "Авто"
    override val settingsPermissionsSubtitle =
        "«Спрашивать» — подтверждать правки файлов и локальный Git. «Авто» — выполнять их без запроса; опасные действия (push, reset --hard, удаление) всегда требуют подтверждения."
    override val settingsSaveAgentButton = "Сохранить настройки агента"

    // Local Model Download UI
    override fun settingsLocalDownloading(label: String) = "Скачивание $label..."
    override val settingsLocalCancelDownload = "Отменить загрузку"
    override val settingsLocalReadyTitle = "Модель скачана и готова"
    override fun settingsLocalReadySubtitle(size: String) =
        "Размер на устройстве: $size • работает офлайн"
    override val settingsLocalDeleteButton = "Удалить файл модели"
    override val settingsLocalOfflineInfo =
        "Работает полностью на устройстве (LiteRT-LM) без API-ключа и без интернета после скачивания."
    override fun settingsLocalResumeHint(downloaded: String, total: String) =
        "Загружено ранее: $downloaded / $total — загрузка продолжится с места остановки."
    override val settingsLocalContinueDownload = "Продолжить загрузку"
    override val settingsLocalDownloadButton = "Скачать модель"
    override fun settingsLocalRamLowHint(totalGb: String, availGb: String) =
        "ОЗУ: $totalGb ГБ (свободно $availGb ГБ) • для стабильной работы рекомендуется CPU и модель E2B"
    override fun settingsLocalRamNormalHint(totalGb: String, availGb: String) =
        "ОЗУ: $totalGb ГБ (свободно $availGb ГБ)"
    override val settingsLocalGpuTitle = "Ускорение GPU (OpenCL)"
    override val settingsLocalGpuSubtitle =
        "Выкл. — запуск на CPU (меньше ОЗУ и без вылетов драйвера). Вкл. — быстрее на флагманах 12+ ГБ ОЗУ."
    override val settingsLocalDeleteDialogTitle = "Удалить локальную модель?"
    override fun settingsLocalDeleteDialogBody(label: String, size: String) =
        "Файл «$label» ($size) будет удалён с устройства. Чтобы снова использовать эту модель, её придётся скачать заново."

    // Settings ViewModel messages
    override val settingsInfoProjectAuthorReset =
        "Автор коммитов проекта сброшен (используется по умолчанию)"
    override val settingsInfoProjectAuthorSaved = "Автор коммитов проекта сохранён"
    override val settingsInfoRepoAuthorUpdated = "Автор коммитов репозитория обновлён"
    override val settingsInfoRepoAuthorReset = "Автор коммитов репозитория сброшен"
    override val settingsInfoGlobalAuthorSaved = "Глобальный автор коммитов сохранён"
    override val settingsInfoGlobalAuthorReset = "Автор коммитов по умолчанию сброшен"
    override val settingsInfoLocalModelDeleted = "Локальная модель удалена"
    override fun settingsInfoLocalModelActive(label: String) =
        "Локальная модель «$label» сохранена и активна"
    override val settingsInfoAgentSavedDownloadLocal =
        "Настройки агента сохранены (скачайте выбранную локальную модель)"
    override val settingsInfoAgentSaved = "Настройки ИИ и агента сохранены"

    // ── AI Chat, Context, Markdown, Floating Button & Providers ─────────────────────────────
    override val aiChatDrawerDesc = "Меню чатов"
    override val aiScrollToBottomDesc = "Прокрутить вниз"
    override val aiScrollToBottomLabel = "Вниз"
    override val aiChatHistoryTitle = "История чатов"
    override val aiRenameChatTitle = "Переименовать чат"
    override val aiRenameChatLabel = "Название чата"
    override val aiDeleteChatDesc = "Удалить чат"
    override val aiChatHistoryLocalHint = "История чатов сохраняется локально на устройстве"
    override val aiNewChat = "Новый чат"
    override val aiDeleteDialogTitle = "Удалить диалог?"
    override fun aiDeleteDialogBody(title: String) =
        "Диалог «$title» и вся его история сообщений будут удалены без возможности восстановления."
    override fun aiAttachedFilesLine(names: String) = "Файлы: $names"
    override val aiRetrySubtitle = "Откатить изменения и вернуть в поле ввода"
    override val aiCopyResponseDesc = "Копировать ответ"
    override fun aiActionsGroupTitle(count: Int) = "Действия ($count)"
    override val aiCollapseActionsDesc = "Свернуть действия"
    override val aiExpandActionsDesc = "Развернуть действия"
    override fun aiChangedFilesMore(names: String, remaining: Int) = "$names и ещё $remaining"
    override fun aiChangedFilesGroupTitle(count: Int) = "Изменённые файлы ($count)"
    override val aiCollapseFilesDesc = "Свернуть файлы"
    override val aiExpandFilesDesc = "Развернуть файлы"
    override val aiOpenDiffDesc = "Открыть diff"
    override val aiThinking = "Думает..."
    override val aiCollapseDetailsDesc = "Свернуть детали"
    override val aiExpandDetailsDesc = "Показать детали"
    override fun aiToolLabel(toolName: String) = "Инструмент: $toolName"
    override fun aiParamsLabel(preview: String) = "Параметры:\n$preview"
    override fun aiResultLabel(preview: String) = "Результат:\n$preview"
    override val aiOpenAgentSettings = "Открыть настройки агента"
    override val aiConfirmDangerousTitle = "Требуется подтверждение (опасное действие)"
    override val aiConfirmNormalTitle = "Подтвердите действие агента"
    override val aiAttachFileDesc = "Прикрепить файл"
    override val aiInputPlaceholderBusy = "Агент работает..."
    override val aiInputPlaceholderIdle = "Напишите сообщение..."
    override val aiRemoveAttachmentDesc = "Убрать файл"
    override fun aiAttachedFileHeader(name: String) = "[Прикреплённый файл: $name]"
    override fun aiAttachedBinaryHeader(name: String, mime: String) =
        "[Прикреплённый файл: $name ($mime)]"

    // AI ViewModel statuses & errors
    override val aiRollingBack = "Откат изменений..."
    override val aiReturnedToInput = "Сообщение возвращено в поле ввода"
    override fun aiRollbackCountSuffix(count: Int) = " • откачено изменений: $count"
    override fun aiNoSavedDiff(path: String) = "Нет сохранённого diff для файла $path."
    override fun aiAttachedFilesSummary(count: Int) = "Прикреплены файлы ($count)"
    override fun aiProviderNotConnected(providerName: String) =
        "Клиент для $providerName пока не подключён. Выберите доступного провайдера в «Настройки → Агент»."
    override fun aiLocalModelNotDownloaded(modelLabel: String) =
        "Локальная модель «$modelLabel» ещё не скачана. Откройте «Настройки → Агент» и нажмите «Скачать модель»."
    override fun aiMissingApiKey(providerName: String) =
        "Не указан API-ключ $providerName. Добавьте ключ в «Настройки → Агент»."
    override fun aiInvalidProxy(reason: String) = "Прокси включён, но адрес некорректен: $reason"
    override val aiLoadingLocalModel = "Загружаю локальную модель..."
    override val aiUnexpectedError = "Непредвиденная ошибка агента."
    override val aiAnalyzingResult = "Анализирует результат..."
    override val aiResponding = "Отвечает..."
    override val aiWebSearchRunning = "Ищу в сети..."
    override val aiWebSearchDone = "Поиск в сети завершён"
    override fun aiWaitingConfirmationStatus(summary: String) = "Ожидаю подтверждение: $summary"
    override val aiWaitingConfirmationTitle = "Ожидаю подтверждение..."
    override val aiRejectedByUser = "Отменено пользователем"
    override fun aiToolErrorTitle(code: String) = "Ошибка: $code"
    override fun aiMaxStepsReached(maxSteps: Int) =
        "Достигнут лимит шагов агента ($maxSteps) за один запрос. Вы можете продолжить диалог новым сообщением."

    override fun aiToolStatus(toolName: String): Pair<String, String> = when (toolName) {
        "web_search" -> aiWebSearchRunning to aiWebSearchDone
        "workspace_get_context" -> "Смотрю контекст..." to "Контекст получен"
        "workspace_list_projects" -> "Смотрю проекты..." to "Список проектов получен"
        "workspace_create_project" -> "Создаю проект..." to "Проект создан"
        "workspace_list_repos" -> "Смотрю репозитории..." to "Репозитории получены"
        "workspace_create_repo" -> "Создаю репозиторий..." to "Репозиторий создан"
        "workspace_clone_repo" -> "Клонирую репозиторий..." to "Репозиторий клонирован"
        "workspace_discover_group_repos" -> "Ищу репозитории в группе..." to "Репозитории найдены"
        "repo_overview" -> "Изучаю репозиторий..." to "Обзор репозитория готов"
        "fs_list_dir" -> "Смотрю папку..." to "Папка прочитана"
        "fs_find_files" -> "Ищу файлы..." to "Файлы найдены"
        "fs_grep" -> "Ищу в коде..." to "Поиск в коде завершён"
        "fs_read_file" -> "Читаю файл..." to "Файл прочитан"
        "fs_edit_file" -> "Редактирую файл..." to "Файл изменён"
        "fs_write_file" -> "Записываю файл..." to "Файл сохранён"
        "fs_replace_all" -> "Заменяю по файлам..." to "Замена выполнена"
        "fs_batch_structure" -> "Меняю структуру папок..." to "Структура обновлена"
        "fs_delete_path" -> "Удаляю..." to "Удалено"
        "git_exec" -> "Выполняю Git..." to "Git-команда выполнена"
        "check_push_readiness" -> "Проверяю готовность к push..." to "Проверка push завершена"
        "check_merge_conflicts" -> "Проверяю конфликты..." to "Проверка конфликтов завершена"
        "check_syntax" -> "Проверяю синтаксис..." to "Синтаксис проверен"
        else -> "Выполняю $toolName..." to "Выполнено: $toolName"
    }

    override fun aiSyntaxFilesCount(count: Int) = "$count файл(ов)"
    override val aiSyntaxChangedFiles = "изменённые файлы"

    // AI Context
    override val aiGreetingAllProjects =
        "Привет! Чем могу помочь? Сейчас ты находишься в папке со всеми проектами, я их вижу."
    override fun aiGreetingProjectNamed(name: String) =
        "Привет! Чем могу помочь? Сейчас ты находишься в папке проекта «$name», я вижу его репозитории."
    override val aiGreetingProjectUnnamed =
        "Привет! Чем могу помочь? Сейчас ты находишься в папке проекта, я вижу его репозитории."
    override fun aiGreetingRepoSubdirNamed(dir: String, repo: String) =
        "Привет! Чем могу помочь? Сейчас ты находишься в папке «$dir» репозитория «$repo», я её вижу."
    override fun aiGreetingRepoSubdirUnnamed(dir: String) =
        "Привет! Чем могу помочь? Сейчас ты находишься в папке «$dir» репозитория, я её вижу."
    override fun aiGreetingRepoRootNamed(repo: String) =
        "Привет! Чем могу помочь? Сейчас ты находишься в папке с репозиторием «$repo», я его вижу."
    override val aiGreetingRepoRootUnnamed =
        "Привет! Чем могу помочь? Сейчас ты находишься в папке с репозиторием, я его вижу."
    override fun aiGreetingFileWithRepo(file: String, repo: String) =
        "Привет! Чем могу помочь? Сейчас ты находишься в файле «$file» репозитория «$repo», я его вижу."
    override fun aiGreetingFileWithoutRepo(file: String) =
        "Привет! Чем могу помочь? Сейчас ты находишься в файле «$file», я его вижу."

    override val aiEmptyPromptAllProjects = "Над чем поработаем в проектах?"
    override fun aiEmptyPromptProjectNamed(name: String) = "Над чем поработаем в проекте «$name»?"
    override val aiEmptyPromptProjectUnnamed = "Над чем поработаем в проекте?"
    override fun aiEmptyPromptRepoSubdirNamed(dir: String, repo: String) =
        "Над чем поработаем в папке «$dir» репозитория «$repo»?"
    override fun aiEmptyPromptRepoNamed(repo: String) =
        "Над чем поработаем в репозитории «$repo»?"
    override fun aiEmptyPromptDirOnly(dir: String) = "Над чем поработаем в папке «$dir»?"
    override val aiEmptyPromptRepoUnnamed = "Над чем поработаем в репозитории?"
    override fun aiEmptyPromptFileNamed(fileName: String) =
        "Над чем поработаем в файле «$fileName»?"
    override val aiEmptyPromptFileUnnamed = "Над чем поработаем в файле?"

    override val aiSubtitleAllProjects = "Все проекты"
    override fun aiSubtitleProject(name: String) = "Проект: $name"
    override val aiSubtitleProjectFolder = "Папка проекта"
    override fun aiSubtitleRepo(name: String) = "Репозиторий: $name"
    override val aiSubtitleRepoUnnamed = "Репозиторий"
    override val aiSubtitleFile = "Файл"

    // Markdown & Floating AI Button
    override val mdCalloutNote = "Заметка"
    override val mdCalloutTip = "Совет"
    override val mdCalloutImportant = "Важно"
    override val mdCalloutWarning = "Внимание"
    override val mdCalloutCaution = "Осторожно"
    override val mdCodeDefaultLabel = "Код"
    override val mdCopyCodeDesc = "Копировать код"
    override val fabRevealAiDesc = "Выдвинуть кнопку AI"
    override val fabOpenAiDesc = "Открыть чат с AI"
    override val fabRevealFoldersDesc = "Выдвинуть кнопку возврата"
    override val fabReturnToFoldersDesc = "Вернуться в папки"

    // AI Providers & Models
    override fun providerDisplayName(provider: AiProvider): String = when (provider) {
        AiProvider.ANTHROPIC -> "Claude"
        AiProvider.GEMINI -> "Gemini"
        AiProvider.OPENROUTER -> "OpenRouter"
        AiProvider.OPENAI -> "OpenAI"
        AiProvider.LOCAL -> "Локальный"
    }

    override fun providerApiKeyLabel(provider: AiProvider): String = when (provider) {
        AiProvider.ANTHROPIC -> "API-ключ Claude (Android Keystore)"
        AiProvider.GEMINI -> "API-ключ Google AI Studio (Keystore)"
        AiProvider.OPENROUTER -> "API-ключ OpenRouter (Keystore)"
        AiProvider.OPENAI -> "API-ключ OpenAI (Android Keystore)"
        AiProvider.LOCAL -> ""
    }

    override fun modelLabel(modelId: String, fallback: String): String = when (modelId) {
        "gemma-4-E2B-it" -> "Gemma 4 E2B (~2.58 ГБ)"
        "gemma-4-E4B-it" -> "Gemma 4 E4B (~3.65 ГБ)"
        else -> fallback
    }

    override fun modelSubtitle(modelId: String, fallback: String): String = when (modelId) {
        "claude-sonnet-5-5" -> "Оптимальный баланс скорости и качества кода"
        "claude-opus-5-5" -> "Флагман для долгой агентской разработки"
        "claude-fable-5-2" -> "Глубокое рассуждение и сложные задачи"
        "claude-haiku-5-5" -> "Сверхбыстрая и экономичная модель"
        "gemini-4-neon" -> "Быстрая модель Gemini 4 для кода и инструментов"
        "gemini-4-carbon" -> "Флагман Gemini 4 для сложной архитектуры"
        "gemini-4-hydrogen" -> "Лёгкая и экономичная модель Gemini 4"
        "gemini-3.5-flash" -> "Быстрая универсальная модель"
        "gemini-3.5-flash-lite" -> "Максимальная скорость и низкая цена"
        "gemini-3.1-pro-preview" -> "Pro-модель с расширенным вызовом инструментов"
        "particle-ai/particle-1:free" -> "Агентская кодерская модель (DeepSeek V4.1 Flash, 1M)"
        "nvidia/nemotron-3-ultra-550b-a55b:free" -> "Крупная MoE-модель NVIDIA для оркестрации и сложных задач"
        "google/gemma-4-31b-it:free" -> "Плотная модель Google с нативным вызовом инструментов"
        "inclusionai/ling-3.2-flash:free" -> "Гибридная reasoning-модель с контекстом 1M"
        "baidu/ernie-5.5-flash-preview:free" -> "Бесплатная preview-модель Baidu через OpenRouter"
        "gpt-6.2-sol" -> "Сбалансированная модель для кода и агентов"
        "gpt-6.2-astra" -> "Флагман OpenAI для сложных задач и анализа"
        "gpt-6.1-terra" -> "Быстрая модель для агентских задач"
        "gpt-6-luna" -> "Экономичная модель для частых запросов"
        "gpt-5.5" -> "Надёжная модель с контекстом 1M+"
        "gpt-5.4" -> "Проверенная модель для работы с инструментами"
        "gemma-4-E2B-it" -> "Офлайн-модель для смартфонов (6–8+ ГБ ОЗУ)"
        "gemma-4-E4B-it" -> "Умнее для кода и инструментов (8–12+ ГБ ОЗУ)"
        else -> fallback
    }

    // ── Onboarding Guide ────────────────────────────────────────────────────────────────────
    override val onboardingTitle = "Инструкция"
    override fun onboardingSlideCounter(current: Int, total: Int) = "Слайд $current из $total"
    override val onboardingSlide1Title = "1. Проекты и папки"
    override val onboardingSlide1Desc =
        "На главном экране собирайте связанные репозитории в проекты — например, мобильное приложение, бэкенд и общие библиотеки."
    override val onboardingSlide1Bullet1 = "Нажмите «+» внизу экрана, чтобы создать новый проект."
    override val onboardingSlide1Bullet2 =
        "Карточка сразу показывает число репозиториев и количество несохранённых изменений."
    override val onboardingSlide2Title = "2. Репозитории и клонирование"
    override val onboardingSlide2Desc =
        "Внутри проекта добавляйте локальные репозитории, клонируйте по HTTPS-ссылке или загружайте сразу группу GitHub / GitLab."
    override val onboardingSlide2Bullet1 =
        "На каждой карточке видны текущая ветка, число изменений и последний коммит."
    override val onboardingSlide2Bullet2 = "Сохранённый PAT-токен подставляется автоматически."
    override val onboardingSlide3Title = "3. Редактор кода и Git"
    override val onboardingSlide3Desc =
        "Открывайте файлы с подсветкой синтаксиса, просматривайте diff по строкам и выполняйте все основные команды Git прямо с телефона."
    override val onboardingSlide3Bullet1 =
        "Поддерживаются commit, amend, ветки, merge, stash, теги, pull и push."
    override val onboardingSlide3Bullet2 = "Вкладка «Файлы» и вкладка «Git» всегда синхронизированы."
    override val onboardingSlide4Title = "4. AI-агент на любом экране"
    override val onboardingSlide4Desc =
        "Нажмите на круглую кнопку AI, чтобы открыть чат. Агент видит текущий проект, репозиторий или открытый файл и отвечает с красивым форматированием."
    override val onboardingSlide4Bullet1 =
        "Кнопку AI можно перетаскивать по экрану, чтобы она не мешала коду."
    override val onboardingSlide4Bullet2 =
        "Агент умеет искать и править файлы, проверять синтаксис и запускать Git."
    override val onboardingSlide5Title = "5. Действия, файлы и откат"
    override val onboardingSlide5Desc =
        "После ответа шаги агента сворачиваются в компактный блок, а изменённые файлы выводятся со статистикой строк из Git."
    override val onboardingSlide5Bullet1 =
        "Нажмите на изменённый файл в чате, чтобы сразу увидеть его Git diff."
    override val onboardingSlide5Bullet2 =
        "Нажмите на своё сообщение, чтобы повторить его и автоматически откатить правки агента."
    override val onboardingSlide6Title = "6. Настройки ИИ и доступа Git"
    override val onboardingSlide6Desc =
        "Выберите облачную модель или скачайте локальную Gemma 4 для работы офлайн. Настройте токены и автора коммитов на нужном уровне."
    override val onboardingSlide6Bullet1 =
        "Доступны Claude, Gemini (Google AI Studio), OpenRouter, OpenAI и офлайн Gemma 4."
    override val onboardingSlide6Bullet2 =
        "Git-токен и автор коммитов работают по иерархии: по умолчанию → проект → репозиторий."
    override val onboardingMockProjectDesc = "Android-приложение и backend-сервис"
    override val onboardingMockCommitMsg = "feat: обновлён экран настроек"
    override val onboardingMockGreetingCode = "Привет"
    override val onboardingMockAiButtonTitle = "Кнопка вызова AI-агента"
    override val onboardingMockAiButtonSubtitle = "Доступна поверх проектов, папок и редактора"
    override val onboardingMockAiResponse =
        "Готово! Добавил проверку токена в `AuthService.kt` и обновил **unit-тесты**."
    override val onboardingMockActionSearch = "Поиск файлов"
    override val onboardingMockActionRead = "Чтение файла"
    override val onboardingMockActionUpdated = "Файл обновлён"
    override val onboardingMockAiProviderLabel = "Провайдер ИИ"
    override val onboardingMockTokenSaved = "Сохранён в Keystore (dev-user)"

    // ── Notifications, Proxy, LLM, Local Model, Git & File Core Errors ──────────────────────
    override val notifFinishedErrorTitle = "Агент завершил задачу с ошибкой"
    override val notifFinishedSuccessTitle = "Агент закончил задачу"
    override fun notifOpenNamedChat(chatTitle: String) = "Чат «$chatTitle» — нажмите, чтобы открыть"
    override val notifOpenChat = "Нажмите, чтобы открыть чат"
    override val notifProgressChannelName = "Работа ИИ-агента"
    override val notifProgressChannelDesc =
        "Показывает текущее действие ИИ-агента, пока приложение свёрнуто"
    override val notifFinishedChannelName = "Завершение задач ИИ-агента"
    override val notifFinishedChannelDesc = "Уведомляет, когда ИИ-агент закончил задачу в фоне"
    override fun notifOngoingNamedTitle(chatTitle: String) = "ИИ-агент • $chatTitle"
    override val notifOngoingDefaultTitle = "ИИ-агент работает"

    override val proxyErrEmpty = "Укажите адрес прокси, например http://user:password@ip:port"
    override fun proxyErrUnsupportedScheme(scheme: String) =
        "Неподдерживаемая схема прокси «$scheme». Используйте http:// или socks5://."
    override val proxyErrNoHost = "Не удалось определить адрес хоста прокси."
    override val proxyErrNoIpOrDomain = "Не указан IP или домен прокси-сервера."
    override val proxyErrInvalidIpv6 = "Некорректный IPv6-адрес прокси."
    override val proxyErrInvalidPort = "Некорректный порт прокси."
    override val proxyErrInvalidHost = "Некорректный хост прокси-сервера."
    override val proxyErrPortRange = "Порт прокси должен быть в диапазоне 1..65535."

    override val llmErrClaudeStreamUnknown = "Неизвестная ошибка потока Claude"
    override val llmErrGoogleAiStudio = "Ошибка Google AI Studio."
    override fun llmErrProviderStreamUnknown(providerName: String) =
        "Неизвестная ошибка потока $providerName"
    override fun llmErrHttp401(provider: String) =
        "Неверный API-ключ для $provider (401). Проверьте ключ в «Настройки → Агент»."
    override fun llmErrHttp403(provider: String) =
        "Доступ запрещён (403) для $provider. Проверьте права ключа или доступ к выбранной модели."
    override fun llmErrHttp404(provider: String, details: String) =
        "Модель не найдена или недоступна для вашего аккаунта $provider (404).$details"
    override val llmErrHttp407 =
        "Прокси-сервер требует авторизацию (407). Проверьте логин и пароль в адресе прокси."
    override fun llmErrHttp429(provider: String, details: String) =
        "Превышен лимит запросов или квота $provider (429).$details"
    override fun llmErrHttp5xx(provider: String, code: Int) =
        "Сервер $provider временно недоступен ($code). Попробуйте позже."
    override fun llmErrHttpGeneric(provider: String, code: Int, details: String) =
        "Ошибка $provider (HTTP $code): $details"
    override fun llmErrUnknownHost(provider: String) =
        "Нет подключения к серверу $provider или хосту прокси. Проверьте интернет и настройки прокси."
    override fun llmErrTimeout(provider: String) =
        "Превышено время ожидания ответа от $provider. Проверьте сеть или прокси."
    override fun llmErrProxyConnect(provider: String) =
        "Ошибка прокси-сервера при подключении к $provider. Проверьте адрес, порт и логин/пароль прокси."
    override fun llmErrGenericConnect(provider: String, details: String) =
        "Ошибка соединения с $provider: $details"

    override val localLlmErrEmptyHistory = "Пустая история сообщений для локальной модели."
    override fun localLlmErrLoadFailed(details: String) =
        "Не удалось загрузить локальную модель Gemma 4: $details. Убедитесь, что на устройстве достаточно свободной ОЗУ."
    override fun localLlmErrSessionInit(details: String) =
        "Ошибка инициализации диалога локальной модели: $details"
    override fun localLlmErrGeneration(details: String) =
        "Ошибка генерации локальной модели: $details"
    override fun localModelErrLowStorage(neededGb: String, freeGb: String) =
        "Недостаточно памяти на устройстве: нужно ~$neededGb ГБ, свободно $freeGb ГБ."
    override fun localModelErrHttpStatus(code: Int) = "Сервер загрузки вернул HTTP $code"
    override val localModelErrEmptyFile = "Загруженный файл модели пуст."
    override val localModelErrIncomplete =
        "Загрузка прервалась не полностью. Нажмите «Скачать модель» для докачки."
    override val localModelErrDownloadFailed = "Не удалось скачать модель."
    override fun localModelErrBadRedirect(code: Int) =
        "Некорректный редирект (HTTP $code) без заголовка Location."
    override val localModelErrTooManyRedirects = "Слишком много перенаправлений при загрузке модели."

    override fun undoNoVisibleGitChanges(path: String) =
        "Нет видимых изменений в Git для файла $path."
    override fun undoRemotePushWarning(command: String) =
        "Удалённый push ($command) нельзя отменить локально; локальное состояние откачено."

    // Group discovery
    override fun groupDiscoveryGithubOwnerNotFound(owner: String) =
        "Профиль или организация GitHub «$owner» не найдены"
    override fun groupDiscoveryGithubReposFailed(owner: String) =
        "Не удалось получить список репозиториев GitHub для «$owner»"
    override fun groupDiscoveryGitlabGroupNotFound(path: String) =
        "Группа или пользователь GitLab «$path» не найдены"
    override fun groupDiscoveryAuth401(detail: String) =
        "Ошибка авторизации (401): включите авторизацию и укажите действительный токен (PAT)$detail"
    override fun groupDiscoveryForbidden403(detail: String) =
        "Доступ запрещён (403): проверьте права токена или лимит запросов API$detail"
    override fun groupDiscoveryServerError(code: Int, detail: String) =
        "Ошибка сервера ($code)$detail"
    override val groupDiscoveryEmptyUrl = "Укажите ссылку на группу или организацию"
    override fun groupDiscoveryInvalidUrl(url: String) = "Некорректная ссылка: $url"
    override val groupDiscoveryNoDomain = "Не удалось определить домен в ссылке"
    override val groupDiscoveryNoPath = "Укажите путь к группе или организации в ссылке"
    override val groupDiscoveryGithubMustBeOrg =
        "Для GitHub укажите ссылку на организацию или профиль (например, https://github.com/my-org), а не на конкретный репозиторий"
    override val groupDiscoveryGithubOwnerBlank =
        "Не удалось определить имя организации или пользователя GitHub"
    override val groupDiscoveryGitlabPathBlank = "Не удалось определить группу GitLab в ссылке"

    // JGitEngine, FileManager & ProjectRegistry
    override val jgitErrEmptyUrl = "URL репозитория не может быть пустым"
    override fun jgitErrTargetDirNotEmpty(name: String) =
        "Целевая папка уже существует и не пуста: $name"
    override val jgitProgressConnecting = "Подключение..."
    override val jgitProgressInitClone = "Инициализация клонирования..."
    override val jgitProgressDownloading = "Загрузка..."
    override fun jgitErrProjectDirExists(name: String) = "Папка проекта уже существует: $name"
    override val jgitReadmeCreatedLine = "Создано в PocketWorker."
    override val jgitErrRepoDirMissing = "Папка репозитория не найдена на диске"
    override val jgitErrReadGit = "Ошибка чтения Git"
    override val jgitErrEmptyCommitMsg = "Сообщение коммита не может быть пустым"
    override val jgitErrEmptyBranchName = "Имя ветки не может быть пустым"
    override val jgitErrNoCommitsForBranch =
        "В репозитории ещё нет ни одного коммита для создания ветки"
    override val jgitErrEmptyNewBranchName = "Новое имя ветки не может быть пустым"
    override fun jgitErrBranchNotFound(name: String) = "Ветка не найдена: $name"
    override val jgitErrEmptyRemoteName = "Имя remote не может быть пустым"
    override val jgitErrEmptyRemoteUrl = "URL remote не может быть пустым"
    override fun jgitFetchUpToDate(remote: String) = "Fetch ($remote): всё актуально"
    override fun jgitFetchUpdatedRefs(remote: String, count: Int) =
        "Fetch ($remote): обновлено ссылок — $count"
    override val jgitPullDone = "Pull выполнен"
    override val jgitPullFailed = "Pull не выполнен"
    override fun jgitUpdatedRefsSummary(count: Int) = "Обновлено ссылок: $count"
    override val jgitErrNothingToPush = "Нечего отправлять: сделайте хотя бы один коммит"
    override val jgitErrNoCurrentBranch = "Не удалось определить текущую ветку"
    override val jgitErrStashNoCommits = "Нельзя создать stash в репозитории без коммитов"
    override val jgitErrEmptyTagName = "Имя тега не может быть пустым"
    override val jgitErrTagNoCommits = "Нельзя создать тег в репозитории без коммитов"
    override val jgitTagsTargetWord = "теги"
    override fun jgitErrPush(details: String) = "Ошибка Push: $details"
    override fun jgitPushFallbackOk(target: String, remote: String) =
        "Push ($target) в $remote выполнен"
    override fun jgitPushSummary(remote: String, details: String) = "Push в $remote: $details"
    override fun jgitErrCommitNotFound(id: String) = "Коммит не найден: $id"
    override fun jgitBinaryFileSummary(bytes: Int) = "Бинарный файл ($bytes байт)"

    override fun fmErrOutsideProject(path: String) = "Путь выходит за пределы проекта: $path"
    override fun fmErrFileNotFound(path: String) = "Файл не найден: $path"
    override fun fmErrWriteToDirectory(path: String) = "Нельзя записать текст в директорию: $path"
    override fun fmErrAlreadyExists(path: String) = "Файл или папка уже существует: $path"
    override fun fmErrCreateDirFailed(path: String) = "Не удалось создать папку: $path"
    override fun fmErrInvalidName(name: String) = "Некорректное имя: $name"
    override fun fmErrEntryNotFound(path: String) = "Элемент не найден: $path"
    override fun fmErrTargetExists(name: String) = "Элемент с именем '$name' уже существует"
    override fun fmErrRenameFailed(path: String) = "Не удалось переименовать '$path'"
    override val fmErrDeleteRoot = "Нельзя удалить корень проекта через файловый менеджер"
    override fun fmErrDeleteFailed(path: String) = "Не удалось удалить: $path"

    override fun registryErrCreateDirFailed(path: String) =
        "Не удалось создать папку проекта: $path"
    override fun registryErrProjectNotFound(id: String) = "Проект не найден: $id"

    // MCP user-facing confirmations & hints
    override fun mcpConfirmGenericTool(toolName: String) = "Выполнить инструмент $toolName"
    override fun mcpConfirmRun(toolName: String) = "Выполнить $toolName"
    override val mcpHintConfirmationRequired =
        "Для этого действия необходимо явное подтверждение пользователя."
    override val mcpHintCancelledByUser = "Операция отменена пользователем."
    override fun mcpHintInternalError(toolName: String, details: String) =
        "Внутренняя ошибка при выполнении '$toolName': $details"
    override fun mcpHintRepoFolderDeleted(name: String) =
        "Папка репозитория '$name' была удалена с устройства."
    override fun mcpConfirmCreateProject(name: String) = "Создать новый проект «$name»"
    override fun mcpConfirmCreateRepo(name: String) = "Создать репозиторий «$name»"
    override val mcpHintUseHttpsNotSsh = "Укажите HTTPS-ссылку репозитория (https://...) вместо SSH."
    override fun mcpConfirmCloneRepo(url: String) = "Клонировать репозиторий $url"
    override val mcpHintUseHttpsClone =
        "Для клонирования используйте HTTPS-ссылку (https://...) и при необходимости добавьте PAT в настройках."
    override val mcpHintAddPatInSettings =
        "Добавьте или обновите Personal Access Token в «Настройки → Общие» для репозитория, проекта или по умолчанию."
    override val mcpHintCheckPatScopes =
        "Проверьте права доступа (scopes) вашего Personal Access Token к этому репозиторию."
    override val mcpHintCheckNetwork = "Проверьте подключение к интернету и доступность сервера."
    override val mcpHintBinaryNotEditable = "Бинарные файлы не редактируются текстовым агентом."
    override fun mcpConfirmEditFile(path: String) = "Изменить файл $path"
    override fun mcpConfirmWriteFile(path: String) = "Записать файл $path"
    override fun mcpConfirmReplaceAll(query: String, replacement: String) =
        "Массовая замена «$query» → «$replacement»"
    override fun mcpConfirmFsBatch(count: Int) = "Изменить структуру папок/файлов ($count опер.)"
    override fun mcpConfirmDeletePath(path: String) = "Удалить «$path»"
    override val mcpHintGitHooksUnavailable =
        "Git hooks недоступны на телефоне. Проверки проекта можно выполнить на ПК или в CI."
    override fun mcpHintPushRejected(details: String) = "Удалённый сервер отклонил push: $details"
    override val mcpHintSshUnsupported =
        "SSH-ключи не поддерживаются в PocketWorker. Используйте HTTPS URL репозитория и Personal Access Token."
    override fun mcpConfirmGitForcePush(cmd: String) =
        "ОПАСНО: выполнить force push на удалённый сервер: $cmd"
    override fun mcpConfirmGitPush(cmd: String) = "Отправить изменения на удалённый сервер: $cmd"
    override fun mcpConfirmGitResetHard(cmd: String) =
        "ОПАСНО: выполнить жёсткий сброс (локальные изменения будут потеряны): $cmd"
    override fun mcpConfirmGitClean(cmd: String) = "ОПАСНО: удалить неотслеживаемые файлы: $cmd"
    override fun mcpConfirmGitRestore(cmd: String) =
        "ОПАСНО: откатить незакоммиченные изменения файлов: $cmd"
    override fun mcpConfirmGitDeleteBranch(cmd: String) = "Удалить ветку: $cmd"
    override fun mcpConfirmGitStashDelete(cmd: String) = "ОПАСНО: удалить сохранённый stash: $cmd"
    override fun mcpConfirmGitCommand(cmd: String) = "Выполнить Git-команду: $cmd"

    // ── Git OAuth (GitHub / GitLab / Self-hosted) ───────────────────────────────────────────
    override val oauthAccountsTitle = "Аккаунты (OAuth)"
    override val oauthAccountsEmptyHint =
        "Войдите через GitHub или GitLab — токен подберётся автоматически по адресу репозитория."
    override val oauthSignInGitHub = "Войти через GitHub"
    override val oauthSignInGitLab = "Войти через GitLab"
    override val oauthDefaultForHostBadge = "По умолчанию"
    override val oauthMakeDefaultForHost = "Сделать основным для хоста"
    override val oauthDisconnectAccountDesc = "Отключить аккаунт"
    override fun oauthSignedInInfo(label: String) = "Вход выполнен: $label"
    override fun oauthSignedInWithAuthorInfo(label: String) =
        "Вход выполнен: $label (автор коммитов заполнен)"
    override fun oauthAccountRemovedInfo(label: String) = "Аккаунт отключён: $label"
    override fun oauthHostDefaultUpdatedInfo(label: String) = "Основной аккаунт обновлён: $label"
    override val oauthProjectAccountBoundInfo = "Аккаунт привязан к проекту"
    override val oauthRepoAccountBoundInfo = "Аккаунт привязан к репозиторию"

    override val oauthGitHubDialogTitle = "Вход через GitHub"
    override val oauthGitHubDialogSubtitle = "Авторизация по коду устройства (Device Flow)"
    override val oauthGitHubClientIdLabel = "GitHub OAuth Client ID"
    override val oauthGitHubClientIdHint =
        "Укажите Client ID вашего GitHub OAuth App (с включённой опцией Enable Device Flow)."
    override val oauthGitHubStartButton = "Получить код входа"
    override val oauthGitHubCodeLabel = "Одноразовый код для github.com/login/device"
    override val oauthGitHubCopyAndOpenButton = "Скопировать код и открыть GitHub"
    override val oauthGitHubCodeCopiedInfo = "Код скопирован в буфер обмена"
    override val oauthGitHubWaitingHint = "Ожидаем подтверждения в браузере…"
    override val oauthGitHubChangeClientId = "Изменить Client ID"

    override val oauthGitLabDialogTitle = "Вход через GitLab"
    override val oauthGitLabDialogSubtitle = "OAuth PKCE с автообновлением токена"
    override val oauthGitLabModeCloud = "gitlab.com"
    override val oauthGitLabModeSelfHosted = "Self-hosted"
    override val oauthGitLabServerUrlLabel = "URL сервера GitLab"
    override val oauthGitLabServerUrlPlaceholder = "https://gitlab.company.com"
    override val oauthGitLabClientIdLabel = "Application ID (Client ID)"
    override val oauthGitLabSetupHint =
        "В настройках GitLab Application снимите галочку Confidential и включите права: read_user, write_repository, read_api."
    override val oauthGitLabRedirectUriLabel = "Redirect URI"
    override val oauthGitLabCopyRedirectUriDesc = "Скопировать Redirect URI"
    override val oauthGitLabRedirectUriCopiedInfo = "Redirect URI скопирован"
    override val oauthGitLabOpenBrowserButton = "Открыть GitLab в браузере"
    override val oauthGitLabWaitingHint =
        "Завершите вход в браузере — вы автоматически вернётесь в приложение."
    override val oauthGitLabExchangingHint = "Завершаем авторизацию и загружаем профиль…"

    override val oauthScopedModeAccount = "Аккаунт"
    override val oauthScopedModeManual = "Вручную (PAT)"
    override val oauthScopedNoAccountsHint =
        "Пока нет подключённых OAuth-аккаунтов. Войдите через GitHub или GitLab ниже."
    override val oauthScopedAddAccountTitle = "Подключить новый аккаунт"
    override val oauthScopedClearOverride = "Сбросить"
    override val oauthCloneUsingConnectedAccountsHint =
        "Оставьте поле токена пустым, чтобы автоматически использовать подключённый OAuth-аккаунт для этого сервера."

    override val oauthErrClientIdRequired = "Укажите Client ID (Application ID)."
    override val oauthErrInvalidServerUrl = "Введите корректный адрес сервера (например, https://gitlab.company.com)."
    override val oauthErrBrowserUnavailable = "Не удалось открыть браузер на устройстве."
    override val oauthErrDeviceExpired = "Срок действия кода истёк. Запросите новый код."
    override val oauthErrAccessDenied = "Авторизация отменена пользователем."
    override val oauthErrStateMismatch = "Несовпадение параметра безопасности state. Повторите вход."
    override val oauthErrMissingCode = "Сервер не вернул код авторизации."
    override fun oauthErrRequestFailed(details: String) = "Ошибка OAuth: $details"
    override fun oauthErrProfileFailed(details: String) = "Не удалось получить профиль: $details"
}
