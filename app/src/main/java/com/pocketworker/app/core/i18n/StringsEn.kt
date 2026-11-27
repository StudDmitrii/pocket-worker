package com.pocketworker.app.core.i18n

import com.pocketworker.app.core.storage.AiProvider

/**
 * English localization catalog for PocketWorker.
 */
object StringsEn : AppStrings {
    // ── Common ──────────────────────────────────────────────────────────────────────────────
    override val commonBack = "Back"
    override val commonClose = "Close"
    override val commonCancel = "Cancel"
    override val commonSave = "Save"
    override val commonCreate = "Create"
    override val commonDelete = "Delete"
    override val commonRename = "Rename"
    override val commonEdit = "Edit"
    override val commonReset = "Reset"
    override val commonRefresh = "Refresh"
    override val commonSettings = "Settings"
    override val commonActions = "Actions"
    override val commonClear = "Clear"
    override val commonCopy = "Copy"
    override val commonCopied = "Copied"
    override val commonNewBadge = "New"
    override val commonUsername = "Username"
    override val commonEncryptedInKeystore = "Encrypted in Android Keystore"
    override val commonShowHideToken = "Show/hide token"
    override val commonHideError = "Dismiss error"
    override val commonNotSet = "Not set"
    override val commonDone = "Done"
    override val commonNotDone = "Not completed"
    override val commonError = "Error"
    override val commonConfirmation = "Confirmation"
    override val commonAllow = "Allow"
    override val commonStop = "Stop"
    override val commonSend = "Send"
    override val commonRetry = "Retry"
    override val commonSkip = "Skip"
    override val commonNext = "Next"
    override val commonStartWorking = "Get started"
    override val commonDismissNotification = "Dismiss notification"

    override fun formatBytes(bytes: Long) = "$bytes B"
    override fun formatKilobytes(kb: Long) = "$kb KB"
    override fun formatMegabytes(mbFormatted: String) = "$mbFormatted MB"
    override fun gitTransportAuthError(raw: String) =
        "Authentication failed: check your username and Personal Access Token (PAT).\nDetails: $raw"
    override fun gitTransportForbiddenError(raw: String) =
        "Access denied (403): verify that your token has permissions for this repository.\nDetails: $raw"
    override fun gitTransportNotFoundError(raw: String) =
        "Repository or branch not found (404). Check the URL and access permissions.\nDetails: $raw"
    override fun gitTransportConnectionError(raw: String) =
        "Could not connect to server. Check your internet connection and repository URL.\nDetails: $raw"

    // ── Home (Projects) ─────────────────────────────────────────────────────────────────────
    override val homeTitle = "Projects"
    override val homeWorkspaceFolder = "Workspace"
    override fun homeAllProjectsCount(count: Int) = "All projects ($count)"
    override val homeRefreshList = "Refresh list"
    override val homeNewProject = "New project"
    override val homeEditProject = "Edit project"
    override fun homeDeleteProjectTitle(name: String) = "Delete project \"$name\"?"
    override fun homeDeleteProjectMessage(repoCount: Int) =
        "The project folder and all local repositories inside it ($repoCount) will be deleted from this device. Remote cloud repositories will not be affected."
    override val homeEmptyTitle = "No projects yet"
    override val homeEmptySubtitle =
        "Create a project folder to group related repositories together (client, backend, admin, services)."
    override val homeCreateFirstProject = "Create first project"
    override val homeProjectMenu = "Project menu"
    override val homeRenameOrDescription = "Rename / description"
    override val homeDeleteProjectAction = "Delete project"
    override fun homeDirtyReposBadge(count: Int) = "Modified: $count"
    override fun homeEmptyProjectHint(formattedTime: String) =
        "Tap to connect or create repositories • $formattedTime"
    override val homeProjectCreateSubtitle = "Container folder for your repositories"
    override val homeProjectEditSubtitle = "Update the project name or note"
    override val homeProjectNameLabel = "Project name"
    override val homeProjectNamePlaceholder = "e.g. Shop Platform"
    override val homeProjectDescLabel = "Description (optional)"
    override val homeProjectDescPlaceholder = "Client, backend, admin, and services"
    override val homeOpenProjectImmediately = "Open project immediately"
    override val homeOpenProjectImmediatelyHint = "Jump straight to adding repositories after creation"
    override fun formatRepoCount(count: Int): String =
        if (count == 1) "1 repository" else "$count repositories"
    override val homeErrEnterProjectName = "Enter a project name"
    override fun homeInfoProjectCreated(name: String) = "Project \"$name\" created"
    override val homeErrCreateProject = "Failed to create project"
    override val homeErrEmptyProjectName = "Project name cannot be empty"
    override val homeInfoProjectUpdated = "Project updated"
    override val homeInfoProjectDeleted = "Project deleted"
    override val homeInfoDefaultTokenCleared = "Default token cleared"
    override val homeInfoDefaultTokenSaved = "Default token saved"

    // ── Project Detail (Repositories) ───────────────────────────────────────────────────────
    override val projectBackToProjects = "Back to projects"
    override val projectFallbackTitle = "Project"
    override val projectFallbackInLocation = "project"
    override fun projectRepositoriesCount(count: Int) = "Repositories ($count)"
    override val projectAddRepo = "Add repository"
    override fun projectDeleteRepoTitle(name: String) = "Delete repository \"$name\"?"
    override val projectDeleteRepoMessage =
        "The local repository folder and its history on this device will be deleted. The remote cloud repository will not be affected."
    override fun projectEmptyTitle(projectName: String) = "Project \"$projectName\" is empty"
    override val projectEmptySubtitle =
        "Add repositories here (e.g. client, backend, admin, services): clone via Git URL, import a group, or create a new one."
    override val projectDeleteRepoDesc = "Delete repository"
    override val projectRepoStatusNoCommits = "No commits"
    override val projectRepoStatusClean = "Clean"
    override fun projectRepoStatusChanges(count: Int) = "Changes: $count"
    override val projectRepoNoGit = "No Git (can be initialized inside)"
    override val projectErrNotFound = "Project not found"
    override val projectErrLoadFailed = "Failed to load project"
    override val projectErrEnterRepoUrl = "Enter a Git repository URL"
    override val projectCloneConnecting = "Connecting to repository..."
    override fun projectInfoRepoAdded(name: String) = "Repository \"$name\" added to project"
    override val projectCloneCancelled = "Cloning cancelled"
    override val projectErrEnterGroupUrl = "Enter a GitLab group or GitHub user/organization URL"
    override val projectErrGroupNoRepos = "No accessible repositories found at this URL"
    override val projectErrSelectAtLeastOneRepo = "Select at least one repository"
    override fun projectGroupConnecting(repoName: String) = "Connecting to $repoName..."
    override fun projectGroupCancelled(succeeded: Int, total: Int) =
        "Group clone cancelled (downloaded: $succeeded of $total)"
    override fun projectGroupPartialErrorsHeader(succeeded: Int, total: Int) =
        "Downloaded $succeeded of $total. Errors:\n"
    override fun projectGroupMoreErrors(remaining: Int) = "\n...and $remaining more"
    override fun projectGroupAddedCount(count: Int) = "Repositories added: $count"
    override val projectErrEnterRepoName = "Enter a repository name"
    override fun projectInfoRepoCreated(name: String) = "Repository \"$name\" created"
    override val projectErrCreateRepo = "Failed to create repository"
    override val projectInfoRepoDeleted = "Repository deleted"
    override val projectInfoProjectTokenCleared = "Project token cleared"
    override val projectInfoProjectTokenSaved = "Project token saved"

