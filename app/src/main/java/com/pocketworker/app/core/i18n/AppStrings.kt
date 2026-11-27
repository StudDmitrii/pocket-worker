package com.pocketworker.app.core.i18n

import com.pocketworker.app.core.storage.AiProvider

/**
 * Centralized, type-safe catalog of all user-facing strings in PocketWorker.
 * Every language implementation ([StringsRu], [StringsEn]) must implement all keys.
 */
interface AppStrings {
    // ── Common ──────────────────────────────────────────────────────────────────────────────
    val commonBack: String
    val commonClose: String
    val commonCancel: String
    val commonSave: String
    val commonCreate: String
    val commonDelete: String
    val commonRename: String
    val commonEdit: String
    val commonReset: String
    val commonRefresh: String
    val commonSettings: String
    val commonActions: String
    val commonClear: String
    val commonCopy: String
    val commonCopied: String
    val commonNewBadge: String
    val commonUsername: String
    val commonEncryptedInKeystore: String
    val commonShowHideToken: String
    val commonHideError: String
    val commonNotSet: String
    val commonDone: String
    val commonNotDone: String
    val commonError: String
    val commonConfirmation: String
    val commonAllow: String
    val commonStop: String
    val commonSend: String
    val commonRetry: String
    val commonSkip: String
    val commonNext: String
    val commonStartWorking: String
    val commonDismissNotification: String

    // File sizes & transport errors
    fun formatBytes(bytes: Long): String
    fun formatKilobytes(kb: Long): String
    fun formatMegabytes(mbFormatted: String): String
    fun gitTransportAuthError(raw: String): String
    fun gitTransportForbiddenError(raw: String): String
    fun gitTransportNotFoundError(raw: String): String
    fun gitTransportConnectionError(raw: String): String

    // ── Home (Projects) ─────────────────────────────────────────────────────────────────────
    val homeTitle: String
    val homeWorkspaceFolder: String
    fun homeAllProjectsCount(count: Int): String
    val homeRefreshList: String
    val homeNewProject: String
    val homeEditProject: String
    fun homeDeleteProjectTitle(name: String): String
    fun homeDeleteProjectMessage(repoCount: Int): String
    val homeEmptyTitle: String
    val homeEmptySubtitle: String
    val homeCreateFirstProject: String
    val homeProjectMenu: String
    val homeRenameOrDescription: String
    val homeDeleteProjectAction: String
    fun homeDirtyReposBadge(count: Int): String
    fun homeEmptyProjectHint(formattedTime: String): String
    val homeProjectCreateSubtitle: String
    val homeProjectEditSubtitle: String
    val homeProjectNameLabel: String
    val homeProjectNamePlaceholder: String
    val homeProjectDescLabel: String
    val homeProjectDescPlaceholder: String
    val homeOpenProjectImmediately: String
    val homeOpenProjectImmediatelyHint: String
    fun formatRepoCount(count: Int): String
    val homeErrEnterProjectName: String
    fun homeInfoProjectCreated(name: String): String
    val homeErrCreateProject: String
    val homeErrEmptyProjectName: String
    val homeInfoProjectUpdated: String
    val homeInfoProjectDeleted: String
    val homeInfoDefaultTokenCleared: String
    val homeInfoDefaultTokenSaved: String

    // ── Project Detail (Repositories) ───────────────────────────────────────────────────────
    val projectBackToProjects: String
    val projectFallbackTitle: String
    val projectFallbackInLocation: String
    fun projectRepositoriesCount(count: Int): String
    val projectAddRepo: String
    fun projectDeleteRepoTitle(name: String): String
    val projectDeleteRepoMessage: String
    fun projectEmptyTitle(projectName: String): String
    val projectEmptySubtitle: String
    val projectDeleteRepoDesc: String
    val projectRepoStatusNoCommits: String
    val projectRepoStatusClean: String
    fun projectRepoStatusChanges(count: Int): String
    val projectRepoNoGit: String
    val projectErrNotFound: String
    val projectErrLoadFailed: String
    val projectErrEnterRepoUrl: String
    val projectCloneConnecting: String
    fun projectInfoRepoAdded(name: String): String
    val projectCloneCancelled: String
    val projectErrEnterGroupUrl: String
    val projectErrGroupNoRepos: String
    val projectErrSelectAtLeastOneRepo: String
    fun projectGroupConnecting(repoName: String): String
    fun projectGroupCancelled(succeeded: Int, total: Int): String
    fun projectGroupPartialErrorsHeader(succeeded: Int, total: Int): String
    fun projectGroupMoreErrors(remaining: Int): String
    fun projectGroupAddedCount(count: Int): String
    val projectErrEnterRepoName: String
    fun projectInfoRepoCreated(name: String): String
    val projectErrCreateRepo: String
    val projectInfoRepoDeleted: String
    val projectInfoProjectTokenCleared: String
    val projectInfoProjectTokenSaved: String

