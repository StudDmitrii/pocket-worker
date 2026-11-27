package com.pocketworker.app.ui.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pocketworker.app.core.i18n.LocalAppStrings
import com.pocketworker.app.ui.theme.GitSemanticColors
import com.pocketworker.app.ui.theme.LocalIsDarkTheme
import com.pocketworker.app.ui.workspace.CodeLanguage
import com.pocketworker.app.ui.workspace.CodeSyntaxHighlighter
import com.pocketworker.app.ui.workspace.VsCodePalettes
import kotlinx.coroutines.delay

/**
 * Rich Markdown renderer for AI chat responses with live streaming support,
 * VS Code syntax-highlighted code blocks, copy buttons, GFM tables, callouts,
 * checklists, nested lists, headings, and clickable links.
 */
@Composable
fun AiMarkdownText(
    markdown: String,
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier
) {
    val blocks = remember(markdown) { AiMarkdownParser.parse(markdown) }
    val isDark = LocalIsDarkTheme.current
    val uriHandler = LocalUriHandler.current
    val palette = rememberMarkdownInlinePalette(isDark)

    if (blocks.isEmpty()) {
        if (isStreaming) {
            Text(
                text = "▍",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = modifier
            )
        }
        return
    }

    MarkdownBlocksColumn(
        blocks = blocks,
        palette = palette,
        isDark = isDark,
        uriHandler = uriHandler,
        appendStreamingCursor = isStreaming,
        verticalSpacing = 8.dp,
        modifier = modifier
    )
}

@Composable
private fun MarkdownBlocksColumn(
    blocks: List<MdBlock>,
    palette: MarkdownInlinePalette,
    isDark: Boolean,
    uriHandler: UriHandler,
    appendStreamingCursor: Boolean,
    verticalSpacing: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(verticalSpacing)
    ) {
        blocks.forEachIndexed { index, block ->
            val isLast = index == blocks.lastIndex
            val showCursorHere = appendStreamingCursor && isLast
            when (block) {
                is MdBlock.Heading -> MarkdownHeadingView(
                    block = block,
                    palette = palette,
                    uriHandler = uriHandler,
                    appendCursor = showCursorHere
                )

                is MdBlock.Paragraph -> MarkdownParagraphView(
                    inlines = block.inlines,
                    palette = palette,
                    uriHandler = uriHandler,
                    appendCursor = showCursorHere
                )

                is MdBlock.CodeBlock -> MarkdownCodeBlockCard(
                    block = block,
                    isDark = isDark,
                    appendCursor = showCursorHere
                )

                is MdBlock.ListBlock -> MarkdownListView(
                    block = block,
                    palette = palette,
                    isDark = isDark,
                    uriHandler = uriHandler,
                    appendCursor = showCursorHere
                )

                is MdBlock.Blockquote -> MarkdownBlockquoteView(
                    block = block,
                    palette = palette,
                    isDark = isDark,
                    uriHandler = uriHandler,
                    appendCursor = showCursorHere
                )

                is MdBlock.Table -> {
                    MarkdownTableView(
                        table = block,
                        palette = palette,
                        isDark = isDark,
                        uriHandler = uriHandler
                    )
                    if (showCursorHere) {
                        Text(
                            text = "▍",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.cursorColor
                        )
                    }
                }

                MdBlock.HorizontalRule -> {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
                        modifier = Modifier.padding(vertical = 3.dp)
                    )
                    if (showCursorHere) {
                        Text(
                            text = "▍",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.cursorColor
                        )
                    }
                }
            }
        }
    }
}

private data class MarkdownInlinePalette(
    val textColor: Color,
    val mutedTextColor: Color,
    val inlineCodeText: Color,
    val inlineCodeBackground: Color,
    val linkColor: Color,
    val cursorColor: Color
)