    // Repo Dialogs
    override val repoAddSheetSubtitle = "The repository will be saved inside this project folder"
    override val repoConnectTitle = "Connect repository"
    override val repoConnectSubtitle = "Clone an existing Git repository via HTTPS URL"
    override val repoAddGroupTitle = "Add group"
    override val repoAddGroupSubtitle = "Clone multiple repositories from a GitHub or GitLab URL"
    override val repoCreateTitle = "Create repository"
    override val repoCreateSubtitle = "Create a new empty folder (with or without Git)"
    override val repoCloneDialogSubtitle = "Clone a Git repository over HTTPS"
    override val repoCloneAction = "Clone"
    override val repoUrlLabel = "Git repository URL"
    override val repoFolderNameLabel = "Folder / repository name"
    override val repoFolderNamePlaceholder = "Auto-detected from URL"
    override val repoBranchOptionalLabel = "Branch (optional)"
    override val repoBranchOptionalPlaceholder = "Default (HEAD)"
    override val repoSaveTokenForRepo = "Save token for this repository"
    override val repoCreateDialogSubtitle = "New local folder inside the project"
    override val repoNameLabel = "Repository (folder) name"
    override val repoInitGitTitle = "Initialize Git"
    override val repoInitGitSubtitle = "Create a local Git repository right away"
    override val repoCreateReadmeTitle = "README.md and initial commit"
    override val repoCreateReadmeSubtitle = "Prepare the initial branch right away"
    override val repoInitialBranchLabel = "Initial branch"
    override val repoRemoteUrlOptionalLabel = "Remote Git URL (optional)"
    override val repoAuthorNameLabel = "Author name (user.name)"
    override val repoAuthorEmailLabel = "Author email (user.email)"
    override val repoAuthTitle = "Authentication (PAT / password)"
    override val repoAuthSubtitle = "For private repositories and groups"
    override val repoAuthUserLabel = "Username (or blank for GitHub)"
    override val repoAuthTokenLabel = "Personal Access Token / Password"
    override val repoGroupDialogSubtitle = "Batch clone from GitHub / GitLab"
    override val repoGroupUrlLabel = "GitLab group or GitHub URL"
    override val repoGroupUrlPlaceholder = "https://gitlab.com/group or https://github.com/org"
    override val repoGroupSaveTokenLabel = "Save token for cloned repositories"
    override val repoGroupFindRepos = "Find repositories"
    override val repoGroupRefreshList = "Refresh list"
    override val repoGroupSearching = "Searching repositories..."
    override fun repoGroupFoundSelected(found: Int, selected: Int) =
        "Found: $found • Selected: $selected"
    override val repoGroupUnselectAll = "Clear all"
    override val repoGroupSelectAll = "Select all"
    override val repoGroupPrivateDesc = "Private"
    override val repoGroupAlreadyAdded = "Already added"
    override fun repoGroupDownloading(current: Int, total: Int, repoName: String) =
        "Cloning $current of $total: $repoName"
    override fun repoGroupDownloadSelected(count: Int) = "Clone selected ($count)"

    // ── Workspace (Tabs, Files, Editor, History) ────────────────────────────────────────────
    override val workspaceBackToProjectRepos = "Back to project repositories"
    override val workspaceRepoFallbackTitle = "Repository"
    override val workspaceTabFiles = "Files"
    override val workspaceTabHistory = "History"
    override val workspaceErrRepoNotFound = "Repository not found"
    override val workspaceErrLoadRepo = "Failed to load repository"
    override val workspaceErrOpenFile = "Could not open file"
    override fun workspaceInfoFileDeletedExternally(path: String) = "File \"$path\" was deleted"
    override fun workspaceInfoFileSaved(path: String) = "File saved: $path"
    override val workspaceErrSaveFile = "Failed to save file"
    override val workspaceBusyCreateFile = "Creating file..."
    override val workspaceBusyCreateFolder = "Creating folder..."
    override val workspaceBusyRename = "Renaming..."
    override val workspaceBusyDelete = "Deleting..."
    override val workspaceBusyInitGit = "Initializing Git..."
    override val workspaceInfoGitInitialized = "Git initialized"
    override val workspaceBusyStageFile = "Staging..."
    override val workspaceBusyStageAll = "Staging all changes..."
    override val workspaceBusyUnstageFile = "Unstaging..."
    override val workspaceBusyUnstageAll = "Unstaging all files..."
    override val workspaceBusyDiscardFile = "Discarding changes..."
    override fun workspaceInfoDiscardFileDone(path: String) = "Discarded changes in $path"
    override val workspaceBusyDiscardAll = "Discarding all changes..."
    override val workspaceInfoDiscardAllDone = "All working changes discarded"
    override val workspaceBusyCommitAndPush = "Committing & pushing..."
    override val workspaceBusyCommit = "Creating commit..."
    override fun workspaceInfoCommitAndPushDone(shortId: String, pushMsg: String) =
        "Commit $shortId created. $pushMsg"
    override fun workspaceInfoCommitDone(shortId: String, message: String) =
        "Commit $shortId created: $message"
    override val workspaceBusyCreateBranch = "Creating branch..."
    override fun workspaceInfoBranchCreatedAndCheckedOut(name: String) =
        "Switched to new branch \"$name\""
    override fun workspaceInfoBranchCreated(name: String) = "Branch \"$name\" created"
    override val workspaceBusyCheckoutBranch = "Switching branch..."
    override val workspaceInfoBranchSwitched = "Current branch switched"
    override val workspaceBusyRenameBranch = "Renaming branch..."
    override fun workspaceInfoBranchRenamed(oldName: String, newName: String) =
        "Branch \"$oldName\" renamed to \"$newName\""
    override val workspaceBusyDeleteBranch = "Deleting branch..."
    override fun workspaceInfoBranchDeleted(name: String) = "Branch \"$name\" deleted"
    override fun workspaceBusyMergeBranch(source: String) = "Merging branch \"$source\"..."
    override fun workspaceMergeConflictsNote(conflicts: String) = " Conflicts: $conflicts"
    override fun workspaceMergeFailedStatus(status: String, conflictNote: String) =
        "Merge ended with status $status.$conflictNote"
    override val workspaceBusyAddRemote = "Adding remote..."
    override fun workspaceInfoRemoteAdded(name: String) = "Remote \"$name\" added"
    override val workspaceBusyUpdateRemote = "Updating remote..."
    override fun workspaceInfoRemoteUpdated(name: String) = "URL updated for \"$name\""
    override val workspaceBusyRemoveRemote = "Removing remote..."
    override fun workspaceInfoRemoteRemoved(name: String) = "Remote \"$name\" removed"
    override fun workspaceBusyFetch(remote: String) = "Fetching from $remote..."
    override fun workspaceBusyPullRebase(remote: String) = "Pulling (rebase) from $remote..."
    override fun workspaceBusyPull(remote: String) = "Pulling from $remote..."
    override fun workspaceBusyForcePush(remote: String) = "Force pushing to $remote..."
    override fun workspaceBusyPush(remote: String) = "Pushing to $remote..."
    override fun workspaceBusySync(remote: String) = "Syncing with $remote..."
    override val workspaceBusyStashCreate = "Saving stash..."
    override val workspaceInfoStashEmpty = "No changes to stash"
    override fun workspaceInfoStashCreated(message: String) = "Saved to stash: $message"
    override fun workspaceBusyStashApply(index: Int) = "Applying stash@{$index}..."
    override fun workspaceInfoStashApplied(index: Int) = "Applied stash@{$index}"
    override fun workspaceBusyStashPop(index: Int) = "Popping stash@{$index}..."
    override fun workspaceInfoStashPopped(index: Int) = "Popped stash@{$index}"
    override fun workspaceBusyStashDrop(index: Int) = "Dropping stash@{$index}..."
    override fun workspaceInfoStashDropped(index: Int) = "Dropped stash@{$index}"
    override val workspaceBusyStashClear = "Clearing all stashes..."
    override val workspaceInfoStashCleared = "All stashed changes cleared"
    override fun workspaceBusyCreateTag(name: String) = "Creating tag \"$name\"..."
    override fun workspaceInfoTagCreated(name: String) = "Tag \"$name\" created"
    override fun workspaceBusyDeleteTag(name: String) = "Deleting tag \"$name\"..."
    override fun workspaceInfoTagDeleted(name: String) = "Tag \"$name\" deleted"
    override fun workspaceBusyPushTags(remote: String) = "Pushing tags to $remote..."
    override val workspaceInfoRepoTokenCleared =
        "Repository token cleared (project or default token will be used)"
    override val workspaceInfoRepoTokenSaved = "Repository access token saved"
    override val workspaceBusySaveAuthor = "Saving author settings..."
    override val workspaceInfoAuthorUpdated = "Commit author updated"
    override val workspaceDiffEmpty = "No text differences"
    override val workspaceErrDiffBuild = "Failed to build diff"
    override val workspaceBusyLoadHistory = "Loading history..."
    override val workspaceErrLoadHistory = "Failed to load history"
    override val workspaceErrLoadCommit = "Failed to load commit"
    override val workspaceErrDiff = "Diff error"