    // Repo Dialogs
    val repoAddSheetSubtitle: String
    val repoConnectTitle: String
    val repoConnectSubtitle: String
    val repoAddGroupTitle: String
    val repoAddGroupSubtitle: String
    val repoCreateTitle: String
    val repoCreateSubtitle: String
    val repoCloneDialogSubtitle: String
    val repoCloneAction: String
    val repoUrlLabel: String
    val repoFolderNameLabel: String
    val repoFolderNamePlaceholder: String
    val repoBranchOptionalLabel: String
    val repoBranchOptionalPlaceholder: String
    val repoSaveTokenForRepo: String
    val repoCreateDialogSubtitle: String
    val repoNameLabel: String
    val repoInitGitTitle: String
    val repoInitGitSubtitle: String
    val repoCreateReadmeTitle: String
    val repoCreateReadmeSubtitle: String
    val repoInitialBranchLabel: String
    val repoRemoteUrlOptionalLabel: String
    val repoAuthorNameLabel: String
    val repoAuthorEmailLabel: String
    val repoAuthTitle: String
    val repoAuthSubtitle: String
    val repoAuthUserLabel: String
    val repoAuthTokenLabel: String
    val repoGroupDialogSubtitle: String
    val repoGroupUrlLabel: String
    val repoGroupUrlPlaceholder: String
    val repoGroupSaveTokenLabel: String
    val repoGroupFindRepos: String
    val repoGroupRefreshList: String
    val repoGroupSearching: String
    fun repoGroupFoundSelected(found: Int, selected: Int): String
    val repoGroupUnselectAll: String
    val repoGroupSelectAll: String
    val repoGroupPrivateDesc: String
    val repoGroupAlreadyAdded: String
    fun repoGroupDownloading(current: Int, total: Int, repoName: String): String
    fun repoGroupDownloadSelected(count: Int): String

    // ── Workspace (Tabs, Files, Editor, History) ────────────────────────────────────────────
    val workspaceBackToProjectRepos: String
    val workspaceRepoFallbackTitle: String
    val workspaceTabFiles: String
    val workspaceTabHistory: String
    val workspaceErrRepoNotFound: String
    val workspaceErrLoadRepo: String
    val workspaceErrOpenFile: String
    fun workspaceInfoFileDeletedExternally(path: String): String
    fun workspaceInfoFileSaved(path: String): String
    val workspaceErrSaveFile: String
    val workspaceBusyCreateFile: String
    val workspaceBusyCreateFolder: String
    val workspaceBusyRename: String
    val workspaceBusyDelete: String
    val workspaceBusyInitGit: String
    val workspaceInfoGitInitialized: String
    val workspaceBusyStageFile: String
    val workspaceBusyStageAll: String
    val workspaceBusyUnstageFile: String
    val workspaceBusyUnstageAll: String
    val workspaceBusyDiscardFile: String
    fun workspaceInfoDiscardFileDone(path: String): String
    val workspaceBusyDiscardAll: String
    val workspaceInfoDiscardAllDone: String
    val workspaceBusyCommitAndPush: String
    val workspaceBusyCommit: String
    fun workspaceInfoCommitAndPushDone(shortId: String, pushMsg: String): String
    fun workspaceInfoCommitDone(shortId: String, message: String): String
    val workspaceBusyCreateBranch: String
    fun workspaceInfoBranchCreatedAndCheckedOut(name: String): String
    fun workspaceInfoBranchCreated(name: String): String
    val workspaceBusyCheckoutBranch: String
    val workspaceInfoBranchSwitched: String
    val workspaceBusyRenameBranch: String
    fun workspaceInfoBranchRenamed(oldName: String, newName: String): String
    val workspaceBusyDeleteBranch: String
    fun workspaceInfoBranchDeleted(name: String): String
    fun workspaceBusyMergeBranch(source: String): String
    fun workspaceMergeConflictsNote(conflicts: String): String
    fun workspaceMergeFailedStatus(status: String, conflictNote: String): String
    val workspaceBusyAddRemote: String
    fun workspaceInfoRemoteAdded(name: String): String
    val workspaceBusyUpdateRemote: String
    fun workspaceInfoRemoteUpdated(name: String): String
    val workspaceBusyRemoveRemote: String
    fun workspaceInfoRemoteRemoved(name: String): String
    fun workspaceBusyFetch(remote: String): String
    fun workspaceBusyPullRebase(remote: String): String
    fun workspaceBusyPull(remote: String): String
    fun workspaceBusyForcePush(remote: String): String
    fun workspaceBusyPush(remote: String): String
    fun workspaceBusySync(remote: String): String
    val workspaceBusyStashCreate: String
    val workspaceInfoStashEmpty: String
    fun workspaceInfoStashCreated(message: String): String
    fun workspaceBusyStashApply(index: Int): String
    fun workspaceInfoStashApplied(index: Int): String
    fun workspaceBusyStashPop(index: Int): String
    fun workspaceInfoStashPopped(index: Int): String
    fun workspaceBusyStashDrop(index: Int): String
    fun workspaceInfoStashDropped(index: Int): String
    val workspaceBusyStashClear: String
    val workspaceInfoStashCleared: String
    fun workspaceBusyCreateTag(name: String): String
    fun workspaceInfoTagCreated(name: String): String
    fun workspaceBusyDeleteTag(name: String): String
    fun workspaceInfoTagDeleted(name: String): String
    fun workspaceBusyPushTags(remote: String): String
    val workspaceInfoRepoTokenCleared: String
    val workspaceInfoRepoTokenSaved: String
    val workspaceBusySaveAuthor: String
    val workspaceInfoAuthorUpdated: String
    val workspaceDiffEmpty: String
    val workspaceErrDiffBuild: String
    val workspaceBusyLoadHistory: String
    val workspaceErrLoadHistory: String
    val workspaceErrLoadCommit: String
    val workspaceErrDiff: String