@Composable
private fun rememberMarkdownInlinePalette(isDark: Boolean): MarkdownInlinePalette {
    val colorScheme = MaterialTheme.colorScheme
    return remember(isDark, colorScheme) {
        MarkdownInlinePalette(
            textColor = colorScheme.onSurface,
            mutedTextColor = colorScheme.onSurfaceVariant,
            inlineCodeText = if (isDark) Color(0xFF7DD3FC) else Color(0xFF0369A1),
            inlineCodeBackground = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0),
            linkColor = colorScheme.primary,
            cursorColor = colorScheme.primary
        )
    }
}

@Composable
private fun MarkdownHeadingView(
    block: MdBlock.Heading,
    palette: MarkdownInlinePalette,
    uriHandler: UriHandler,
    appendCursor: Boolean
) {
    val baseStyle = when (block.level) {
        1 -> MaterialTheme.typography.titleLarge.copy(
            fontSize = 19.sp,
            lineHeight = 25.sp,
            fontWeight = FontWeight.Bold
        )
        2 -> MaterialTheme.typography.titleMedium.copy(
            fontSize = 17.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.Bold
        )
        3 -> MaterialTheme.typography.titleSmall.copy(
            fontSize = 15.5.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.SemiBold
        )
        4 -> MaterialTheme.typography.bodyLarge.copy(
            fontSize = 14.5.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
        else -> MaterialTheme.typography.bodyMedium.copy(
            fontSize = 14.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
    val headingColor = if (block.level >= 5) palette.mutedTextColor else palette.textColor
    val annotated = remember(block.inlines, palette, headingColor, appendCursor) {
        buildMarkdownAnnotatedString(
            inlines = block.inlines,
            palette = palette.copy(textColor = headingColor),
            uriHandler = uriHandler,
            appendCursor = appendCursor
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (block.level <= 2) 4.dp else 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = annotated,
            style = baseStyle,
            color = headingColor
        )
        if (block.level <= 2) {
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
            )
        }
    }
}

@Composable
private fun MarkdownParagraphView(
    inlines: List<MdInline>,
    palette: MarkdownInlinePalette,
    uriHandler: UriHandler,
    appendCursor: Boolean,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
    textAlign: TextAlign = TextAlign.Start,
    modifier: Modifier = Modifier
) {
    val annotated = remember(inlines, palette, appendCursor) {
        buildMarkdownAnnotatedString(
            inlines = inlines,
            palette = palette,
            uriHandler = uriHandler,
            appendCursor = appendCursor
        )
    }
    Text(
        text = annotated,
        style = textStyle,
        color = palette.textColor,
        textAlign = textAlign,
        modifier = modifier
    )
}

@Composable
private fun MarkdownCodeBlockCard(
    block: MdBlock.CodeBlock,
    isDark: Boolean,
    appendCursor: Boolean
) {
    val s = LocalAppStrings.current
    val clipboardManager = LocalClipboardManager.current
    var copied by remember(block.code) { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1800L)
            copied = false
        }
    }

    val vsPalette = remember(isDark) { VsCodePalettes.forTheme(isDark) }
    val codeBg = if (isDark) Color(0xFF0D1320) else Color(0xFFF8FAFC)
    val headerBg = if (isDark) Color(0xFF172033) else Color(0xFFEFF4F9)
    val borderColor = if (isDark) Color(0xFF28354E) else Color(0xFFD6E0EC)

    val rawLang = block.language.trim()
    val lowerLang = rawLang.lowercase()
    val isDiff = lowerLang == "diff" || lowerLang == "patch"
    val detectedLanguage = remember(rawLang) {
        CodeSyntaxHighlighter.detectLanguageByFence(rawLang)
    }
    val languageLabel = remember(rawLang, detectedLanguage, isDiff, s) {
        when {
            isDiff -> "Diff"
            detectedLanguage != CodeLanguage.PLAIN_TEXT -> detectedLanguage.displayName
            rawLang.isNotEmpty() -> rawLang
            else -> s.mdCodeDefaultLabel
        }
    }

    val highlightedCode = remember(block.code, detectedLanguage, isDiff, isDark, appendCursor) {
        val base = when {
            isDiff -> highlightDiffBlock(block.code, isDark, vsPalette.plainText)
            else -> CodeSyntaxHighlighter.highlight(block.code, detectedLanguage, isDark)
        }
        if (appendCursor) {
            buildAnnotatedString {
                append(base)
                withStyle(SpanStyle(color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0369A1), fontWeight = FontWeight.Bold)) {
                    append("▍")
                }
            }
        } else {
            base
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = codeBg,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            DisableSelection {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(headerBg)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = languageLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(enabled = block.code.isNotEmpty()) {
                                clipboardManager.setText(AnnotatedString(block.code))
                                copied = true
                            }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        val copyTint = if (copied) {
                            if (isDark) GitSemanticColors.AddedDark else GitSemanticColors.AddedLight
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Icon(
                            imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                            contentDescription = s.mdCopyCodeDesc,
                            tint = copyTint,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (copied) s.commonCopied else s.commonCopy,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = copyTint
                        )
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = borderColor.copy(alpha = 0.7f))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = highlightedCode,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        color = vsPalette.plainText
                    )
                )
            }
        }
    }
}