    // Files tab
    override val filesNavigateUp = "Up"
    override val filesSearchDesc = "Search files"
    override val filesToggleGitDirDesc = "Show/hide .git"
    override val filesNewFile = "New file"
    override val filesNewFolder = "New folder"
    override val filesSearchPlaceholder = "Search by file name or path..."
    override val filesNothingFound = "Nothing found"
    override val filesFolderEmpty = "Folder is empty"
    override val filesNewFileNameLabel = "File name (e.g. notes.md)"
    override val filesNewFolderNameLabel = "Folder name"
    override val filesNewNameLabel = "New name"
    override fun filesDeleteTitle(name: String) = "Delete \"$name\"?"
    override val filesDeleteFolderConfirm = "The folder and all its contents will be deleted from this device."
    override fun filesDeleteFileConfirm(path: String) = "File \"$path\" will be deleted."
    override val filesToolsDesc = "Tools"
    override val filesSyntaxCheckTitle = "Syntax check"
    override fun filesSyntaxCheckScopeSubtitle(scope: String) = "Scope: $scope"
    override val filesSyntaxCheckSingleFile = "Check syntax"
    override val filesSyntaxChecking = "Checking file syntax..."
    override val filesSyntaxNoErrorsTitle = "No syntax errors found"
    override fun filesSyntaxCheckedSummary(checked: Int, skipped: Int) =
        if (skipped > 0) "Files checked: $checked (skipped: $skipped)" else "Files checked: $checked"
    override fun filesSyntaxIssuesSummary(errors: Int, warnings: Int, filesWithIssues: Int) =
        "Errors: $errors • Warnings: $warnings • Files with issues: $filesWithIssues"
    override val filesSyntaxNoFilesToCheck = "No text files to check in this folder."
    override val filesSyntaxOpenFileHint = "Tap a file to open it in the editor"
    override val filesSyntaxCheckAgain = "Check again"
    override fun filesSyntaxLineCol(line: Int, col: Int) = "Ln $line, Col $col"
    override val filesSyntaxErrorBadge = "Error"
    override val filesSyntaxWarningBadge = "Warning"

    // File editor
    override val editorModifiedSuffix = " • modified"
    override val editorViewModeDesc = "View mode"
    override val editorEditModeDesc = "Edit"
    override fun editorBinaryUnsupported(size: String) =
        "Binary file ($size) is not supported by the text editor."
    override fun editorTooLarge(size: String) =
        "File is too large ($size) to open in the editor."
    override fun editorZoomReset(percent: String) = "$percent  •  reset"
    override val editorUnsavedTitle = "Unsaved changes"
    override fun editorUnsavedMessage(path: String) =
        "Save changes to \"$path\" before closing?"
    override val editorExitWithoutSaving = "Discard & exit"

    // History tab
    override val historyNoGit = "Git is not initialized in this repository"
    override val historyBranchLabel = "History branch:"
    override fun historyBranchCurrent(name: String) = "$name (current)"
    override val historyEmptyTitle = "Commit history is empty"
    override val historyEmptySubtitle =
        "Create your first commit in the Git tab and the branch history will appear here."
    override val historyLoadOlder = "Load earlier commits"

    // ── Git Control Tab, Action Sheet & Dialogs ─────────────────────────────────────────────
    override val gitNotInitializedTitle = "Git not initialized"
    override val gitNotInitializedSubtitle =
        "This repository doesn't have Git yet. Initialize it to track changes, branches, and remote sync."
    override val gitInitButton = "Initialize Git (branch main)"
    override val gitSelectBranchDesc = "Select branch"
    override val gitMenuDesc = "Git menu"
    override val gitCommitMessagePlaceholder = "Commit message..."
    override val gitAmendBanner = "Amend mode (update last commit)"
    override val gitDisableAmend = "Disable Amend"
    override val gitCommitOptionsDesc = "Commit options"
    override val gitCommitStaged = "Commit (staged)"
    override val gitCommitAmend = "Commit (Amend)"
    override val gitStagedSectionTitle = "Staged"
    override val gitUnstageAllDesc = "Unstage all"
    override val gitUnstageDesc = "Unstage"
    override val gitChangesSectionTitle = "Changes"
    override val gitDiscardAllDesc = "Discard all changes"
    override val gitStageAllDesc = "Stage all changes"
    override val gitWorkingTreeClean = "Working tree is clean"
    override val gitNoUnstagedChanges = "No unstaged changes"
    override val gitStageDesc = "Stage change"
    override val gitStashSectionTitle = "Stashes"
    override val gitDeleteStashDesc = "Drop stash"
    override val gitDiscardFileTitle = "Discard changes?"
    override fun gitDiscardFileMessage(path: String) =
        "All uncommitted changes in \"$path\" will be lost."
    override val gitDiscardAction = "Discard"
    override val gitDiscardAllTitle = "Discard all changes?"
    override val gitDiscardAllMessage =
        "All local changes to tracked files will be reset, and untracked files will be deleted."
    override val gitDiscardAllAction = "Discard all"
    override val gitForcePushTitle = "Run Force Push?"
    override fun gitForcePushMessage(remote: String) =
        "The remote branch on \"$remote\" will be forcibly overwritten with your local history."
    override fun gitRemoveRemoteTitle(name: String) = "Remove remote \"$name\"?"
    override fun gitRemoveRemoteMessage(url: String) =
        "URL $url will be unlinked from the local repository."
    override fun gitDropStashTitle(index: Int) = "Drop stash@{$index}?"
    override fun gitDropStashMessage(message: String) =
        "Stashed changes \"$message\" will be permanently deleted."
    override val gitClearStashesTitle = "Clear all stashes?"
    override fun gitClearStashesMessage(count: Int) =
        "All saved stash entries ($count) will be deleted."
    override fun gitDeleteTagTitle(name: String) = "Delete tag \"$name\"?"
    override val gitDeleteTagMessage = "The local tag will be removed from the repository."

    // Git Action Sheet
    override val gitSubmenuRoot = "Git Actions"
    override val gitSubmenuCommit = "Commit"
    override val gitSubmenuChanges = "Changes"
    override val gitSubmenuPullPush = "Pull, Push & Sync"
    override val gitSubmenuRemote = "Remotes"
    override val gitSubmenuStash = "Stash"
    override val gitSubmenuTags = "Tags"
    override fun gitBranchPrefix(branch: String) = "Branch: $branch"
    override val gitViewAsTreeTitle = "Changes view: tree"
    override val gitViewAsTreeSubtitleOn = "Grouped by folders"
    override val gitViewAsTreeSubtitleOff = "Flat file list"
    override val gitMenuRemoteSubtitle = "Manage Git remotes"
    override val gitMenuStashSubtitle = "Stash changes, Pop, Apply, Drop"
    override val gitMenuTagsSubtitle = "Create, delete, and push tags"
    override val gitMenuOutputSubtitle = "Log of executed Git commands"
    override fun gitCommitStagedSubtitleReady(count: Int) = "Create commit from staged files ($count)"
    override val gitCommitStagedSubtitleHint = "Enter a message and stage files"
    override val gitCommitAllSubtitle = "Stage all changes and create a commit"
    override fun gitCommitPushSubtitleReady(remote: String) =
        "Create a commit and push to $remote"
    override val gitNoRemoteConnected = "No remote configured"
    override val gitAmendToggleOn = "Disable Amend mode"
    override val gitAmendToggleOff = "Enable Amend mode"
    override val gitAmendToggleSubtitle = "Modify the last commit instead of creating a new one"
    override val gitOnBadge = "ON"
    override fun gitStageAllSubtitle(count: Int) = "Stage all modified files ($count)"
    override fun gitUnstageAllSubtitle(count: Int) = "Unstage all files ($count)"
    override val gitDiscardAllSubtitle =
        "Reset all uncommitted changes and delete untracked files"
    override val gitSyncSubtitle = "Pull remote changes first, then push local commits"
    override fun gitPullSubtitle(remote: String) = "Fetch and merge changes from $remote"
    override val gitPullRebaseSubtitle = "Rebase local commits on top of remote changes"
    override val gitPullFromSubtitle = "Choose a remote repository to pull from"
    override fun gitPushSubtitle(remote: String) = "Push commits to $remote"
    override val gitPushToSubtitle = "Choose a remote repository to push to"
    override val gitForcePushSubtitle = "Forcibly overwrite the remote branch"
    override val gitFetchSubtitle = "Update remote branch references without merging"
    override val gitAddRemoteTitle = "Add remote..."
    override val gitAddRemoteSubtitle = "Link a new remote repository by URL"
    override val gitNoRemotesConfigured = "No remote repositories configured."
    override val gitStashIncludeUntrackedTitle = "Stash (include untracked)..."
    override val gitStashIncludeUntrackedSubtitle = "Stash all current changes and new files"
    override val gitStashTrackedOnlyTitle = "Stash (tracked only)..."
    override val gitStashTrackedOnlySubtitle = "Leave untracked files untouched"
    override val gitStashPopLatestTitle = "Pop latest Stash"
    override val gitStashPopLatestSubtitle = "Apply stash@{0} and remove it from the list"
    override val gitStashApplyLatestTitle = "Apply latest Stash"
    override val gitStashApplyLatestSubtitle = "Apply stash@{0} and keep it in the list"
    override val gitStashClearAllTitle = "Clear all Stashes"
    override fun gitStashClearAllSubtitle(count: Int) = "Delete all stashed entries ($count)"
    override fun gitStashSavedHeader(count: Int) = "Saved Stashes ($count)"
    override val gitCreateTagTitle = "Create tag..."
    override fun gitCreateTagSubtitle(branch: String) = "Tag the current commit ($branch)"
    override val gitPushTagsTitle = "Push all tags"
    override fun gitPushTagsSubtitle(remote: String) = "Push local tags to $remote"
    override val gitNoTagsYet = "No tags yet."
    override fun gitLocalTagsHeader(count: Int) = "Local tags ($count)"
    override val gitDeleteTagDesc = "Delete tag"