    // Files tab
    val filesNavigateUp: String
    val filesSearchDesc: String
    val filesToggleGitDirDesc: String
    val filesNewFile: String
    val filesNewFolder: String
    val filesSearchPlaceholder: String
    val filesNothingFound: String
    val filesFolderEmpty: String
    val filesNewFileNameLabel: String
    val filesNewFolderNameLabel: String
    val filesNewNameLabel: String
    fun filesDeleteTitle(name: String): String
    val filesDeleteFolderConfirm: String
    fun filesDeleteFileConfirm(path: String): String
    val filesToolsDesc: String
    val filesSyntaxCheckTitle: String
    fun filesSyntaxCheckScopeSubtitle(scope: String): String
    val filesSyntaxCheckSingleFile: String
    val filesSyntaxChecking: String
    val filesSyntaxNoErrorsTitle: String
    fun filesSyntaxCheckedSummary(checked: Int, skipped: Int): String
    fun filesSyntaxIssuesSummary(errors: Int, warnings: Int, filesWithIssues: Int): String
    val filesSyntaxNoFilesToCheck: String
    val filesSyntaxOpenFileHint: String
    val filesSyntaxCheckAgain: String
    fun filesSyntaxLineCol(line: Int, col: Int): String
    val filesSyntaxErrorBadge: String
    val filesSyntaxWarningBadge: String

    // File editor
    val editorModifiedSuffix: String
    val editorViewModeDesc: String
    val editorEditModeDesc: String
    fun editorBinaryUnsupported(size: String): String
    fun editorTooLarge(size: String): String
    fun editorZoomReset(percent: String): String
    val editorUnsavedTitle: String
    fun editorUnsavedMessage(path: String): String
    val editorExitWithoutSaving: String

    // History tab
    val historyNoGit: String
    val historyBranchLabel: String
    fun historyBranchCurrent(name: String): String
    val historyEmptyTitle: String
    val historyEmptySubtitle: String
    val historyLoadOlder: String

    // ── Git Control Tab, Action Sheet & Dialogs ─────────────────────────────────────────────
    val gitNotInitializedTitle: String
    val gitNotInitializedSubtitle: String
    val gitInitButton: String
    val gitSelectBranchDesc: String
    val gitMenuDesc: String
    val gitCommitMessagePlaceholder: String
    val gitAmendBanner: String
    val gitDisableAmend: String
    val gitCommitOptionsDesc: String
    val gitCommitStaged: String
    val gitCommitAmend: String
    val gitStagedSectionTitle: String
    val gitUnstageAllDesc: String
    val gitUnstageDesc: String
    val gitChangesSectionTitle: String
    val gitDiscardAllDesc: String
    val gitStageAllDesc: String
    val gitWorkingTreeClean: String
    val gitNoUnstagedChanges: String
    val gitStageDesc: String
    val gitStashSectionTitle: String
    val gitDeleteStashDesc: String
    val gitDiscardFileTitle: String
    fun gitDiscardFileMessage(path: String): String
    val gitDiscardAction: String
    val gitDiscardAllTitle: String
    val gitDiscardAllMessage: String
    val gitDiscardAllAction: String
    val gitForcePushTitle: String
    fun gitForcePushMessage(remote: String): String
    fun gitRemoveRemoteTitle(name: String): String
    fun gitRemoveRemoteMessage(url: String): String
    fun gitDropStashTitle(index: Int): String
    fun gitDropStashMessage(message: String): String
    val gitClearStashesTitle: String
    fun gitClearStashesMessage(count: Int): String
    fun gitDeleteTagTitle(name: String): String
    val gitDeleteTagMessage: String

