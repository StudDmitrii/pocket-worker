package com.pocketworker.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Commit
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketworker.app.core.ai.ChangedFileSummary
import com.pocketworker.app.core.git.ProjectSummary
import com.pocketworker.app.core.git.RepoCardInfo
import com.pocketworker.app.core.git.RepoEntry
import com.pocketworker.app.core.git.WorkspaceProject
import com.pocketworker.app.core.i18n.AppStrings
import com.pocketworker.app.core.i18n.LocalAppStrings
import com.pocketworker.app.core.storage.AiProvider
import com.pocketworker.app.ui.ai.ActionsGroupCard
import com.pocketworker.app.ui.ai.AiChatMessageItem
import com.pocketworker.app.ui.ai.AiMarkdownText
import com.pocketworker.app.ui.ai.ChangedFilesCardSection
import com.pocketworker.app.ui.ai.IridescentAiOrb
import com.pocketworker.app.ui.ai.ToolActivityCategory
import com.pocketworker.app.ui.ai.ToolActivityStatus
import com.pocketworker.app.ui.home.WorkspaceProjectCard
import com.pocketworker.app.ui.projectdetail.RepoCard
import com.pocketworker.app.ui.settings.SettingsActionRow
import com.pocketworker.app.ui.theme.PocketWorkerTheme
import com.pocketworker.app.ui.workspace.CodeLanguage
import com.pocketworker.app.ui.workspace.CodeSyntaxHighlighter
import com.pocketworker.app.ui.workspace.VsCodePalettes
import kotlinx.coroutines.launch