    // Git Dialogs
    override val gitCopyShaDesc = "Copy SHA"
    override fun gitCommitParents(parents: String) = "Parents: $parents"
    override val gitCreateBranchFromCommit = "Create branch from this commit"
    override fun gitChangedFilesHeader(count: Int) = "Changed files ($count)"
    override fun gitRenamedFrom(oldPath: String) = "from $oldPath"
    override val gitNewBranchTitle = "New branch"
    override fun gitNewBranchBase(base: String) = "From base: $base"
    override val gitBranchNameLabel = "Branch name"
    override val gitCheckoutImmediately = "Switch to branch immediately"
    override val gitRenameBranchTitle = "Rename branch"
    override fun gitRenameBranchCurrent(name: String) = "Current name: $name"
    override val gitNewBranchNameLabel = "New branch name"
    override val gitEditRemoteTitle = "Edit remote"
    override val gitRemoteDialogSubtitle = "Connection to a remote Git repository"
    override val gitRemoteNameLabel = "Remote name"
    override val gitRepoAuthTitle = "Repository credentials"
    override val gitRepoAuthSubtitle = "For fetch, pull, and push operations"
    override val gitRepoAuthFallbackHint =
        "Leave the token field empty to fall back to the project or default token."
    override val gitRepoAuthDirectHint =
        "Enter your username and Personal Access Token (PAT) for fetch, pull, and push."
    override val gitAuthorTitle = "Commit author"
    override val gitAuthorRepoSubtitle = "Commit signature in this repository"
    override val gitAuthorSaveGlobally = "Also save globally"
    override val gitStashDialogTitle = "Save to Stash"
    override val gitStashDialogSubtitle = "Temporarily shelve current changes"
    override val gitStashAction = "Stash"
    override val gitStashMessageLabel = "Message (optional)"
    override val gitStashMessagePlaceholder = "e.g. WIP auth flow"
    override val gitStashIncludeUntrackedOption = "Include untracked files"
    override val gitTagDialogTitle = "New tag"
    override val gitTagDialogSubtitle = "Mark the current commit with a version"
    override val gitTagNameLabel = "Tag name"
    override val gitTagAnnotationLabel = "Annotation (optional)"
    override val gitTagAnnotationPlaceholder = "New version release"
    override val gitSwitchBranchTitle = "Switch branch"
    override val gitNewBranchShort = "New"
    override val gitSearchBranchPlaceholder = "Search branch..."
    override val gitLocalBranches = "Local branches"
    override fun gitRenameBranchDesc(name: String) = "Rename branch $name"
    override val gitRemoteBranches = "Remote branches"
    override val gitUnbornBranchHint = "The branch will be created automatically after the first commit"
    override val gitNoMatchingBranches = "No matching branches found"
    override val gitCopyLogDesc = "Copy log"
    override val gitClearLogDesc = "Clear log"
    override val gitOutputEmptyHint = "Executed Git commands and their output will appear here."

    // ── Settings ────────────────────────────────────────────────────────────────────────────
    override val settingsTitle = "Settings"
    override val settingsTabGeneral = "General"
    override val settingsTabAgent = "Agent"
    override val settingsSubtitleAgent = "AI model, API key, and agent behavior"
    override fun settingsSubtitleRepo(name: String) = "Repository \"$name\" and app"
    override fun settingsSubtitleProject(name: String) = "Project \"$name\" and app"
    override val settingsSubtitleGeneral = "Theme, language, background, and Git access"
    override val settingsAppearanceTitle = "Appearance"
    override val settingsThemeTitle = "Theme"
    override val settingsThemeAuto = "Auto"
    override val settingsThemeLight = "Light"
    override val settingsThemeDark = "Dark"
    override val settingsLanguageTitle = "Language"
    override val settingsLanguageSystem = "System"
    override val settingsLanguageRussian = "Русский"
    override val settingsLanguageEnglish = "English"
    override val settingsAnimatedBgTitle = "Background animation"
    override val settingsAnimatedBgSubtitle =
        "Smooth ambient animation over the gradient. Turn off to save battery."
    override val settingsGitAndAuthTitle = "Git & authentication"
    override val settingsScopeDefault = "Default"
    override val settingsDefaultTokenTitle = "Default Git token (PAT)"
    override val settingsDefaultTokenDialogTitle = "Default Git token"
    override val settingsDefaultTokenDialogDesc =
        "Stored in encrypted Android Keystore and used when neither the project nor repository defines its own token."
    override fun settingsTokenSavedInKeystore(user: String) = "Saved in Keystore ($user)"
    override val settingsDefaultAuthorTitle = "Default commit author"
    override fun settingsScopeProject(name: String) = "Project \"$name\""
    override val settingsProjectTokenTitle = "Project token"
    override fun settingsProjectTokenDialogDesc(name: String) =
        "Shared token for all repositories in project \"$name\". Used if a repository doesn't define its own token."
    override fun settingsTokenSet(user: String) = "Configured ($user)"
    override val settingsProjectAuthorTitle = "Project commit author"
    override fun settingsScopeRepo(name: String) = "Repository \"$name\""
    override val settingsRepoTokenTitle = "Repository token"
    override val settingsRepoAuthorTitle = "Repository commit author"
    override val settingsAboutTitle = "About"
    override val settingsVersionSubtitle = "Version 0.1.0 • Local Git & AI assistant"
    override val settingsGuideTitle = "User guide"
    override val settingsGuideSubtitle = "Open slide walkthrough for projects, Git, and AI agent"
    override val settingsGlobalAuthorSubtitle = "Used by default across all repositories"
    override fun settingsProjectAuthorSubtitle(name: String) =
        "Used for all repositories in project \"$name\" unless a repository sets its own author."

    // Agent Settings
    override val settingsModelAndConnectionTitle = "Model & connection"
    override val settingsProviderLabel = "Provider"
    override val settingsProviderLocalDownloadedDesc = "Model downloaded"
    override val settingsProviderKeySetDesc = "API key set"
    override val settingsModelLabel = "Model"
    override val settingsDownloadedDesc = "Downloaded"
    override val settingsSelectedDesc = "Selected"
    override val settingsGeminiDirectHint =
        "Direct connection to Google AI Studio (aistudio.google.com)"
    override val settingsOpenRouterHint =
        "Free and cloud OpenRouter models with tool calling support (openrouter.ai)"
    override val settingsHideKeyDesc = "Hide key"
    override val settingsShowKeyDesc = "Show key"
    override val settingsProxyTitle = "Proxy"
    override val settingsProxyLocalSubtitle = "Use proxy when downloading a local model"
    override val settingsProxyCloudSubtitle = "Route all AI API requests through a proxy server"
    override val settingsProxyAddressLabel = "Proxy URL"
    override val settingsProxyFormatHint = "Format: http://user:password@ip:port (or socks5://...)"
    override val settingsHideProxyDesc = "Hide proxy URL"
    override val settingsShowProxyDesc = "Show proxy URL"
    override val settingsAgentBehaviorTitle = "Agent behavior"
    override val settingsSystemPromptLabel = "System instructions (System Prompt)"
    override val settingsSystemPromptPlaceholder =
        "Answer concisely, explain changes before editing files..."
    override val settingsShareContextTitle = "Share folder & file context"
    override val settingsShareContextSubtitle =
        "Agent can inspect the currently open project, repository, and file"
    override val settingsPermissionsTitle = "Permissions"
    override val settingsPermissionsAsk = "Ask"
    override val settingsPermissionsAuto = "Auto"
    override val settingsPermissionsSubtitle =
        "\"Ask\" confirms file edits and local Git actions. \"Auto\" runs them without prompting; destructive actions (push, reset --hard, delete) always require confirmation."
    override val settingsSaveAgentButton = "Save agent settings"