    // Git Action Sheet
    val gitSubmenuRoot: String
    val gitSubmenuCommit: String
    val gitSubmenuChanges: String
    val gitSubmenuPullPush: String
    val gitSubmenuRemote: String
    val gitSubmenuStash: String
    val gitSubmenuTags: String
    fun gitBranchPrefix(branch: String): String
    val gitViewAsTreeTitle: String
    val gitViewAsTreeSubtitleOn: String
    val gitViewAsTreeSubtitleOff: String
    val gitMenuRemoteSubtitle: String
    val gitMenuStashSubtitle: String
    val gitMenuTagsSubtitle: String
    val gitMenuOutputSubtitle: String
    fun gitCommitStagedSubtitleReady(count: Int): String
    val gitCommitStagedSubtitleHint: String
    val gitCommitAllSubtitle: String
    fun gitCommitPushSubtitleReady(remote: String): String
    val gitNoRemoteConnected: String
    val gitAmendToggleOn: String
    val gitAmendToggleOff: String
    val gitAmendToggleSubtitle: String
    val gitOnBadge: String
    fun gitStageAllSubtitle(count: Int): String
    fun gitUnstageAllSubtitle(count: Int): String
    val gitDiscardAllSubtitle: String
    val gitSyncSubtitle: String
    fun gitPullSubtitle(remote: String): String
    val gitPullRebaseSubtitle: String
    val gitPullFromSubtitle: String
    fun gitPushSubtitle(remote: String): String
    val gitPushToSubtitle: String
    val gitForcePushSubtitle: String
    val gitFetchSubtitle: String
    val gitAddRemoteTitle: String
    val gitAddRemoteSubtitle: String
    val gitNoRemotesConfigured: String
    val gitStashIncludeUntrackedTitle: String
    val gitStashIncludeUntrackedSubtitle: String
    val gitStashTrackedOnlyTitle: String
    val gitStashTrackedOnlySubtitle: String
    val gitStashPopLatestTitle: String
    val gitStashPopLatestSubtitle: String
    val gitStashApplyLatestTitle: String
    val gitStashApplyLatestSubtitle: String
    val gitStashClearAllTitle: String
    fun gitStashClearAllSubtitle(count: Int): String
    fun gitStashSavedHeader(count: Int): String
    val gitCreateTagTitle: String
    fun gitCreateTagSubtitle(branch: String): String
    val gitPushTagsTitle: String
    fun gitPushTagsSubtitle(remote: String): String
    val gitNoTagsYet: String
    fun gitLocalTagsHeader(count: Int): String
    val gitDeleteTagDesc: String

    // Git Dialogs
    val gitCopyShaDesc: String
    fun gitCommitParents(parents: String): String
    val gitCreateBranchFromCommit: String
    fun gitChangedFilesHeader(count: Int): String
    fun gitRenamedFrom(oldPath: String): String
    val gitNewBranchTitle: String
    fun gitNewBranchBase(base: String): String
    val gitBranchNameLabel: String
    val gitCheckoutImmediately: String
    val gitRenameBranchTitle: String
    fun gitRenameBranchCurrent(name: String): String
    val gitNewBranchNameLabel: String
    val gitEditRemoteTitle: String
    val gitRemoteDialogSubtitle: String
    val gitRemoteNameLabel: String
    val gitRepoAuthTitle: String
    val gitRepoAuthSubtitle: String
    val gitRepoAuthFallbackHint: String
    val gitRepoAuthDirectHint: String
    val gitAuthorTitle: String
    val gitAuthorRepoSubtitle: String
    val gitAuthorSaveGlobally: String
    val gitStashDialogTitle: String
    val gitStashDialogSubtitle: String
    val gitStashAction: String
    val gitStashMessageLabel: String
    val gitStashMessagePlaceholder: String
    val gitStashIncludeUntrackedOption: String
    val gitTagDialogTitle: String
    val gitTagDialogSubtitle: String
    val gitTagNameLabel: String
    val gitTagAnnotationLabel: String
    val gitTagAnnotationPlaceholder: String
    val gitSwitchBranchTitle: String
    val gitNewBranchShort: String
    val gitSearchBranchPlaceholder: String
    val gitLocalBranches: String
    fun gitRenameBranchDesc(name: String): String
    val gitRemoteBranches: String
    val gitUnbornBranchHint: String
    val gitNoMatchingBranches: String
    val gitCopyLogDesc: String
    val gitClearLogDesc: String
    val gitOutputEmptyHint: String

    // ── Settings ────────────────────────────────────────────────────────────────────────────
    val settingsTitle: String
    val settingsTabGeneral: String
    val settingsTabAgent: String
    val settingsSubtitleAgent: String
    fun settingsSubtitleRepo(name: String): String
    fun settingsSubtitleProject(name: String): String
    val settingsSubtitleGeneral: String
    val settingsAppearanceTitle: String
    val settingsThemeTitle: String
    val settingsThemeAuto: String
    val settingsThemeLight: String
    val settingsThemeDark: String
    val settingsLanguageTitle: String
    val settingsLanguageSystem: String
    val settingsLanguageRussian: String
    val settingsLanguageEnglish: String
    val settingsAnimatedBgTitle: String
    val settingsAnimatedBgSubtitle: String
    val settingsGitAndAuthTitle: String
    val settingsScopeDefault: String
    val settingsDefaultTokenTitle: String
    val settingsDefaultTokenDialogTitle: String
    val settingsDefaultTokenDialogDesc: String
    fun settingsTokenSavedInKeystore(user: String): String
    val settingsDefaultAuthorTitle: String
    fun settingsScopeProject(name: String): String
    val settingsProjectTokenTitle: String
    fun settingsProjectTokenDialogDesc(name: String): String
    fun settingsTokenSet(user: String): String
    val settingsProjectAuthorTitle: String
    fun settingsScopeRepo(name: String): String
    val settingsRepoTokenTitle: String
    val settingsRepoAuthorTitle: String
    val settingsAboutTitle: String
    val settingsVersionSubtitle: String
    val settingsGuideTitle: String
    val settingsGuideSubtitle: String
    val settingsGlobalAuthorSubtitle: String
    fun settingsProjectAuthorSubtitle(name: String): String