private data class GuideSlide(
    val title: String,
    val description: String,
    val bullets: List<String>,
    val preview: @Composable () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingGuideScreen(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = LocalAppStrings.current
    // Always render the interactive slide guide on a clean, bright light canvas
    PocketWorkerTheme(darkTheme = false) {
        val slides = remember(s) { buildGuideSlides(s) }
        val pagerState = rememberPagerState(initialPage = 0, pageCount = { slides.size })
        val coroutineScope = rememberCoroutineScope()
        val isFirstPage = pagerState.currentPage == 0
        val isLastPage = pagerState.currentPage == slides.lastIndex

        BackHandler {
            if (pagerState.currentPage > 0) {
                coroutineScope.launch {
                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                }
            } else {
                onFinish()
            }
        }

        val pageBg = Color(0xFFF4F8FC)
        val dividerColor = Color(0xFFD8E3EE)

        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = pageBg,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.White,
                            scrolledContainerColor = Color.White,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                            actionIconContentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.MenuBook,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = s.onboardingTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = s.onboardingSlideCounter(pagerState.currentPage + 1, slides.size),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        actions = {
                            TextButton(onClick = onFinish) {
                                Text(
                                    text = s.commonSkip,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    )
                    HorizontalDivider(color = dividerColor)
                }
            },
            bottomBar = {
                Surface(
                    color = Color.White,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shadowElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                    ) {
                        HorizontalDivider(color = dividerColor)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Slide indicator dots
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                repeat(slides.size) { index ->
                                    val selected = index == pagerState.currentPage
                                    val dotWidth by animateDpAsState(
                                        targetValue = if (selected) 24.dp else 8.dp,
                                        label = "guide_dot_width"
                                    )
                                    val dotColor by animateColorAsState(
                                        targetValue = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            Color(0xFFCBD5E1)
                                        },
                                        label = "guide_dot_color"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .padding(horizontal = 3.dp)
                                            .height(8.dp)
                                            .width(dotWidth)
                                            .clip(CircleShape)
                                            .background(dotColor)
                                    )
                                }
                            }

                            // Back / Next navigation arrows
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (!isFirstPage) {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                            }
                                        }
                                    },
                                    enabled = !isFirstPage,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = s.commonBack,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Button(
                                    onClick = {
                                        if (isLastPage) {
                                            onFinish()
                                        } else {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .heightIn(min = 48.dp)
                                ) {
                                    Text(
                                        text = if (isLastPage) s.commonStartWorking else s.commonNext,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = if (isLastPage) {
                                            Icons.Rounded.Check
                                        } else {
                                            Icons.AutoMirrored.Rounded.ArrowForward
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) { padding ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) { pageIndex ->
                val slide = slides[pageIndex]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header text
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = slide.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = slide.description,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 21.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Clean light showcase container holding non-clickable real app blocks
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFEAF2FA),
                        border = BorderStroke(1.dp, Color(0xFFCEDCEB)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            slide.preview()
                        }
                    }

                    // Short practical bullets
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            slide.bullets.forEach { bullet ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 7.dp)
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                    Text(
                                        text = bullet,
                                        style = MaterialTheme.typography.bodyMedium,
                                        lineHeight = 20.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildGuideSlides(s: AppStrings): List<GuideSlide> = listOf(
    GuideSlide(
        title = s.onboardingSlide1Title,
        description = s.onboardingSlide1Desc,
        bullets = listOf(s.onboardingSlide1Bullet1, s.onboardingSlide1Bullet2),
        preview = { ProjectsSlidePreview() }
    ),
    GuideSlide(
        title = s.onboardingSlide2Title,
        description = s.onboardingSlide2Desc,
        bullets = listOf(s.onboardingSlide2Bullet1, s.onboardingSlide2Bullet2),
        preview = { RepositoriesSlidePreview() }
    ),
    GuideSlide(
        title = s.onboardingSlide3Title,
        description = s.onboardingSlide3Desc,
        bullets = listOf(s.onboardingSlide3Bullet1, s.onboardingSlide3Bullet2),
        preview = { EditorAndGitSlidePreview() }
    ),
    GuideSlide(
        title = s.onboardingSlide4Title,
        description = s.onboardingSlide4Desc,
        bullets = listOf(s.onboardingSlide4Bullet1, s.onboardingSlide4Bullet2),
        preview = { AiAssistantSlidePreview() }
    ),
    GuideSlide(
        title = s.onboardingSlide5Title,
        description = s.onboardingSlide5Desc,
        bullets = listOf(s.onboardingSlide5Bullet1, s.onboardingSlide5Bullet2),
        preview = { ActionsAndFilesSlidePreview() }
    ),
    GuideSlide(
        title = s.onboardingSlide6Title,
        description = s.onboardingSlide6Desc,
        bullets = listOf(s.onboardingSlide6Bullet1, s.onboardingSlide6Bullet2),
        preview = { SettingsSlidePreview() }
    )
)

@Composable
private fun ProjectsSlidePreview() {
    val s = LocalAppStrings.current
    val sampleSummary = remember(s) {
        ProjectSummary(
            project = WorkspaceProject(
                id = "demo_proj",
                name = "Mobile App",
                directoryPath = "/demo/mobile-app",
                createdAt = 0L,
                description = s.onboardingMockProjectDesc
            ),
            repoCount = 2,
            repoNamesPreview = listOf("android-client", "api-server"),
            dirtyRepoCount = 1
        )
    }

    WorkspaceProjectCard(
        summary = sampleSummary,
        isDark = false,
        onClick = {},
        onEditClick = {},
        onDeleteClick = {},
        interactive = false
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(14.dp),
            shadowElevation = 2.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = s.homeNewProject,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun RepositoriesSlidePreview() {
    val s = LocalAppStrings.current
    val sampleRepo = remember(s) {
        RepoCardInfo(
            entry = RepoEntry(
                id = "demo_repo",
                projectId = "demo_proj",
                name = "android-client",
                directoryPath = "/demo/mobile-app/android-client",
                createdAt = 0L,
                remoteUrl = "https://github.com/team/android-client.git"
            ),
            isGitRepo = true,
            currentBranch = "main",
            isUnborn = false,
            changedFilesCount = 2,
            untrackedCount = 0,
            aheadCount = 1,
            behindCount = 0,
            lastCommitMessage = s.onboardingMockCommitMsg,
            lastCommitTime = System.currentTimeMillis() - 600_000L,
            remoteUrl = "https://github.com/team/android-client.git"
        )
    }

    RepoCard(
        info = sampleRepo,
        isDark = false,
        onClick = {},
        onDeleteClick = {},
        interactive = false
    )
}

@Composable
private fun EditorAndGitSlidePreview() {
    val s = LocalAppStrings.current
    val sampleCode = remember(s) {
        """
        fun greetUser(name: String): String {
            val clean = name.trim().ifEmpty { "Dev" }
            return "${s.onboardingMockGreetingCode}, ${'$'}clean!"
        }
        """.trimIndent()
    }
    val highlighted = remember(sampleCode) {
        CodeSyntaxHighlighter.highlight(sampleCode, CodeLanguage.KOTLIN, isDark = false)
    }
    val vsLight = VsCodePalettes.Light

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFD5E0EC)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF1F5F9))
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(
                    text = "Greeting.kt",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Kotlin",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            HorizontalDivider(color = Color(0xFFE2E8F0))
            Text(
                text = highlighted,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = vsLight.plainText
                ),
                modifier = Modifier.padding(12.dp)
            )
        }
    }

    // Compact non-clickable Git controls row
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StaticActionPill(
            icon = Icons.Rounded.AccountTree,
            label = "main",
            highlighted = false,
            modifier = Modifier.weight(1f)
        )
        StaticActionPill(
            icon = Icons.Rounded.Commit,
            label = "Commit",
            highlighted = true,
            modifier = Modifier.weight(1f)
        )
        StaticActionPill(
            icon = Icons.Rounded.CloudUpload,
            label = "Push",
            highlighted = false,
            modifier = Modifier.weight(1f)
        )
        StaticActionPill(
            icon = Icons.Rounded.CloudDownload,
            label = "Pull",
            highlighted = false,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StaticActionPill(
    icon: ImageVector,
    label: String,
    highlighted: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (highlighted) MaterialTheme.colorScheme.primary else Color.White,
        contentColor = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        border = if (highlighted) null else BorderStroke(1.dp, Color(0xFFCBD5E1)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AiAssistantSlidePreview() {
    val s = LocalAppStrings.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IridescentAiOrb(
            size = 50.dp,
            label = "AI",
            textSize = 14.sp,
            glowIntensity = 0.55f
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = s.onboardingMockAiButtonTitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = s.onboardingMockAiButtonSubtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // Real assistant reply bubble rendered with AiMarkdownText
    Surface(
        shape = RoundedCornerShape(
            topStart = 4.dp,
            topEnd = 16.dp,
            bottomStart = 16.dp,
            bottomEnd = 16.dp
        ),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFD5E0EC)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Claude Sonnet 5.5",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            AiMarkdownText(
                markdown = s.onboardingMockAiResponse,
                isStreaming = false
            )
        }
    }
}

@Composable
private fun ActionsAndFilesSlidePreview() {
    val s = LocalAppStrings.current
    val sampleGroup = remember(s) {
        AiChatMessageItem.ActionsGroup(
            id = "demo_actions",
            turnId = "demo_turn",
            actions = listOf(
                AiChatMessageItem.ToolActivity(
                    id = "a1",
                    toolCallId = "c1",
                    toolName = "fs_find_files",
                    category = ToolActivityCategory.SEARCHING,
                    title = s.onboardingMockActionSearch,
                    subtitle = "*Auth*",
                    status = ToolActivityStatus.SUCCEEDED
                ),
                AiChatMessageItem.ToolActivity(
                    id = "a2",
                    toolCallId = "c2",
                    toolName = "fs_read_file",
                    category = ToolActivityCategory.READING,
                    title = s.onboardingMockActionRead,
                    subtitle = "AuthService.kt",
                    status = ToolActivityStatus.SUCCEEDED
                ),
                AiChatMessageItem.ToolActivity(
                    id = "a3",
                    toolCallId = "c3",
                    toolName = "fs_edit_file",
                    category = ToolActivityCategory.DOING,
                    title = s.onboardingMockActionUpdated,
                    subtitle = "AuthService.kt",
                    status = ToolActivityStatus.SUCCEEDED
                )
            )
        )
    }

    val sampleChangedFiles = remember {
        AiChatMessageItem.ChangedFilesCard(
            id = "demo_files",
            turnId = "demo_turn",
            files = listOf(
                ChangedFileSummary(
                    projectId = "demo_proj",
                    repoId = "demo_repo",
                    repoName = "android-client",
                    relativePath = "src/auth/AuthService.kt",
                    addedLines = 18,
                    deletedLines = 4
                )
            )
        )
    }

    ActionsGroupCard(
        item = sampleGroup,
        interactive = false
    )

    ChangedFilesCardSection(
        item = sampleChangedFiles,
        onFileClick = {},
        interactive = false
    )
}

@Composable
private fun SettingsSlidePreview() {
    val s = LocalAppStrings.current
    val providers = remember(s) { AiProvider.entries.map { s.providerDisplayName(it) } }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFD5E0EC)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = s.onboardingMockAiProviderLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                providers.chunked(3).forEachIndexed { rowIdx, rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowItems.forEachIndexed { itemIdx, label ->
                            val selected = rowIdx == 0 && itemIdx == 1
                            Surface(
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    Color(0xFFF1F5F9)
                                },
                                border = if (selected) {
                                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                } else null,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp)
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(thickness = 1.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))

            SettingsActionRow(
                icon = Icons.Rounded.Key,
                title = s.settingsDefaultTokenTitle,
                subtitle = s.onboardingMockTokenSaved,
                onClick = {},
                interactive = false
            )

            SettingsActionRow(
                icon = Icons.Rounded.Person,
                title = s.settingsDefaultAuthorTitle,
                subtitle = "Ivan Dev <ivan@example.com>",
                onClick = {},
                interactive = false
            )
        }
    }
}