    // Local Model Download UI
    override fun settingsLocalDownloading(label: String) = "Downloading $label..."
    override val settingsLocalCancelDownload = "Cancel download"
    override val settingsLocalReadyTitle = "Model downloaded and ready"
    override fun settingsLocalReadySubtitle(size: String) = "Size on device: $size • works offline"
    override val settingsLocalDeleteButton = "Delete model file"
    override val settingsLocalOfflineInfo =
        "Runs completely on-device (LiteRT-LM) with no API key and no internet required after download."
    override fun settingsLocalResumeHint(downloaded: String, total: String) =
        "Previously downloaded: $downloaded / $total — download will resume where it left off."
    override val settingsLocalContinueDownload = "Resume download"
    override val settingsLocalDownloadButton = "Download model"
    override fun settingsLocalRamLowHint(totalGb: String, availGb: String) =
        "RAM: $totalGb GB ($availGb GB free) • CPU and E2B model are recommended for stability"
    override fun settingsLocalRamNormalHint(totalGb: String, availGb: String) =
        "RAM: $totalGb GB ($availGb GB free)"
    override val settingsLocalGpuTitle = "GPU acceleration (OpenCL)"
    override val settingsLocalGpuSubtitle =
        "Off — runs on CPU (less RAM and avoids driver crashes). On — faster on flagship devices with 12+ GB RAM."
    override val settingsLocalDeleteDialogTitle = "Delete local model?"
    override fun settingsLocalDeleteDialogBody(label: String, size: String) =
        "File \"$label\" ($size) will be removed from this device. You will need to download it again to use this model."

    // Settings ViewModel messages
    override val settingsInfoProjectAuthorReset = "Project commit author reset (using default)"
    override val settingsInfoProjectAuthorSaved = "Project commit author saved"
    override val settingsInfoRepoAuthorUpdated = "Repository commit author updated"
    override val settingsInfoRepoAuthorReset = "Repository commit author reset"
    override val settingsInfoGlobalAuthorSaved = "Default commit author saved"
    override val settingsInfoGlobalAuthorReset = "Default commit author reset"
    override val settingsInfoLocalModelDeleted = "Local model deleted"
    override fun settingsInfoLocalModelActive(label: String) =
        "Local model \"$label\" saved and active"
    override val settingsInfoAgentSavedDownloadLocal =
        "Agent settings saved (download the selected local model)"
    override val settingsInfoAgentSaved = "AI & agent settings saved"

    // ── AI Chat, Context, Markdown, Floating Button & Providers ─────────────────────────────
    override val aiChatDrawerDesc = "Chat drawer"
    override val aiScrollToBottomDesc = "Scroll to bottom"
    override val aiScrollToBottomLabel = "Bottom"
    override val aiChatHistoryTitle = "Chat history"
    override val aiRenameChatTitle = "Rename chat"
    override val aiRenameChatLabel = "Chat title"
    override val aiDeleteChatDesc = "Delete chat"
    override val aiChatHistoryLocalHint = "Chat history is stored locally on this device"
    override val aiNewChat = "New chat"
    override val aiDeleteDialogTitle = "Delete conversation?"
    override fun aiDeleteDialogBody(title: String) =
        "Conversation \"$title\" and all of its messages will be permanently deleted."
    override fun aiAttachedFilesLine(names: String) = "Files: $names"
    override val aiRetrySubtitle = "Roll back changes and restore message to input"
    override val aiCopyResponseDesc = "Copy response"
    override fun aiActionsGroupTitle(count: Int) = "Actions ($count)"
    override val aiCollapseActionsDesc = "Collapse actions"
    override val aiExpandActionsDesc = "Expand actions"
    override fun aiChangedFilesMore(names: String, remaining: Int) = "$names and $remaining more"
    override fun aiChangedFilesGroupTitle(count: Int) = "Changed files ($count)"
    override val aiCollapseFilesDesc = "Collapse files"
    override val aiExpandFilesDesc = "Expand files"
    override val aiOpenDiffDesc = "Open diff"
    override val aiThinking = "Thinking..."
    override val aiCollapseDetailsDesc = "Collapse details"
    override val aiExpandDetailsDesc = "Show details"
    override fun aiToolLabel(toolName: String) = "Tool: $toolName"
    override fun aiParamsLabel(preview: String) = "Parameters:\n$preview"
    override fun aiResultLabel(preview: String) = "Result:\n$preview"
    override val aiOpenAgentSettings = "Open agent settings"
    override val aiConfirmDangerousTitle = "Confirmation required (destructive action)"
    override val aiConfirmNormalTitle = "Confirm agent action"
    override val aiAttachFileDesc = "Attach file"
    override val aiInputPlaceholderBusy = "Agent is working..."
    override val aiInputPlaceholderIdle = "Type a message..."
    override val aiRemoveAttachmentDesc = "Remove file"
    override fun aiAttachedFileHeader(name: String) = "[Attached file: $name]"
    override fun aiAttachedBinaryHeader(name: String, mime: String) =
        "[Attached file: $name ($mime)]"

    // AI ViewModel statuses & errors
    override val aiRollingBack = "Rolling back changes..."
    override val aiReturnedToInput = "Message restored to input"
    override fun aiRollbackCountSuffix(count: Int) = " • changes rolled back: $count"
    override fun aiNoSavedDiff(path: String) = "No saved diff available for $path."
    override fun aiAttachedFilesSummary(count: Int) = "Attached files ($count)"
    override fun aiProviderNotConnected(providerName: String) =
        "Client for $providerName is not connected yet. Choose an available provider in Settings → Agent."
    override fun aiLocalModelNotDownloaded(modelLabel: String) =
        "Local model \"$modelLabel\" is not downloaded yet. Open Settings → Agent and tap Download model."
    override fun aiMissingApiKey(providerName: String) =
        "No API key set for $providerName. Add your key in Settings → Agent."
    override fun aiInvalidProxy(reason: String) = "Proxy is enabled, but the URL is invalid: $reason"
    override val aiLoadingLocalModel = "Loading local model..."
    override val aiUnexpectedError = "Unexpected agent error."
    override val aiAnalyzingResult = "Analyzing result..."
    override val aiResponding = "Responding..."
    override val aiWebSearchRunning = "Searching the web..."
    override val aiWebSearchDone = "Web search finished"
    override fun aiWaitingConfirmationStatus(summary: String) = "Waiting for confirmation: $summary"
    override val aiWaitingConfirmationTitle = "Waiting for confirmation..."
    override val aiRejectedByUser = "Cancelled by user"
    override fun aiToolErrorTitle(code: String) = "Error: $code"
    override fun aiMaxStepsReached(maxSteps: Int) =
        "Reached the agent step limit ($maxSteps) for a single turn. You can continue with a new message."

    override fun aiToolStatus(toolName: String): Pair<String, String> = when (toolName) {
        "web_search" -> aiWebSearchRunning to aiWebSearchDone
        "workspace_get_context" -> "Checking context..." to "Context loaded"
        "workspace_list_projects" -> "Listing projects..." to "Projects listed"
        "workspace_create_project" -> "Creating project..." to "Project created"
        "workspace_list_repos" -> "Listing repositories..." to "Repositories listed"
        "workspace_create_repo" -> "Creating repository..." to "Repository created"
        "workspace_clone_repo" -> "Cloning repository..." to "Repository cloned"
        "workspace_discover_group_repos" -> "Finding group repositories..." to "Repositories found"
        "repo_overview" -> "Inspecting repository..." to "Repository overview ready"
        "fs_list_dir" -> "Listing folder..." to "Folder listed"
        "fs_find_files" -> "Finding files..." to "Files found"
        "fs_grep" -> "Searching code..." to "Code search finished"
        "fs_read_file" -> "Reading file..." to "File read"
        "fs_edit_file" -> "Editing file..." to "File updated"
        "fs_write_file" -> "Writing file..." to "File saved"
        "fs_replace_all" -> "Replacing across files..." to "Replacements applied"
        "fs_batch_structure" -> "Updating folder structure..." to "Structure updated"
        "fs_delete_path" -> "Deleting..." to "Deleted"
        "git_exec" -> "Running Git..." to "Git command completed"
        "check_push_readiness" -> "Checking push readiness..." to "Push check finished"
        "check_merge_conflicts" -> "Checking merge conflicts..." to "Conflict check finished"
        "check_syntax" -> "Checking syntax..." to "Syntax checked"
        else -> "Running $toolName..." to "Completed: $toolName"
    }

    override fun aiSyntaxFilesCount(count: Int) = if (count == 1) "1 file" else "$count files"
    override val aiSyntaxChangedFiles = "changed files"

    // AI Context
    override val aiGreetingAllProjects =
        "Hi! How can I help? You're currently in the projects workspace, and I can see all your projects."
    override fun aiGreetingProjectNamed(name: String) =
        "Hi! How can I help? You're currently in project \"$name\", and I can see its repositories."
    override val aiGreetingProjectUnnamed =
        "Hi! How can I help? You're currently in a project folder, and I can see its repositories."
    override fun aiGreetingRepoSubdirNamed(dir: String, repo: String) =
        "Hi! How can I help? You're currently in folder \"$dir\" of repository \"$repo\", and I can see it."
    override fun aiGreetingRepoSubdirUnnamed(dir: String) =
        "Hi! How can I help? You're currently in folder \"$dir\" of the repository, and I can see it."
    override fun aiGreetingRepoRootNamed(repo: String) =
        "Hi! How can I help? You're currently in repository \"$repo\", and I can see it."
    override val aiGreetingRepoRootUnnamed =
        "Hi! How can I help? You're currently in a repository folder, and I can see it."
    override fun aiGreetingFileWithRepo(file: String, repo: String) =
        "Hi! How can I help? You're currently viewing \"$file\" in repository \"$repo\", and I can see it."
    override fun aiGreetingFileWithoutRepo(file: String) =
        "Hi! How can I help? You're currently viewing \"$file\", and I can see it."