    // Agent Settings
    val settingsModelAndConnectionTitle: String
    val settingsProviderLabel: String
    val settingsProviderLocalDownloadedDesc: String
    val settingsProviderKeySetDesc: String
    val settingsModelLabel: String
    val settingsDownloadedDesc: String
    val settingsSelectedDesc: String
    val settingsGeminiDirectHint: String
    val settingsOpenRouterHint: String
    val settingsHideKeyDesc: String
    val settingsShowKeyDesc: String
    val settingsProxyTitle: String
    val settingsProxyLocalSubtitle: String
    val settingsProxyCloudSubtitle: String
    val settingsProxyAddressLabel: String
    val settingsProxyFormatHint: String
    val settingsHideProxyDesc: String
    val settingsShowProxyDesc: String
    val settingsAgentBehaviorTitle: String
    val settingsSystemPromptLabel: String
    val settingsSystemPromptPlaceholder: String
    val settingsShareContextTitle: String
    val settingsShareContextSubtitle: String
    val settingsPermissionsTitle: String
    val settingsPermissionsAsk: String
    val settingsPermissionsAuto: String
    val settingsPermissionsSubtitle: String
    val settingsSaveAgentButton: String

    // Local Model Download UI
    fun settingsLocalDownloading(label: String): String
    val settingsLocalCancelDownload: String
    val settingsLocalReadyTitle: String
    fun settingsLocalReadySubtitle(size: String): String
    val settingsLocalDeleteButton: String
    val settingsLocalOfflineInfo: String
    fun settingsLocalResumeHint(downloaded: String, total: String): String
    val settingsLocalContinueDownload: String
    val settingsLocalDownloadButton: String
    fun settingsLocalRamLowHint(totalGb: String, availGb: String): String
    fun settingsLocalRamNormalHint(totalGb: String, availGb: String): String
    val settingsLocalGpuTitle: String
    val settingsLocalGpuSubtitle: String
    val settingsLocalDeleteDialogTitle: String
    fun settingsLocalDeleteDialogBody(label: String, size: String): String

    // Settings ViewModel messages
    val settingsInfoProjectAuthorReset: String
    val settingsInfoProjectAuthorSaved: String
    val settingsInfoRepoAuthorUpdated: String
    val settingsInfoRepoAuthorReset: String
    val settingsInfoGlobalAuthorSaved: String
    val settingsInfoGlobalAuthorReset: String
    val settingsInfoLocalModelDeleted: String
    fun settingsInfoLocalModelActive(label: String): String
    val settingsInfoAgentSavedDownloadLocal: String
    val settingsInfoAgentSaved: String

    // ── AI Chat, Context, Markdown, Floating Button & Providers ─────────────────────────────
    val aiChatDrawerDesc: String
    val aiScrollToBottomDesc: String
    val aiScrollToBottomLabel: String
    val aiChatHistoryTitle: String
    val aiRenameChatTitle: String
    val aiRenameChatLabel: String
    val aiDeleteChatDesc: String
    val aiChatHistoryLocalHint: String
    val aiNewChat: String
    val aiDeleteDialogTitle: String
    fun aiDeleteDialogBody(title: String): String
    fun aiAttachedFilesLine(names: String): String
    val aiRetrySubtitle: String
    val aiCopyResponseDesc: String
    fun aiActionsGroupTitle(count: Int): String
    val aiCollapseActionsDesc: String
    val aiExpandActionsDesc: String
    fun aiChangedFilesMore(names: String, remaining: Int): String
    fun aiChangedFilesGroupTitle(count: Int): String
    val aiCollapseFilesDesc: String
    val aiExpandFilesDesc: String
    val aiOpenDiffDesc: String
    val aiThinking: String
    val aiCollapseDetailsDesc: String
    val aiExpandDetailsDesc: String
    fun aiToolLabel(toolName: String): String
    fun aiParamsLabel(preview: String): String
    fun aiResultLabel(preview: String): String
    val aiOpenAgentSettings: String
    val aiConfirmDangerousTitle: String
    val aiConfirmNormalTitle: String
    val aiAttachFileDesc: String
    val aiInputPlaceholderBusy: String
    val aiInputPlaceholderIdle: String
    val aiRemoveAttachmentDesc: String
    fun aiAttachedFileHeader(name: String): String
    fun aiAttachedBinaryHeader(name: String, mime: String): String

