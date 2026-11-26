package com.pocketworker.app.ui.workspace

import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketworker.app.ui.common.formatFileSize
import com.pocketworker.app.ui.theme.LocalIsDarkTheme
import kotlin.math.abs
import kotlin.math.roundToInt

internal const val MIN_EDITOR_ZOOM = 0.65f
internal const val MAX_EDITOR_ZOOM = 2.40f
private const val BASE_CODE_FONT_SP = 13f
private const val BASE_GUTTER_FONT_SP = 11.5f
private const val BASE_LINE_HEIGHT_SP = 20f

internal fun applyEditorZoom(currentScale: Float, zoomFactor: Float): Float {
    if (!zoomFactor.isFinite() || zoomFactor <= 0f) {
        return currentScale.coerceIn(MIN_EDITOR_ZOOM, MAX_EDITOR_ZOOM)
    }
    return (currentScale * zoomFactor).coerceIn(MIN_EDITOR_ZOOM, MAX_EDITOR_ZOOM)
}

internal fun formatEditorZoomPercent(scale: Float): String {
    return "${(scale.coerceIn(MIN_EDITOR_ZOOM, MAX_EDITOR_ZOOM) * 100f).roundToInt()}%"
}

internal fun buildLineNumbersText(lineCount: Int): String {
    val safeCount = lineCount.coerceAtLeast(1)
    return buildString(safeCount * 5) {
        for (lineNum in 1..safeCount) {
            if (lineNum > 1) append('\n')
            append(lineNum)
        }
    }
}

/**
 * Multi-touch-only pinch-to-zoom detector running on [PointerEventPass.Initial].
 *
 * Single-finger gestures (vertical/horizontal scroll, taps, long-press text selection,
 * selection handle drags) are never consumed and work natively without interference.
 * As soon as 2+ pointers are down, pinch zoom is tracked and consumed to avoid scroll jitter.
 */