    override val aiEmptyPromptAllProjects = "What shall we work on in projects?"
    override fun aiEmptyPromptProjectNamed(name: String) =
        "What shall we work on in project \"$name\"?"
    override val aiEmptyPromptProjectUnnamed = "What shall we work on in this project?"
    override fun aiEmptyPromptRepoSubdirNamed(dir: String, repo: String) =
        "What shall we work on in \"$dir\" of \"$repo\"?"
    override fun aiEmptyPromptRepoNamed(repo: String) =
        "What shall we work on in repository \"$repo\"?"
    override fun aiEmptyPromptDirOnly(dir: String) = "What shall we work on in folder \"$dir\"?"
    override val aiEmptyPromptRepoUnnamed = "What shall we work on in this repository?"
    override fun aiEmptyPromptFileNamed(fileName: String) =
        "What shall we work on in \"$fileName\"?"
    override val aiEmptyPromptFileUnnamed = "What shall we work on in this file?"

    override val aiSubtitleAllProjects = "All projects"
    override fun aiSubtitleProject(name: String) = "Project: $name"
    override val aiSubtitleProjectFolder = "Project folder"
    override fun aiSubtitleRepo(name: String) = "Repository: $name"
    override val aiSubtitleRepoUnnamed = "Repository"
    override val aiSubtitleFile = "File"

    // Markdown & Floating AI Button
    override val mdCalloutNote = "Note"
    override val mdCalloutTip = "Tip"
    override val mdCalloutImportant = "Important"
    override val mdCalloutWarning = "Warning"
    override val mdCalloutCaution = "Caution"
    override val mdCodeDefaultLabel = "Code"
    override val mdCopyCodeDesc = "Copy code"
    override val fabRevealAiDesc = "Reveal AI button"
    override val fabOpenAiDesc = "Open AI chat"
    override val fabRevealFoldersDesc = "Reveal return button"
    override val fabReturnToFoldersDesc = "Return to folders"

    // AI Providers & Models
    override fun providerDisplayName(provider: AiProvider): String = when (provider) {
        AiProvider.ANTHROPIC -> "Claude"
        AiProvider.GEMINI -> "Gemini"
        AiProvider.OPENROUTER -> "OpenRouter"
        AiProvider.OPENAI -> "OpenAI"
        AiProvider.LOCAL -> "Local"
    }

    override fun providerApiKeyLabel(provider: AiProvider): String = when (provider) {
        AiProvider.ANTHROPIC -> "Claude API key (Android Keystore)"
        AiProvider.GEMINI -> "Google AI Studio API key (Keystore)"
        AiProvider.OPENROUTER -> "OpenRouter API key (Keystore)"
        AiProvider.OPENAI -> "OpenAI API key (Android Keystore)"
        AiProvider.LOCAL -> ""
    }

    override fun modelLabel(modelId: String, fallback: String): String = when (modelId) {
        "gemma-4-E2B-it" -> "Gemma 4 E2B (~2.58 GB)"
        "gemma-4-E4B-it" -> "Gemma 4 E4B (~3.65 GB)"
        else -> fallback
    }

    override fun modelSubtitle(modelId: String, fallback: String): String = when (modelId) {
        "claude-sonnet-5-5" -> "Balanced speed and coding quality"
        "claude-opus-5-5" -> "Flagship model for extended agentic coding"
        "claude-fable-5-2" -> "Deep reasoning for complex tasks"
        "claude-haiku-5-5" -> "Ultra-fast and economical model"
        "gemini-4-neon" -> "Fast Gemini 4 model for code and tools"
        "gemini-4-carbon" -> "Flagship Gemini 4 for complex architecture"
        "gemini-4-hydrogen" -> "Lightweight and economical Gemini 4"
        "gemini-3.5-flash" -> "Fast general-purpose model"
        "gemini-3.5-flash-lite" -> "Maximum speed and low cost"
        "gemini-3.1-pro-preview" -> "Pro model with advanced tool calling"
        "particle-ai/particle-1:free" -> "Agentic coding model (DeepSeek V4.1 Flash, 1M)"
        "nvidia/nemotron-3-ultra-550b-a55b:free" -> "Large NVIDIA MoE model for orchestration & complex tasks"
        "google/gemma-4-31b-it:free" -> "Dense Google model with native tool calling"
        "inclusionai/ling-3.2-flash:free" -> "Hybrid reasoning model with 1M context"
        "baidu/ernie-5.5-flash-preview:free" -> "Free Baidu preview model via OpenRouter"
        "gpt-6.2-sol" -> "Balanced model for code and agents"
        "gpt-6.2-astra" -> "OpenAI flagship for complex tasks and analysis"
        "gpt-6.1-terra" -> "Fast model for agentic workflows"
        "gpt-6-luna" -> "Economical model for frequent requests"
        "gpt-5.5" -> "Reliable model with 1M+ context"
        "gpt-5.4" -> "Proven model for tool calling"
        "gemma-4-E2B-it" -> "Offline model for smartphones (6–8+ GB RAM)"
        "gemma-4-E4B-it" -> "Smarter for code and tools (8–12+ GB RAM)"
        else -> fallback
    }

    // ── Onboarding Guide ────────────────────────────────────────────────────────────────────
    override val onboardingTitle = "Guide"
    override fun onboardingSlideCounter(current: Int, total: Int) = "Slide $current of $total"
    override val onboardingSlide1Title = "1. Projects & folders"
    override val onboardingSlide1Desc =
        "Group related repositories into projects on the home screen — such as a mobile app, backend, and shared libraries."
    override val onboardingSlide1Bullet1 = "Tap \"+\" at the bottom to create a new project."
    override val onboardingSlide1Bullet2 =
        "Each card shows repository count and uncommitted changes at a glance."
    override val onboardingSlide2Title = "2. Repositories & cloning"
    override val onboardingSlide2Desc =
        "Inside a project, create local repositories, clone via HTTPS URL, or batch-import a GitHub / GitLab group."
    override val onboardingSlide2Bullet1 =
        "Every card displays the current branch, change count, and latest commit."
    override val onboardingSlide2Bullet2 = "Saved Personal Access Tokens are applied automatically."
    override val onboardingSlide3Title = "3. Code editor & Git"
    override val onboardingSlide3Desc =
        "Open files with syntax highlighting, inspect line-by-line diffs, and run core Git workflows right from your phone."
    override val onboardingSlide3Bullet1 =
        "Supports commit, amend, branches, merge, stash, tags, pull, and push."
    override val onboardingSlide3Bullet2 = "The Files and Git tabs stay synchronized at all times."
    override val onboardingSlide4Title = "4. AI agent on every screen"
    override val onboardingSlide4Desc =
        "Tap the floating AI orb to open chat. The agent sees your current project, repository, or open file and responds with rich formatting."
    override val onboardingSlide4Bullet1 =
        "Drag the AI button anywhere along the screen edges to keep code clear."
    override val onboardingSlide4Bullet2 =
        "The agent can search and edit files, check syntax, and run Git commands."
    override val onboardingSlide5Title = "5. Actions, files & rollback"
    override val onboardingSlide5Desc =
        "After a response, agent steps fold into a compact block while changed files display Git line stats."
    override val onboardingSlide5Bullet1 =
        "Tap any changed file in chat to view its Git diff immediately."
    override val onboardingSlide5Bullet2 =
        "Tap your own message to retry it and automatically roll back agent edits."
    override val onboardingSlide6Title = "6. AI & Git access settings"
    override val onboardingSlide6Desc =
        "Pick a cloud provider or download Gemma 4 for offline use. Configure tokens and commit authors at the right scope."
    override val onboardingSlide6Bullet1 =
        "Supports Claude, Gemini (Google AI Studio), OpenRouter, OpenAI, and offline Gemma 4."
    override val onboardingSlide6Bullet2 =
        "Git tokens and commit authors follow the hierarchy: default → project → repository."
    override val onboardingMockProjectDesc = "Android app and backend service"
    override val onboardingMockCommitMsg = "feat: updated settings screen"
    override val onboardingMockGreetingCode = "Hello"
    override val onboardingMockAiButtonTitle = "Floating AI agent button"
    override val onboardingMockAiButtonSubtitle = "Available over projects, folders, and the editor"
    override val onboardingMockAiResponse =
        "Done! Added token validation in `AuthService.kt` and updated **unit tests**."
    override val onboardingMockActionSearch = "Search files"
    override val onboardingMockActionRead = "Read file"
    override val onboardingMockActionUpdated = "File updated"
    override val onboardingMockAiProviderLabel = "AI Provider"
    override val onboardingMockTokenSaved = "Saved in Keystore (dev-user)"