    // AI ViewModel statuses & errors
    val aiRollingBack: String
    val aiReturnedToInput: String
    fun aiRollbackCountSuffix(count: Int): String
    fun aiNoSavedDiff(path: String): String
    fun aiAttachedFilesSummary(count: Int): String
    fun aiProviderNotConnected(providerName: String): String
    fun aiLocalModelNotDownloaded(modelLabel: String): String
    fun aiMissingApiKey(providerName: String): String
    fun aiInvalidProxy(reason: String): String
    val aiLoadingLocalModel: String
    val aiUnexpectedError: String
    val aiAnalyzingResult: String
    val aiResponding: String
    val aiWebSearchRunning: String
    val aiWebSearchDone: String
    fun aiWaitingConfirmationStatus(summary: String): String
    val aiWaitingConfirmationTitle: String
    val aiRejectedByUser: String
    fun aiToolErrorTitle(code: String): String
    fun aiMaxStepsReached(maxSteps: Int): String
    fun aiToolStatus(toolName: String): Pair<String, String>
    fun aiSyntaxFilesCount(count: Int): String
    val aiSyntaxChangedFiles: String

    // AI Context (greeting, empty state prompt, subtitle)
    val aiGreetingAllProjects: String
    fun aiGreetingProjectNamed(name: String): String
    val aiGreetingProjectUnnamed: String
    fun aiGreetingRepoSubdirNamed(dir: String, repo: String): String
    fun aiGreetingRepoSubdirUnnamed(dir: String): String
    fun aiGreetingRepoRootNamed(repo: String): String
    val aiGreetingRepoRootUnnamed: String
    fun aiGreetingFileWithRepo(file: String, repo: String): String
    fun aiGreetingFileWithoutRepo(file: String): String

    val aiEmptyPromptAllProjects: String
    fun aiEmptyPromptProjectNamed(name: String): String
    val aiEmptyPromptProjectUnnamed: String
    fun aiEmptyPromptRepoSubdirNamed(dir: String, repo: String): String
    fun aiEmptyPromptRepoNamed(repo: String): String
    fun aiEmptyPromptDirOnly(dir: String): String
    val aiEmptyPromptRepoUnnamed: String
    fun aiEmptyPromptFileNamed(fileName: String): String
    val aiEmptyPromptFileUnnamed: String

    val aiSubtitleAllProjects: String
    fun aiSubtitleProject(name: String): String
    val aiSubtitleProjectFolder: String
    fun aiSubtitleRepo(name: String): String
    val aiSubtitleRepoUnnamed: String
    val aiSubtitleFile: String

    // Markdown & Floating AI Button
    val mdCalloutNote: String
    val mdCalloutTip: String
    val mdCalloutImportant: String
    val mdCalloutWarning: String
    val mdCalloutCaution: String
    val mdCodeDefaultLabel: String
    val mdCopyCodeDesc: String
    val fabRevealAiDesc: String
    val fabOpenAiDesc: String
    val fabRevealFoldersDesc: String
    val fabReturnToFoldersDesc: String

    // AI Providers & Models
    fun providerDisplayName(provider: AiProvider): String
    fun providerApiKeyLabel(provider: AiProvider): String
    fun modelLabel(modelId: String, fallback: String): String
    fun modelSubtitle(modelId: String, fallback: String): String

    // ── Onboarding Guide ────────────────────────────────────────────────────────────────────
    val onboardingTitle: String
    fun onboardingSlideCounter(current: Int, total: Int): String
    val onboardingSlide1Title: String
    val onboardingSlide1Desc: String
    val onboardingSlide1Bullet1: String
    val onboardingSlide1Bullet2: String
    val onboardingSlide2Title: String
    val onboardingSlide2Desc: String
    val onboardingSlide2Bullet1: String
    val onboardingSlide2Bullet2: String
    val onboardingSlide3Title: String
    val onboardingSlide3Desc: String
    val onboardingSlide3Bullet1: String
    val onboardingSlide3Bullet2: String
    val onboardingSlide4Title: String
    val onboardingSlide4Desc: String
    val onboardingSlide4Bullet1: String
    val onboardingSlide4Bullet2: String
    val onboardingSlide5Title: String
    val onboardingSlide5Desc: String
    val onboardingSlide5Bullet1: String
    val onboardingSlide5Bullet2: String
    val onboardingSlide6Title: String
    val onboardingSlide6Desc: String
    val onboardingSlide6Bullet1: String
    val onboardingSlide6Bullet2: String
    val onboardingMockProjectDesc: String
    val onboardingMockCommitMsg: String
    val onboardingMockGreetingCode: String
    val onboardingMockAiButtonTitle: String
    val onboardingMockAiButtonSubtitle: String
    val onboardingMockAiResponse: String
    val onboardingMockActionSearch: String
    val onboardingMockActionRead: String
    val onboardingMockActionUpdated: String
    val onboardingMockAiProviderLabel: String
    val onboardingMockTokenSaved: String