private fun highlightDiffBlock(code: String, isDark: Boolean, defaultColor: Color): AnnotatedString {
    val addedColor = if (isDark) GitSemanticColors.AddedDark else GitSemanticColors.AddedLight
    val deletedColor = if (isDark) GitSemanticColors.DeletedDark else GitSemanticColors.DeletedLight
    val hunkColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF0369A1)
    val metaColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569)

    return buildAnnotatedString {
        val lines = code.split('\n')
        lines.forEachIndexed { idx, line ->
            val style = when {
                line.startsWith("+++") || line.startsWith("---") || line.startsWith("diff ") || line.startsWith("index ") ->
                    SpanStyle(color = metaColor, fontWeight = FontWeight.SemiBold)
                line.startsWith("@@") ->
                    SpanStyle(color = hunkColor, fontWeight = FontWeight.Medium)
                line.startsWith("+") ->
                    SpanStyle(color = addedColor)
                line.startsWith("-") ->
                    SpanStyle(color = deletedColor)
                else ->
                    SpanStyle(color = defaultColor)
            }
            withStyle(style) { append(line) }
            if (idx < lines.lastIndex) append('\n')
        }
    }
}

@Composable
private fun MarkdownListView(
    block: MdBlock.ListBlock,
    palette: MarkdownInlinePalette,
    isDark: Boolean,
    uriHandler: UriHandler,
    appendCursor: Boolean
) {
    val s = LocalAppStrings.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        block.items.forEachIndexed { idx, item ->
            val showCursorHere = appendCursor && idx == block.items.lastIndex
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = (item.indentLevel * 14).dp)
            ) {
                DisableSelection {
                    when (val marker = item.marker) {
                        MdListMarker.Bullet -> {
                            val bulletGlyph = when (item.indentLevel % 3) {
                                0 -> "•"
                                1 -> "◦"
                                else -> "▪"
                            }
                            Text(
                                text = bulletGlyph,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    lineHeight = 21.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (item.indentLevel == 0) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.widthIn(min = 12.dp)
                            )
                        }

                        is MdListMarker.Ordered -> {
                            Text(
                                text = "${marker.number}.",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    lineHeight = 21.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.widthIn(min = 20.dp)
                            )
                        }

                        is MdListMarker.Task -> {
                            Icon(
                                imageVector = if (marker.checked) {
                                    Icons.Rounded.CheckBox
                                } else {
                                    Icons.Rounded.CheckBoxOutlineBlank
                                },
                                contentDescription = if (marker.checked) s.commonDone else s.commonNotDone,
                                tint = if (marker.checked) {
                                    if (isDark) GitSemanticColors.AddedDark else GitSemanticColors.AddedLight
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(16.dp)
                            )
                        }
                    }
                }

                val itemPalette = if (item.marker is MdListMarker.Task && item.marker.checked) {
                    palette.copy(textColor = palette.mutedTextColor)
                } else {
                    palette
                }
                MarkdownParagraphView(
                    inlines = item.inlines,
                    palette = itemPalette,
                    uriHandler = uriHandler,
                    appendCursor = showCursorHere,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MarkdownBlockquoteView(
    block: MdBlock.Blockquote,
    palette: MarkdownInlinePalette,
    isDark: Boolean,
    uriHandler: UriHandler,
    appendCursor: Boolean
) {
    val s = LocalAppStrings.current
    val accentColor = when (block.callout) {
        MdCalloutType.NOTE -> MaterialTheme.colorScheme.primary
        MdCalloutType.TIP -> if (isDark) GitSemanticColors.AddedDark else GitSemanticColors.AddedLight
        MdCalloutType.IMPORTANT -> if (isDark) GitSemanticColors.ConflictedDark else GitSemanticColors.ConflictedLight
        MdCalloutType.WARNING -> if (isDark) GitSemanticColors.ModifiedDark else GitSemanticColors.ModifiedLight
        MdCalloutType.CAUTION -> MaterialTheme.colorScheme.error
        null -> MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
    }
    val calloutIcon: ImageVector? = when (block.callout) {
        MdCalloutType.NOTE -> Icons.Rounded.Info
        MdCalloutType.TIP -> Icons.Rounded.Lightbulb
        MdCalloutType.IMPORTANT -> Icons.Rounded.PriorityHigh
        MdCalloutType.WARNING -> Icons.Rounded.WarningAmber
        MdCalloutType.CAUTION -> Icons.Rounded.ErrorOutline
        null -> null
    }
    val containerBg = if (block.callout != null) {
        accentColor.copy(alpha = if (isDark) 0.12f else 0.08f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.45f else 0.55f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 10.dp, bottomEnd = 10.dp))
            .background(containerBg)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(accentColor)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (block.callout != null && calloutIcon != null) {
                DisableSelection {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = calloutIcon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = block.callout.localizedTitle(s),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor
                        )
                    }
                }
            }
            if (block.blocks.isNotEmpty()) {
                val quotePalette = if (block.callout == null) {
                    palette.copy(textColor = palette.textColor.copy(alpha = 0.90f))
                } else {
                    palette
                }
                MarkdownBlocksColumn(
                    blocks = block.blocks,
                    palette = quotePalette,
                    isDark = isDark,
                    uriHandler = uriHandler,
                    appendStreamingCursor = appendCursor,
                    verticalSpacing = 5.dp
                )
            } else if (appendCursor) {
                Text(
                    text = "▍",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.cursorColor
                )
            }
        }
    }
}