    // ── Notifications, Proxy, LLM, Local Model, Git & File Core Errors ──────────────────────
    override val notifFinishedErrorTitle = "Agent finished with an error"
    override val notifFinishedSuccessTitle = "Agent finished the task"
    override fun notifOpenNamedChat(chatTitle: String) = "Chat \"$chatTitle\" — tap to open"
    override val notifOpenChat = "Tap to open chat"
    override val notifProgressChannelName = "AI agent activity"
    override val notifProgressChannelDesc =
        "Shows the current AI agent action while the app is in the background"
    override val notifFinishedChannelName = "AI agent task completion"
    override val notifFinishedChannelDesc =
        "Notifies you when the AI agent finishes a task in the background"
    override fun notifOngoingNamedTitle(chatTitle: String) = "AI Agent • $chatTitle"
    override val notifOngoingDefaultTitle = "AI Agent is working"

    override val proxyErrEmpty = "Enter a proxy URL, e.g. http://user:password@ip:port"
    override fun proxyErrUnsupportedScheme(scheme: String) =
        "Unsupported proxy scheme \"$scheme\". Use http:// or socks5://."
    override val proxyErrNoHost = "Could not determine the proxy host address."
    override val proxyErrNoIpOrDomain = "Proxy server IP or domain is missing."
    override val proxyErrInvalidIpv6 = "Invalid IPv6 proxy address."
    override val proxyErrInvalidPort = "Invalid proxy port."
    override val proxyErrInvalidHost = "Invalid proxy server host."
    override val proxyErrPortRange = "Proxy port must be in the range 1..65535."

    override val llmErrClaudeStreamUnknown = "Unknown Claude stream error"
    override val llmErrGoogleAiStudio = "Google AI Studio error."
    override fun llmErrProviderStreamUnknown(providerName: String) =
        "Unknown $providerName stream error"
    override fun llmErrHttp401(provider: String) =
        "Invalid API key for $provider (401). Check your key in Settings → Agent."
    override fun llmErrHttp403(provider: String) =
        "Access denied (403) for $provider. Check key permissions or model access."
    override fun llmErrHttp404(provider: String, details: String) =
        "Model not found or unavailable for your $provider account (404).$details"
    override val llmErrHttp407 =
        "Proxy server requires authentication (407). Check the username and password in the proxy URL."
    override fun llmErrHttp429(provider: String, details: String) =
        "Rate limit or quota exceeded for $provider (429).$details"
    override fun llmErrHttp5xx(provider: String, code: Int) =
        "$provider server is temporarily unavailable ($code). Please try again later."
    override fun llmErrHttpGeneric(provider: String, code: Int, details: String) =
        "$provider error (HTTP $code): $details"
    override fun llmErrUnknownHost(provider: String) =
        "Could not connect to $provider or the proxy host. Check your network and proxy settings."
    override fun llmErrTimeout(provider: String) =
        "Request to $provider timed out. Check your network or proxy."
    override fun llmErrProxyConnect(provider: String) =
        "Proxy error while connecting to $provider. Check proxy host, port, and credentials."
    override fun llmErrGenericConnect(provider: String, details: String) =
        "Connection error with $provider: $details"

    override val localLlmErrEmptyHistory = "Empty message history for local model."
    override fun localLlmErrLoadFailed(details: String) =
        "Failed to load local Gemma 4 model: $details. Make sure the device has enough free RAM."
    override fun localLlmErrSessionInit(details: String) =
        "Failed to initialize local model session: $details"
    override fun localLlmErrGeneration(details: String) =
        "Local model generation error: $details"
    override fun localModelErrLowStorage(neededGb: String, freeGb: String) =
        "Not enough storage on device: needs ~$neededGb GB, available $freeGb GB."
    override fun localModelErrHttpStatus(code: Int) = "Download server returned HTTP $code"
    override val localModelErrEmptyFile = "Downloaded model file is empty."
    override val localModelErrIncomplete =
        "Download was interrupted. Tap \"Download model\" to resume."
    override val localModelErrDownloadFailed = "Failed to download model."
    override fun localModelErrBadRedirect(code: Int) =
        "Invalid redirect (HTTP $code) missing Location header."
    override val localModelErrTooManyRedirects = "Too many redirects while downloading model."

    override fun undoNoVisibleGitChanges(path: String) =
        "No visible Git changes for file $path."
    override fun undoRemotePushWarning(command: String) =
        "Remote push ($command) cannot be undone locally; local state has been rolled back."

    // Group discovery
    override fun groupDiscoveryGithubOwnerNotFound(owner: String) =
        "GitHub user or organization \"$owner\" not found"
    override fun groupDiscoveryGithubReposFailed(owner: String) =
        "Failed to fetch GitHub repositories for \"$owner\""
    override fun groupDiscoveryGitlabGroupNotFound(path: String) =
        "GitLab group or user \"$path\" not found"
    override fun groupDiscoveryAuth401(detail: String) =
        "Authentication error (401): enable auth and provide a valid Personal Access Token (PAT)$detail"
    override fun groupDiscoveryForbidden403(detail: String) =
        "Access denied (403): check token scopes or API rate limit$detail"
    override fun groupDiscoveryServerError(code: Int, detail: String) =
        "Server error ($code)$detail"
    override val groupDiscoveryEmptyUrl = "Enter a group or organization URL"
    override fun groupDiscoveryInvalidUrl(url: String) = "Invalid URL: $url"
    override val groupDiscoveryNoDomain = "Could not determine domain in URL"
    override val groupDiscoveryNoPath = "Include a group or organization path in the URL"
    override val groupDiscoveryGithubMustBeOrg =
        "For GitHub, provide an organization or profile URL (e.g. https://github.com/my-org), not a single repository URL"
    override val groupDiscoveryGithubOwnerBlank =
        "Could not determine GitHub organization or username"
    override val groupDiscoveryGitlabPathBlank = "Could not determine GitLab group from URL"

    // JGitEngine, FileManager & ProjectRegistry
    override val jgitErrEmptyUrl = "Repository URL cannot be empty"
    override fun jgitErrTargetDirNotEmpty(name: String) =
        "Target folder already exists and is not empty: $name"
    override val jgitProgressConnecting = "Connecting..."
    override val jgitProgressInitClone = "Initializing clone..."
    override val jgitProgressDownloading = "Downloading..."
    override fun jgitErrProjectDirExists(name: String) = "Project folder already exists: $name"
    override val jgitReadmeCreatedLine = "Created in PocketWorker."
    override val jgitErrRepoDirMissing = "Repository folder not found on disk"
    override val jgitErrReadGit = "Error reading Git repository"
    override val jgitErrEmptyCommitMsg = "Commit message cannot be empty"
    override val jgitErrEmptyBranchName = "Branch name cannot be empty"
    override val jgitErrNoCommitsForBranch = "Repository has no commits yet to branch from"
    override val jgitErrEmptyNewBranchName = "New branch name cannot be empty"
    override fun jgitErrBranchNotFound(name: String) = "Branch not found: $name"
    override val jgitErrEmptyRemoteName = "Remote name cannot be empty"
    override val jgitErrEmptyRemoteUrl = "Remote URL cannot be empty"
    override fun jgitFetchUpToDate(remote: String) = "Fetch ($remote): already up to date"
    override fun jgitFetchUpdatedRefs(remote: String, count: Int) =
        "Fetch ($remote): updated $count ref(s)"
    override val jgitPullDone = "Pull completed"
    override val jgitPullFailed = "Pull failed"
    override fun jgitUpdatedRefsSummary(count: Int) = "Updated refs: $count"
    override val jgitErrNothingToPush = "Nothing to push: create at least one commit first"
    override val jgitErrNoCurrentBranch = "Could not determine current branch"
    override val jgitErrStashNoCommits = "Cannot create a stash in a repository with no commits"
    override val jgitErrEmptyTagName = "Tag name cannot be empty"
    override val jgitErrTagNoCommits = "Cannot create a tag in a repository with no commits"
    override val jgitTagsTargetWord = "tags"
    override fun jgitErrPush(details: String) = "Push failed: $details"
    override fun jgitPushFallbackOk(target: String, remote: String) =
        "Push ($target) to $remote succeeded"
    override fun jgitPushSummary(remote: String, details: String) = "Push to $remote: $details"
    override fun jgitErrCommitNotFound(id: String) = "Commit not found: $id"
    override fun jgitBinaryFileSummary(bytes: Int) = "Binary file ($bytes bytes)"