    // ── Notifications, Proxy, LLM, Local Model, Git & File Core Errors ──────────────────────
    val notifFinishedErrorTitle: String
    val notifFinishedSuccessTitle: String
    fun notifOpenNamedChat(chatTitle: String): String
    val notifOpenChat: String
    val notifProgressChannelName: String
    val notifProgressChannelDesc: String
    val notifFinishedChannelName: String
    val notifFinishedChannelDesc: String
    fun notifOngoingNamedTitle(chatTitle: String): String
    val notifOngoingDefaultTitle: String

    val proxyErrEmpty: String
    fun proxyErrUnsupportedScheme(scheme: String): String
    val proxyErrNoHost: String
    val proxyErrNoIpOrDomain: String
    val proxyErrInvalidIpv6: String
    val proxyErrInvalidPort: String
    val proxyErrInvalidHost: String
    val proxyErrPortRange: String

    val llmErrClaudeStreamUnknown: String
    val llmErrGoogleAiStudio: String
    fun llmErrProviderStreamUnknown(providerName: String): String
    fun llmErrHttp401(provider: String): String
    fun llmErrHttp403(provider: String): String
    fun llmErrHttp404(provider: String, details: String): String
    val llmErrHttp407: String
    fun llmErrHttp429(provider: String, details: String): String
    fun llmErrHttp5xx(provider: String, code: Int): String
    fun llmErrHttpGeneric(provider: String, code: Int, details: String): String
    fun llmErrUnknownHost(provider: String): String
    fun llmErrTimeout(provider: String): String
    fun llmErrProxyConnect(provider: String): String
    fun llmErrGenericConnect(provider: String, details: String): String

    val localLlmErrEmptyHistory: String
    fun localLlmErrLoadFailed(details: String): String
    fun localLlmErrSessionInit(details: String): String
    fun localLlmErrGeneration(details: String): String
    fun localModelErrLowStorage(neededGb: String, freeGb: String): String
    fun localModelErrHttpStatus(code: Int): String
    val localModelErrEmptyFile: String
    val localModelErrIncomplete: String
    val localModelErrDownloadFailed: String
    fun localModelErrBadRedirect(code: Int): String
    val localModelErrTooManyRedirects: String

    fun undoNoVisibleGitChanges(path: String): String
    fun undoRemotePushWarning(command: String): String

    // Group discovery
    fun groupDiscoveryGithubOwnerNotFound(owner: String): String
    fun groupDiscoveryGithubReposFailed(owner: String): String
    fun groupDiscoveryGitlabGroupNotFound(path: String): String
    fun groupDiscoveryAuth401(detail: String): String
    fun groupDiscoveryForbidden403(detail: String): String
    fun groupDiscoveryServerError(code: Int, detail: String): String
    val groupDiscoveryEmptyUrl: String
    fun groupDiscoveryInvalidUrl(url: String): String
    val groupDiscoveryNoDomain: String
    val groupDiscoveryNoPath: String
    val groupDiscoveryGithubMustBeOrg: String
    val groupDiscoveryGithubOwnerBlank: String
    val groupDiscoveryGitlabPathBlank: String

    // JGitEngine, FileManager & ProjectRegistry
    val jgitErrEmptyUrl: String
    fun jgitErrTargetDirNotEmpty(name: String): String
    val jgitProgressConnecting: String
    val jgitProgressInitClone: String
    val jgitProgressDownloading: String
    fun jgitErrProjectDirExists(name: String): String
    val jgitReadmeCreatedLine: String
    val jgitErrRepoDirMissing: String
    val jgitErrReadGit: String
    val jgitErrEmptyCommitMsg: String
    val jgitErrEmptyBranchName: String
    val jgitErrNoCommitsForBranch: String
    val jgitErrEmptyNewBranchName: String
    fun jgitErrBranchNotFound(name: String): String
    val jgitErrEmptyRemoteName: String
    val jgitErrEmptyRemoteUrl: String
    fun jgitFetchUpToDate(remote: String): String
    fun jgitFetchUpdatedRefs(remote: String, count: Int): String
    val jgitPullDone: String
    val jgitPullFailed: String
    fun jgitUpdatedRefsSummary(count: Int): String
    val jgitErrNothingToPush: String
    val jgitErrNoCurrentBranch: String
    val jgitErrStashNoCommits: String
    val jgitErrEmptyTagName: String
    val jgitErrTagNoCommits: String
    val jgitTagsTargetWord: String
    fun jgitErrPush(details: String): String
    fun jgitPushFallbackOk(target: String, remote: String): String
    fun jgitPushSummary(remote: String, details: String): String
    fun jgitErrCommitNotFound(id: String): String
    fun jgitBinaryFileSummary(bytes: Int): String