@Composable
private fun MarkdownTableView(
    table: MdBlock.Table,
    palette: MarkdownInlinePalette,
    isDark: Boolean,
    uriHandler: UriHandler
) {
    val colCount = table.headers.size
    if (colCount == 0) return

    val totalRows = 1 + table.rows.size
    val borderColor = if (isDark) Color(0xFF28354E) else Color(0xFFD6E0EC)
    val headerBg = if (isDark) Color(0xFF172033) else Color(0xFFEFF4F9)
    val zebraBg = if (isDark) Color(0xFF101829) else Color(0xFFF8FAFC)
    val bodyBg = if (isDark) Color(0xFF0C1220) else Color(0xFFFFFFFF)

    val density = LocalDensity.current
    val minColPx = with(density) { 72.dp.roundToPx() }
    val maxColPx = with(density) { 220.dp.roundToPx() }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bodyBg,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            val metrics = remember { TableLayoutMetrics() }

            Layout(
                modifier = Modifier.drawBehind {
                    if (metrics.colWidths.size != colCount || metrics.rowHeights.size != totalRows) return@drawBehind
                    val strokePx = 1.dp.toPx()
                    val totalWidthPx = size.width
                    val totalHeightPx = size.height

                    // Row backgrounds (header + subtle zebra striping)
                    var y = 0f
                    for (r in 0 until totalRows) {
                        val h = metrics.rowHeights[r].toFloat()
                        val rowFill = when {
                            r == 0 -> headerBg
                            r % 2 == 0 -> zebraBg
                            else -> Color.Transparent
                        }
                        if (rowFill != Color.Transparent) {
                            drawRect(
                                color = rowFill,
                                topLeft = Offset(0f, y),
                                size = Size(totalWidthPx, h)
                            )
                        }
                        if (r > 0) {
                            drawLine(
                                color = borderColor.copy(alpha = if (r == 1) 0.95f else 0.55f),
                                start = Offset(0f, y),
                                end = Offset(totalWidthPx, y),
                                strokeWidth = strokePx
                            )
                        }
                        y += h
                    }

                    // Vertical column separators
                    var x = 0f
                    for (c in 0 until colCount - 1) {
                        x += metrics.colWidths[c].toFloat()
                        drawLine(
                            color = borderColor.copy(alpha = 0.5f),
                            start = Offset(x, 0f),
                            end = Offset(x, totalHeightPx),
                            strokeWidth = strokePx
                        )
                    }
                },
                content = {
                    // Header row cells
                    table.headers.forEachIndexed { colIdx, cellInlines ->
                        val align = table.alignments.getOrElse(colIdx) { MdColumnAlignment.LEFT }
                        MarkdownTableCell(
                            inlines = cellInlines,
                            alignment = align,
                            isHeader = true,
                            palette = palette,
                            uriHandler = uriHandler
                        )
                    }
                    // Body rows cells
                    table.rows.forEach { row ->
                        for (colIdx in 0 until colCount) {
                            val align = table.alignments.getOrElse(colIdx) { MdColumnAlignment.LEFT }
                            val cellInlines = row.getOrElse(colIdx) { emptyList() }
                            MarkdownTableCell(
                                inlines = cellInlines,
                                alignment = align,
                                isHeader = false,
                                palette = palette,
                                uriHandler = uriHandler
                            )
                        }
                    }
                }
            ) { measurables, constraints ->
                val colWidths = IntArray(colCount) { minColPx }
                for (r in 0 until totalRows) {
                    for (c in 0 until colCount) {
                        val idx = r * colCount + c
                        if (idx < measurables.size) {
                            val natural = measurables[idx].maxIntrinsicWidth(Constraints.Infinity)
                            val clamped = natural.coerceIn(minColPx, maxColPx)
                            if (clamped > colWidths[c]) {
                                colWidths[c] = clamped
                            }
                        }
                    }
                }

                // Expand evenly if total table width is narrower than available viewport width
                val rawTotalWidth = colWidths.sum()
                val minTableWidth = if (constraints.hasBoundedWidth) {
                    constraints.minWidth.coerceAtMost(constraints.maxWidth)
                } else {
                    0
                }
                if (rawTotalWidth < minTableWidth && colCount > 0) {
                    val extraPerCol = (minTableWidth - rawTotalWidth) / colCount
                    for (c in 0 until colCount) {
                        colWidths[c] += extraPerCol
                    }
                }

                val placeables = Array(measurables.size) { idx ->
                    val c = idx % colCount
                    measurables[idx].measure(
                        Constraints(
                            minWidth = colWidths[c],
                            maxWidth = colWidths[c],
                            minHeight = 0,
                            maxHeight = Constraints.Infinity
                        )
                    )
                }

                val rowHeights = IntArray(totalRows)
                for (r in 0 until totalRows) {
                    var maxH = 0
                    for (c in 0 until colCount) {
                        val idx = r * colCount + c
                        if (idx < placeables.size && placeables[idx].height > maxH) {
                            maxH = placeables[idx].height
                        }
                    }
                    rowHeights[r] = maxH
                }

                metrics.colWidths = colWidths
                metrics.rowHeights = rowHeights

                val layoutWidth = colWidths.sum()
                val layoutHeight = rowHeights.sum()
                layout(layoutWidth, layoutHeight) {
                    var y = 0
                    for (r in 0 until totalRows) {
                        var x = 0
                        for (c in 0 until colCount) {
                            val idx = r * colCount + c
                            if (idx < placeables.size) {
                                placeables[idx].placeRelative(x, y)
                            }
                            x += colWidths[c]
                        }
                        y += rowHeights[r]
                    }
                }
            }
        }
    }
}