    override fun fmErrOutsideProject(path: String) = "Path escapes project root: $path"
    override fun fmErrFileNotFound(path: String) = "File not found: $path"
    override fun fmErrWriteToDirectory(path: String) = "Cannot write text to a directory: $path"
    override fun fmErrAlreadyExists(path: String) = "File or folder already exists: $path"
    override fun fmErrCreateDirFailed(path: String) = "Failed to create folder: $path"
    override fun fmErrInvalidName(name: String) = "Invalid name: $name"
    override fun fmErrEntryNotFound(path: String) = "Item not found: $path"
    override fun fmErrTargetExists(name: String) = "An item named '$name' already exists"
    override fun fmErrRenameFailed(path: String) = "Failed to rename '$path'"
    override val fmErrDeleteRoot = "Cannot delete the project root via file manager"
    override fun fmErrDeleteFailed(path: String) = "Failed to delete: $path"

    override fun registryErrCreateDirFailed(path: String) =
        "Failed to create project directory: $path"
    override fun registryErrProjectNotFound(id: String) = "Project not found: $id"

    // MCP user-facing confirmations & hints
    override fun mcpConfirmGenericTool(toolName: String) = "Run tool $toolName"
    override fun mcpConfirmRun(toolName: String) = "Run $toolName"
    override val mcpHintConfirmationRequired =
        "Explicit user confirmation is required for this action."
    override val mcpHintCancelledByUser = "Operation cancelled by user."
    override fun mcpHintInternalError(toolName: String, details: String) =
        "Internal error while running '$toolName': $details"
    override fun mcpHintRepoFolderDeleted(name: String) =
        "Repository folder '$name' was deleted from this device."
    override fun mcpConfirmCreateProject(name: String) = "Create new project \"$name\""
    override fun mcpConfirmCreateRepo(name: String) = "Create repository \"$name\""
    override val mcpHintUseHttpsNotSsh = "Use an HTTPS repository URL (https://...) instead of SSH."
    override fun mcpConfirmCloneRepo(url: String) = "Clone repository $url"
    override val mcpHintUseHttpsClone =
        "Use an HTTPS URL (https://...) for cloning and add a PAT in Settings if needed."
    override val mcpHintAddPatInSettings =
        "Add or update your Personal Access Token in Settings → General (repository, project, or default)."
    override val mcpHintCheckPatScopes =
        "Check your Personal Access Token scopes for this repository."
    override val mcpHintCheckNetwork = "Check your internet connection and server availability."
    override val mcpHintBinaryNotEditable = "Binary files cannot be edited by the text agent."
    override fun mcpConfirmEditFile(path: String) = "Edit file $path"
    override fun mcpConfirmWriteFile(path: String) = "Write file $path"
    override fun mcpConfirmReplaceAll(query: String, replacement: String) =
        "Replace all \"$query\" → \"$replacement\""
    override fun mcpConfirmFsBatch(count: Int) = "Modify folder/file structure ($count ops)"
    override fun mcpConfirmDeletePath(path: String) = "Delete \"$path\""
    override val mcpHintGitHooksUnavailable =
        "Git hooks are not available on mobile. Run project checks on desktop or in CI."
    override fun mcpHintPushRejected(details: String) = "Remote server rejected push: $details"
    override val mcpHintSshUnsupported =
        "SSH keys are not supported in PocketWorker. Use an HTTPS repository URL and Personal Access Token."
    override fun mcpConfirmGitForcePush(cmd: String) =
        "DANGEROUS: force push to remote server: $cmd"
    override fun mcpConfirmGitPush(cmd: String) = "Push changes to remote server: $cmd"
    override fun mcpConfirmGitResetHard(cmd: String) =
        "DANGEROUS: hard reset (local changes will be lost): $cmd"
    override fun mcpConfirmGitClean(cmd: String) = "DANGEROUS: delete untracked files: $cmd"
    override fun mcpConfirmGitRestore(cmd: String) =
        "DANGEROUS: discard uncommitted file changes: $cmd"
    override fun mcpConfirmGitDeleteBranch(cmd: String) = "Delete branch: $cmd"
    override fun mcpConfirmGitStashDelete(cmd: String) = "DANGEROUS: delete saved stash: $cmd"
    override fun mcpConfirmGitCommand(cmd: String) = "Run Git command: $cmd"

    // ── Git OAuth (GitHub / GitLab / Self-hosted) ───────────────────────────────────────────
    override val oauthAccountsTitle = "Accounts (OAuth)"
    override val oauthAccountsEmptyHint =
        "Sign in with GitHub or GitLab — credentials are selected automatically by repository host."
    override val oauthSignInGitHub = "Sign in with GitHub"
    override val oauthSignInGitLab = "Sign in with GitLab"
    override val oauthDefaultForHostBadge = "Default"
    override val oauthMakeDefaultForHost = "Set as default for host"
    override val oauthDisconnectAccountDesc = "Disconnect account"
    override fun oauthSignedInInfo(label: String) = "Signed in: $label"
    override fun oauthSignedInWithAuthorInfo(label: String) =
        "Signed in: $label (commit author filled)"
    override fun oauthAccountRemovedInfo(label: String) = "Account disconnected: $label"
    override fun oauthHostDefaultUpdatedInfo(label: String) = "Default account updated: $label"
    override val oauthProjectAccountBoundInfo = "Account linked to project"
    override val oauthRepoAccountBoundInfo = "Account linked to repository"

    override val oauthGitHubDialogTitle = "Sign in with GitHub"
    override val oauthGitHubDialogSubtitle = "Device code authorization (Device Flow)"
    override val oauthGitHubClientIdLabel = "GitHub OAuth Client ID"
    override val oauthGitHubClientIdHint =
        "Enter the Client ID of your GitHub OAuth App (with Enable Device Flow checked)."
    override val oauthGitHubStartButton = "Get sign-in code"
    override val oauthGitHubCodeLabel = "One-time code for github.com/login/device"
    override val oauthGitHubCopyAndOpenButton = "Copy code & open GitHub"
    override val oauthGitHubCodeCopiedInfo = "Code copied to clipboard"
    override val oauthGitHubWaitingHint = "Waiting for browser authorization…"
    override val oauthGitHubChangeClientId = "Change Client ID"

    override val oauthGitLabDialogTitle = "Sign in with GitLab"
    override val oauthGitLabDialogSubtitle = "OAuth PKCE with automatic token refresh"
    override val oauthGitLabModeCloud = "gitlab.com"
    override val oauthGitLabModeSelfHosted = "Self-hosted"
    override val oauthGitLabServerUrlLabel = "GitLab server URL"
    override val oauthGitLabServerUrlPlaceholder = "https://gitlab.company.com"
    override val oauthGitLabClientIdLabel = "Application ID (Client ID)"
    override val oauthGitLabSetupHint =
        "In GitLab Application settings, uncheck Confidential and enable scopes: read_user, write_repository, read_api."
    override val oauthGitLabRedirectUriLabel = "Redirect URI"
    override val oauthGitLabCopyRedirectUriDesc = "Copy Redirect URI"
    override val oauthGitLabRedirectUriCopiedInfo = "Redirect URI copied"
    override val oauthGitLabOpenBrowserButton = "Open GitLab in browser"
    override val oauthGitLabWaitingHint =
        "Finish signing in in the browser — you will return to the app automatically."
    override val oauthGitLabExchangingHint = "Completing sign-in and loading profile…"

    override val oauthScopedModeAccount = "Account"
    override val oauthScopedModeManual = "Manual (PAT)"
    override val oauthScopedNoAccountsHint =
        "No connected OAuth accounts yet. Sign in with GitHub or GitLab below."
    override val oauthScopedAddAccountTitle = "Connect a new account"
    override val oauthScopedClearOverride = "Reset"
    override val oauthCloneUsingConnectedAccountsHint =
        "Leave the token field empty to automatically use your connected OAuth account for this host."

    override val oauthErrClientIdRequired = "Enter a Client ID (Application ID)."
    override val oauthErrInvalidServerUrl = "Enter a valid server URL (e.g., https://gitlab.company.com)."
    override val oauthErrBrowserUnavailable = "Unable to open a browser on this device."
    override val oauthErrDeviceExpired = "The verification code expired. Request a new code."
    override val oauthErrAccessDenied = "Authorization was cancelled by the user."
    override val oauthErrStateMismatch = "OAuth security state mismatch. Please try signing in again."
    override val oauthErrMissingCode = "The server did not return an authorization code."
    override fun oauthErrRequestFailed(details: String) = "OAuth error: $details"
    override fun oauthErrProfileFailed(details: String) = "Failed to load profile: $details"
}