    fun fmErrOutsideProject(path: String): String
    fun fmErrFileNotFound(path: String): String
    fun fmErrWriteToDirectory(path: String): String
    fun fmErrAlreadyExists(path: String): String
    fun fmErrCreateDirFailed(path: String): String
    fun fmErrInvalidName(name: String): String
    fun fmErrEntryNotFound(path: String): String
    fun fmErrTargetExists(name: String): String
    fun fmErrRenameFailed(path: String): String
    val fmErrDeleteRoot: String
    fun fmErrDeleteFailed(path: String): String

    fun registryErrCreateDirFailed(path: String): String
    fun registryErrProjectNotFound(id: String): String

    // MCP user-facing confirmations & hints
    fun mcpConfirmGenericTool(toolName: String): String
    fun mcpConfirmRun(toolName: String): String
    val mcpHintConfirmationRequired: String
    val mcpHintCancelledByUser: String
    fun mcpHintInternalError(toolName: String, details: String): String
    fun mcpHintRepoFolderDeleted(name: String): String
    fun mcpConfirmCreateProject(name: String): String
    fun mcpConfirmCreateRepo(name: String): String
    val mcpHintUseHttpsNotSsh: String
    fun mcpConfirmCloneRepo(url: String): String
    val mcpHintUseHttpsClone: String
    val mcpHintAddPatInSettings: String
    val mcpHintCheckPatScopes: String
    val mcpHintCheckNetwork: String
    val mcpHintBinaryNotEditable: String
    fun mcpConfirmEditFile(path: String): String
    fun mcpConfirmWriteFile(path: String): String
    fun mcpConfirmReplaceAll(query: String, replacement: String): String
    fun mcpConfirmFsBatch(count: Int): String
    fun mcpConfirmDeletePath(path: String): String
    val mcpHintGitHooksUnavailable: String
    fun mcpHintPushRejected(details: String): String
    val mcpHintSshUnsupported: String
    fun mcpConfirmGitForcePush(cmd: String): String
    fun mcpConfirmGitPush(cmd: String): String
    fun mcpConfirmGitResetHard(cmd: String): String
    fun mcpConfirmGitClean(cmd: String): String
    fun mcpConfirmGitRestore(cmd: String): String
    fun mcpConfirmGitDeleteBranch(cmd: String): String
    fun mcpConfirmGitStashDelete(cmd: String): String
    fun mcpConfirmGitCommand(cmd: String): String

    // ── Git OAuth (GitHub / GitLab / Self-hosted) ───────────────────────────────────────────
    val oauthAccountsTitle: String
    val oauthAccountsEmptyHint: String
    val oauthSignInGitHub: String
    val oauthSignInGitLab: String
    val oauthDefaultForHostBadge: String
    val oauthMakeDefaultForHost: String
    val oauthDisconnectAccountDesc: String
    fun oauthSignedInInfo(label: String): String
    fun oauthSignedInWithAuthorInfo(label: String): String
    fun oauthAccountRemovedInfo(label: String): String
    fun oauthHostDefaultUpdatedInfo(label: String): String
    val oauthProjectAccountBoundInfo: String
    val oauthRepoAccountBoundInfo: String

    val oauthGitHubDialogTitle: String
    val oauthGitHubDialogSubtitle: String
    val oauthGitHubClientIdLabel: String
    val oauthGitHubClientIdHint: String
    val oauthGitHubStartButton: String
    val oauthGitHubCodeLabel: String
    val oauthGitHubCopyAndOpenButton: String
    val oauthGitHubCodeCopiedInfo: String
    val oauthGitHubWaitingHint: String
    val oauthGitHubChangeClientId: String

    val oauthGitLabDialogTitle: String
    val oauthGitLabDialogSubtitle: String
    val oauthGitLabModeCloud: String
    val oauthGitLabModeSelfHosted: String
    val oauthGitLabServerUrlLabel: String
    val oauthGitLabServerUrlPlaceholder: String
    val oauthGitLabClientIdLabel: String
    val oauthGitLabSetupHint: String
    val oauthGitLabRedirectUriLabel: String
    val oauthGitLabCopyRedirectUriDesc: String
    val oauthGitLabRedirectUriCopiedInfo: String
    val oauthGitLabOpenBrowserButton: String
    val oauthGitLabWaitingHint: String
    val oauthGitLabExchangingHint: String

    val oauthScopedModeAccount: String
    val oauthScopedModeManual: String
    val oauthScopedNoAccountsHint: String
    val oauthScopedAddAccountTitle: String
    val oauthScopedClearOverride: String
    val oauthCloneUsingConnectedAccountsHint: String

    val oauthErrClientIdRequired: String
    val oauthErrInvalidServerUrl: String
    val oauthErrBrowserUnavailable: String
    val oauthErrDeviceExpired: String
    val oauthErrAccessDenied: String
    val oauthErrStateMismatch: String
    val oauthErrMissingCode: String
    fun oauthErrRequestFailed(details: String): String
    fun oauthErrProfileFailed(details: String): String
}