private class TableLayoutMetrics(
    var colWidths: IntArray = IntArray(0),
    var rowHeights: IntArray = IntArray(0)
)

@Composable
private fun MarkdownTableCell(
    inlines: List<MdInline>,
    alignment: MdColumnAlignment,
    isHeader: Boolean,
    palette: MarkdownInlinePalette,
    uriHandler: UriHandler
) {
    val textAlign = when (alignment) {
        MdColumnAlignment.LEFT -> TextAlign.Start
        MdColumnAlignment.CENTER -> TextAlign.Center
        MdColumnAlignment.RIGHT -> TextAlign.End
    }
    val boxAlign = when (alignment) {
        MdColumnAlignment.LEFT -> Alignment.CenterStart
        MdColumnAlignment.CENTER -> Alignment.Center
        MdColumnAlignment.RIGHT -> Alignment.CenterEnd
    }
    val cellStyle = if (isHeader) {
        MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
            lineHeight = 18.sp
        )
    } else {
        MaterialTheme.typography.bodySmall.copy(
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }

    Box(
        contentAlignment = boxAlign,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        MarkdownParagraphView(
            inlines = inlines,
            palette = palette,
            uriHandler = uriHandler,
            appendCursor = false,
            textStyle = cellStyle,
            textAlign = textAlign
        )
    }
}