private fun Modifier.pinchToZoom(
    onZoomFactor: (Float) -> Unit
): Modifier = composed {
    val latestOnZoom by rememberUpdatedState(onZoomFactor)
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var zoomAccum = 1f
            var pastTouchSlop = false
            var pinchedInGesture = false
            val touchSlop = viewConfiguration.touchSlop

            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressedCount = event.changes.count { it.pressed }
                if (pressedCount >= 2) {
                    pinchedInGesture = true
                    val zoomChange = event.calculateZoom()
                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    if (!pastTouchSlop) {
                        zoomAccum *= zoomChange
                        val zoomMotion = abs(1f - zoomAccum) * centroidSize
                        if (zoomMotion > touchSlop) {
                            pastTouchSlop = true
                        }
                    }
                    if (pastTouchSlop && zoomChange != 1f) {
                        latestOnZoom(zoomChange)
                    }
                    event.changes.forEach { change ->
                        if (change.positionChanged()) {
                            change.consume()
                        }
                    }
                } else if (pinchedInGesture) {
                    // Consume trailing single-finger release motion after a pinch to prevent sudden scroll jumps
                    event.changes.forEach { change ->
                        if (change.positionChanged()) {
                            change.consume()
                        }
                    }
                }
            } while (event.changes.any { it.pressed })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileEditorScreen(
    editorState: FileEditorState,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val language = remember(editorState.relativePath) {
        CodeSyntaxHighlighter.detectLanguage(editorState.relativePath)
    }
    val palette = remember(isDark) { VsCodePalettes.forTheme(isDark) }

    var isEditing by remember { mutableStateOf(false) }
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var zoomScale by remember(editorState.relativePath) { mutableFloatStateOf(1f) }

    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()

    val primaryColor = MaterialTheme.colorScheme.primary
    val selectionColors = remember(primaryColor, isDark) {
        TextSelectionColors(
            handleColor = primaryColor,
            backgroundColor = primaryColor.copy(alpha = if (isDark) 0.36f else 0.26f)
        )
    }

    val handleBack = {
        if (editorState.isModifiedInEditor) {
            showUnsavedDialog = true
        } else {
            onClose()
        }
    }

    BackHandler(onBack = handleBack)

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = editorState.relativePath.substringAfterLast('/'),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = language.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                            Text(
                                text = buildString {
                                    append(editorState.relativePath)
                                    if (editorState.isModifiedInEditor) append(" • изменён")
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    if (!editorState.isBinary && !editorState.isTooLarge) {
                        IconButton(onClick = { isEditing = !isEditing }) {
                            Icon(
                                imageVector = if (isEditing) Icons.Rounded.Visibility else Icons.Rounded.Edit,
                                contentDescription = if (isEditing) "Режим просмотра" else "Редактировать"
                            )
                        }
                        if (editorState.isModifiedInEditor) {
                            IconButton(
                                onClick = onSave,
                                enabled = !editorState.isSaving
                            ) {
                                Icon(Icons.Rounded.Save, contentDescription = "Сохранить")
                            }
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Настройки")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            HorizontalDivider(color = palette.gutterDivider)

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.editorBackground)
                    .pinchToZoom { factor ->
                        zoomScale = applyEditorZoom(zoomScale, factor)
                    }
            ) {
                val viewportHeight = maxHeight
                val viewportWidth = maxWidth

                when {
                    editorState.isBinary -> {
                        Text(
                            text = "Бинарный файл (${formatFileSize(editorState.sizeBytes)}) не поддерживается текстовым редактором.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp)
                        )
                    }

                    editorState.isTooLarge -> {
                        Text(
                            text = "Файл слишком большой (${formatFileSize(editorState.sizeBytes)}) для открытия в редакторе.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp)
                        )
                    }

                    else -> {
                        val normalizedViewText = remember(editorState.content) {
                            editorState.content.replace("\r\n", "\n").replace("\r", "\n")
                        }
                        val lineCount = remember(normalizedViewText) {
                            normalizedViewText.count { it == '\n' } + 1
                        }
                        val lineNumbersText = remember(lineCount) {
                            buildLineNumbersText(lineCount)
                        }
                        val gutterDigits = lineCount.toString().length.coerceAtLeast(3)
                        val gutterWidth = ((gutterDigits * 8.5f * zoomScale) + 18f).dp
                        val codeMinWidth = (viewportWidth - gutterWidth).coerceAtLeast(0.dp)

                        val lineHeightStyle = remember {
                            LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.None
                            )
                        }
                        val codeFontSize = (BASE_CODE_FONT_SP * zoomScale).sp
                        val gutterFontSize = (BASE_GUTTER_FONT_SP * zoomScale).sp
                        val lineHeight = (BASE_LINE_HEIGHT_SP * zoomScale).sp

                        val gutterTextStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = gutterFontSize,
                            lineHeight = lineHeight,
                            lineHeightStyle = lineHeightStyle,
                            color = palette.lineNumber,
                            textAlign = TextAlign.End
                        )
                        val codeTextStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = codeFontSize,
                            lineHeight = lineHeight,
                            lineHeightStyle = lineHeightStyle,
                            color = palette.plainText
                        )

                        CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .drawBehind {
                                        val gutterPx = gutterWidth.toPx()
                                        drawRect(
                                            color = palette.gutterBackground,
                                            topLeft = Offset.Zero,
                                            size = Size(gutterPx, size.height)
                                        )
                                        drawLine(
                                            color = palette.gutterDivider,
                                            start = Offset(gutterPx, 0f),
                                            end = Offset(gutterPx, size.height),
                                            strokeWidth = 1.dp.toPx()
                                        )
                                    }
                                    .verticalScroll(vScroll)
                                    .heightIn(min = viewportHeight)
                            ) {
                                // Pinned left line-number gutter (single Text for exact line alignment at any zoom)
                                Text(
                                    text = lineNumbersText,
                                    style = gutterTextStyle,
                                    softWrap = false,
                                    modifier = Modifier
                                        .width(gutterWidth)
                                        .padding(horizontal = 8.dp, vertical = 12.dp)
                                )

                                if (isEditing) {
                                    val visualTransformation = remember(language, isDark) {
                                        CodeSyntaxHighlighter.visualTransformation(language, isDark)
                                    }
                                    BasicTextField(
                                        value = editorState.content,
                                        onValueChange = onContentChange,
                                        visualTransformation = visualTransformation,
                                        textStyle = codeTextStyle,
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = viewportHeight)
                                            .horizontalScroll(hScroll)
                                            .widthIn(min = codeMinWidth)
                                            .padding(horizontal = 12.dp, vertical = 12.dp)
                                    )
                                } else {
                                    val highlightedText = remember(normalizedViewText, language, isDark) {
                                        CodeSyntaxHighlighter.highlight(normalizedViewText, language, isDark)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = viewportHeight)
                                            .horizontalScroll(hScroll)
                                    ) {
                                        SelectionContainer {
                                            Text(
                                                text = if (highlightedText.isEmpty()) {
                                                    AnnotatedString(" ")
                                                } else {
                                                    highlightedText
                                                },
                                                style = codeTextStyle,
                                                softWrap = false,
                                                modifier = Modifier
                                                    .widthIn(min = codeMinWidth)
                                                    .padding(horizontal = 12.dp, vertical = 12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Subtle zoom indicator badge at top-end (avoids floating AI orb and IME keyboard; tap to reset)
                        androidx.compose.animation.AnimatedVisibility(
                            visible = abs(zoomScale - 1f) > 0.03f,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 10.dp, end = 12.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                shape = RoundedCornerShape(999.dp),
                                tonalElevation = 4.dp,
                                shadowElevation = 4.dp,
                                modifier = Modifier.clickable { zoomScale = 1f }
                            ) {
                                Text(
                                    text = "${formatEditorZoomPercent(zoomScale)}  •  сброс",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text("Несохранённые изменения") },
            text = { Text("Сохранить изменения в файле «${editorState.relativePath}» перед выходом?") },
            confirmButton = {
                Button(onClick = {
                    showUnsavedDialog = false
                    onSave()
                    onClose()
                }) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showUnsavedDialog = false
                    onClose()
                }) {
                    Text("Выйти без сохранения")
                }
            }
        )
    }
}