private fun buildMarkdownAnnotatedString(
    inlines: List<MdInline>,
    palette: MarkdownInlinePalette,
    uriHandler: UriHandler,
    appendCursor: Boolean
): AnnotatedString = buildAnnotatedString {
    appendMarkdownInlines(inlines, palette, uriHandler)
    if (appendCursor) {
        withStyle(SpanStyle(color = palette.cursorColor, fontWeight = FontWeight.Bold)) {
            append("▍")
        }
    }
}

private fun AnnotatedString.Builder.appendMarkdownInlines(
    inlines: List<MdInline>,
    palette: MarkdownInlinePalette,
    uriHandler: UriHandler
) {
    for (node in inlines) {
        when (node) {
            is MdInline.Text -> append(node.text)
            MdInline.LineBreak -> append('\n')
            is MdInline.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                appendMarkdownInlines(node.children, palette, uriHandler)
            }
            is MdInline.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                appendMarkdownInlines(node.children, palette, uriHandler)
            }
            is MdInline.BoldItalic -> withStyle(
                SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
            ) {
                appendMarkdownInlines(node.children, palette, uriHandler)
            }
            is MdInline.Strikethrough -> withStyle(
                SpanStyle(
                    textDecoration = TextDecoration.LineThrough,
                    color = palette.textColor.copy(alpha = 0.62f)
                )
            ) {
                appendMarkdownInlines(node.children, palette, uriHandler)
            }
            is MdInline.InlineCode -> withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 0.90.em,
                    color = palette.inlineCodeText,
                    background = palette.inlineCodeBackground
                )
            ) {
                append(node.code)
            }
            is MdInline.Link -> {
                val linkStyle = SpanStyle(
                    color = palette.linkColor,
                    textDecoration = TextDecoration.Underline
                )
                val safeUrl = node.url.trim()
                val isOpenable = safeUrl.startsWith("https://", ignoreCase = true) ||
                    safeUrl.startsWith("http://", ignoreCase = true) ||
                    safeUrl.startsWith("mailto:", ignoreCase = true)
                if (isOpenable) {
                    withLink(
                        LinkAnnotation.Url(
                            url = safeUrl,
                            styles = TextLinkStyles(style = linkStyle),
                            linkInteractionListener = {
                                runCatching { uriHandler.openUri(safeUrl) }
                            }
                        )
                    ) {
                        appendMarkdownInlines(node.label, palette, uriHandler)
                    }
                } else {
                    withStyle(linkStyle) {
                        appendMarkdownInlines(node.label, palette, uriHandler)
                    }
                }
            }
        }
    }
}
