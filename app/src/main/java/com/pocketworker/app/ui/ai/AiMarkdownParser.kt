package com.pocketworker.app.ui.ai

/**
 * Pure-Kotlin Markdown AST and parser tailored for AI chat responses (including live streaming).
 *
 * Supports:
 * - ATX headings (`#`..`######`)
 * - Fenced code blocks (``` / ~~~, including unclosed streaming fences and nested backticks)
 * - GFM pipe tables with column alignments and inline formatting inside cells
 * - Blockquotes and GitHub-style callouts (`> [!NOTE]`, `[!TIP]`, `[!IMPORTANT]`, `[!WARNING]`, `[!CAUTION]`)
 * - Unordered, ordered, task checklist (`- [ ]` / `- [x]`), and mixed nested lists
 * - Horizontal rules (`---`, `***`, `___`)
 * - Inline bold, italic, bold+italic, strikethrough, inline code, links, bare URLs, escapes, `<br>`
 */

enum class MdColumnAlignment {
    LEFT,
    CENTER,
    RIGHT
}

enum class MdCalloutType(val title: String) {
    NOTE("Заметка"),
    TIP("Совет"),
    IMPORTANT("Важно"),
    WARNING("Внимание"),
    CAUTION("Осторожно")
}

sealed interface MdListMarker {
    data object Bullet : MdListMarker
    data class Ordered(val number: Int) : MdListMarker
    data class Task(val checked: Boolean) : MdListMarker
}

data class MdListItem(
    val marker: MdListMarker,
    val indentLevel: Int,
    val inlines: List<MdInline>
)

sealed interface MdBlock {
    data class Heading(
        val level: Int,
        val inlines: List<MdInline>
    ) : MdBlock

    data class Paragraph(
        val inlines: List<MdInline>
    ) : MdBlock

    data class CodeBlock(
        val language: String,
        val code: String,
        val isClosed: Boolean = true
    ) : MdBlock

    data class ListBlock(
        val items: List<MdListItem>
    ) : MdBlock

    data class Blockquote(
        val callout: MdCalloutType? = null,
        val blocks: List<MdBlock>
    ) : MdBlock

    data class Table(
        val headers: List<List<MdInline>>,
        val alignments: List<MdColumnAlignment>,
        val rows: List<List<List<MdInline>>>
    ) : MdBlock

    data object HorizontalRule : MdBlock
}

sealed interface MdInline {
    data class Text(val text: String) : MdInline
    data class Bold(val children: List<MdInline>) : MdInline
    data class Italic(val children: List<MdInline>) : MdInline
    data class BoldItalic(val children: List<MdInline>) : MdInline
    data class Strikethrough(val children: List<MdInline>) : MdInline
    data class InlineCode(val code: String) : MdInline
    data class Link(val label: List<MdInline>, val url: String) : MdInline
    data object LineBreak : MdInline
}

object AiMarkdownParser {
    private const val MAX_BLOCK_DEPTH = 4
    private const val MAX_INLINE_DEPTH = 6

    private val HEADING_REGEX = Regex("^\\s{0,3}(#{1,6})\\s+(.+?)\\s*#*$")
    private val HR_REGEX = Regex("^\\s{0,3}(?:(?:\\*\\s*){3,}|(?:-\\s*){3,}|(?:_\\s*){3,})$")
    private val BACKTICK_FENCE_REGEX = Regex("^(\\s*)(`{3,})([^`]*)$")
    private val TILDE_FENCE_REGEX = Regex("^(\\s*)(~{3,})(.*)$")
    private val BLOCKQUOTE_REGEX = Regex("^\\s{0,3}>\\s?(.*)$")
    private val CALLOUT_REGEX = Regex("^\\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)]\\s*$", RegexOption.IGNORE_CASE)
    private val BULLET_LIST_REGEX = Regex("^(\\s*)([-*+])\\s+(.*)$")
    private val ORDERED_LIST_REGEX = Regex("^(\\s*)(\\d{1,4})[.)]\\s+(.*)$")
    private val TASK_PREFIX_REGEX = Regex("^\\[([ xX])]\\s+(.*)$")
    private val TABLE_SEPARATOR_CELL_REGEX = Regex("^:?-{3,}:?$")
    private val HTML_BR_REGEX = Regex("^<br\\s*/?>", RegexOption.IGNORE_CASE)

    fun parse(markdown: String): List<MdBlock> {
        if (markdown.isEmpty()) return emptyList()
        val normalized = markdown.replace("\r\n", "\n").replace("\r", "\n")
        return parseBlocks(normalized.split('\n'), depth = 0)
    }

    private fun parseBlocks(lines: List<String>, depth: Int): List<MdBlock> {
        val blocks = mutableListOf<MdBlock>()
        var i = 0
        val n = lines.size

        while (i < n) {
            val line = lines[i]
            if (line.isBlank()) {
                i++
                continue
            }

            // 1. Fenced code block (``` or ~~~)
            val fenceOpen = matchOpeningFence(line)
            if (fenceOpen != null) {
                val (indentLen, fenceChar, fenceLen, language) = fenceOpen
                i++
                val codeLines = mutableListOf<String>()
                var closed = false
                while (i < n) {
                    val candidate = lines[i]
                    if (isClosingFence(candidate, fenceChar, fenceLen)) {
                        closed = true
                        i++
                        break
                    }
                    val unindented = if (indentLen > 0) stripLeadingSpaces(candidate, indentLen) else candidate
                    codeLines.add(unindented)
                    i++
                }
                blocks.add(
                    MdBlock.CodeBlock(
                        language = language,
                        code = codeLines.joinToString("\n"),
                        isClosed = closed
                    )
                )
                continue
            }

            // 2. ATX Heading (# .. ######)
            val headingMatch = HEADING_REGEX.matchEntire(line)
            if (headingMatch != null) {
                val level = headingMatch.groupValues[1].length
                val content = headingMatch.groupValues[2].trim()
                blocks.add(MdBlock.Heading(level = level, inlines = parseInlines(content)))
                i++
                continue
            }

            // 3. Horizontal rule (---, ***, ___) — check before list items so `* * *` isn't a bullet
            if (HR_REGEX.matches(line)) {
                blocks.add(MdBlock.HorizontalRule)
                i++
                continue
            }

            // 4. Blockquote / GitHub Callout (> ...)
            if (BLOCKQUOTE_REGEX.matches(line)) {
                val quoteLines = mutableListOf<String>()
                while (i < n) {
                    val qMatch = BLOCKQUOTE_REGEX.matchEntire(lines[i]) ?: break
                    quoteLines.add(qMatch.groupValues[1])
                    i++
                }
                var callout: MdCalloutType? = null
                val contentLines = if (quoteLines.isNotEmpty()) {
                    val calloutMatch = CALLOUT_REGEX.matchEntire(quoteLines.first().trim())
                    if (calloutMatch != null) {
                        callout = when (calloutMatch.groupValues[1].uppercase()) {
                            "NOTE" -> MdCalloutType.NOTE
                            "TIP" -> MdCalloutType.TIP
                            "IMPORTANT" -> MdCalloutType.IMPORTANT
                            "WARNING" -> MdCalloutType.WARNING
                            "CAUTION" -> MdCalloutType.CAUTION
                            else -> null
                        }
                        quoteLines.drop(1)
                    } else {
                        quoteLines
                    }
                } else {
                    quoteLines
                }

                val innerBlocks = if (depth < MAX_BLOCK_DEPTH) {
                    parseBlocks(contentLines, depth + 1)
                } else {
                    val raw = contentLines.joinToString("\n").trim()
                    if (raw.isNotEmpty()) listOf(MdBlock.Paragraph(parseInlines(raw))) else emptyList()
                }
                if (innerBlocks.isNotEmpty() || callout != null) {
                    blocks.add(MdBlock.Blockquote(callout = callout, blocks = innerBlocks))
                }
                continue
            }

            // 5. GFM Table (header row + separator row + 0..N body rows)
            if (i + 1 < n && isTableStart(line, lines[i + 1])) {
                val headerCells = splitTableRow(line)
                val alignments = parseTableAlignments(lines[i + 1], headerCells.size)
                i += 2
                val bodyRows = mutableListOf<List<List<MdInline>>>()
                while (i < n && isTableBodyLine(lines[i])) {
                    val rawCells = splitTableRow(lines[i])
                    val normalizedCells = List(headerCells.size) { colIdx ->
                        parseInlines(rawCells.getOrElse(colIdx) { "" })
                    }
                    bodyRows.add(normalizedCells)
                    i++
                }
                blocks.add(
                    MdBlock.Table(
                        headers = headerCells.map { parseInlines(it) },
                        alignments = alignments,
                        rows = bodyRows
                    )
                )
                continue
            }

            // 6. List (unordered, ordered, task checklist, mixed nested)
            if (matchListItem(line) != null) {
                val items = mutableListOf<MdListItem>()
                while (i < n) {
                    val itemLine = lines[i]
                    if (itemLine.isBlank()) {
                        // Allow a single blank line between list items if the next non-blank line continues the list
                        if (i + 1 < n && matchListItem(lines[i + 1]) != null) {
                            i++
                            continue
                        }
                        break
                    }
                    if (HR_REGEX.matches(itemLine)) break
                    if (matchOpeningFence(itemLine) != null) break

                    val parsedItem = matchListItem(itemLine)
                    if (parsedItem != null) {
                        val contentBuilder = StringBuilder(parsedItem.content)
                        i++
                        // Absorb indented soft continuation lines belonging to this list item
                        while (i < n) {
                            val next = lines[i]
                            if (next.isBlank()) break
                            if (matchListItem(next) != null) break
                            if (matchOpeningFence(next) != null) break
                            if (HEADING_REGEX.matches(next) || HR_REGEX.matches(next) || BLOCKQUOTE_REGEX.matches(next)) break
                            if (i + 1 < n && isTableStart(next, lines[i + 1])) break
                            if (countLeadingSpaces(next) >= 2) {
                                contentBuilder.append('\n').append(next.trim())
                                i++
                            } else {
                                break
                            }
                        }
                        items.add(
                            MdListItem(
                                marker = parsedItem.marker,
                                indentLevel = parsedItem.indentLevel,
                                inlines = parseInlines(contentBuilder.toString())
                            )
                        )
                    } else {
                        break
                    }
                }
                if (items.isNotEmpty()) {
                    blocks.add(MdBlock.ListBlock(items))
                }
                continue
            }

            // 7. Paragraph (collect until blank line or start of another block construct)
            val paraLines = mutableListOf<String>()
            while (i < n) {
                val current = lines[i]
                if (current.isBlank()) break
                if (paraLines.isNotEmpty()) {
                    if (matchOpeningFence(current) != null ||
                        HEADING_REGEX.matches(current) ||
                        HR_REGEX.matches(current) ||
                        BLOCKQUOTE_REGEX.matches(current) ||
                        matchListItem(current) != null ||
                        (i + 1 < n && isTableStart(current, lines[i + 1]))
                    ) {
                        break
                    }
                }
                paraLines.add(current.trimEnd())
                i++
            }
            if (paraLines.isNotEmpty()) {
                val joined = paraLines.joinToString("\n")
                blocks.add(MdBlock.Paragraph(parseInlines(joined)))
            }
        }

        return blocks
    }

    private data class FenceOpen(
        val indentLen: Int,
        val fenceChar: Char,
        val fenceLen: Int,
        val language: String
    )

    private fun matchOpeningFence(line: String): FenceOpen? {
        val bt = BACKTICK_FENCE_REGEX.matchEntire(line)
        if (bt != null) {
            val indent = countLeadingSpaces(bt.groupValues[1])
            val marker = bt.groupValues[2]
            val info = bt.groupValues[3].trim()
            val lang = info.substringBefore(' ').trim()
            return FenceOpen(indent, '`', marker.length, lang)
        }
        val tilde = TILDE_FENCE_REGEX.matchEntire(line)
        if (tilde != null) {
            val indent = countLeadingSpaces(tilde.groupValues[1])
            val marker = tilde.groupValues[2]
            val info = tilde.groupValues[3].trim()
            val lang = info.substringBefore(' ').trim()
            return FenceOpen(indent, '~', marker.length, lang)
        }
        return null
    }

    private fun isClosingFence(line: String, fenceChar: Char, minFenceLen: Int): Boolean {
        val trimmed = line.trim()
        if (trimmed.length < minFenceLen) return false
        return trimmed.all { it == fenceChar }
    }

    private data class ParsedListLine(
        val marker: MdListMarker,
        val indentLevel: Int,
        val content: String
    )

    private fun matchListItem(line: String): ParsedListLine? {
        if (HR_REGEX.matches(line)) return null
        val bullet = BULLET_LIST_REGEX.matchEntire(line)
        if (bullet != null) {
            val indentSpaces = countLeadingSpaces(bullet.groupValues[1])
            val indentLevel = (indentSpaces / 2).coerceIn(0, 4)
            val rawContent = bullet.groupValues[3]
            val taskMatch = TASK_PREFIX_REGEX.matchEntire(rawContent)
            return if (taskMatch != null) {
                val checked = taskMatch.groupValues[1].equals("x", ignoreCase = true)
                ParsedListLine(MdListMarker.Task(checked), indentLevel, taskMatch.groupValues[2])
            } else {
                ParsedListLine(MdListMarker.Bullet, indentLevel, rawContent)
            }
        }
        val ordered = ORDERED_LIST_REGEX.matchEntire(line)
        if (ordered != null) {
            val indentSpaces = countLeadingSpaces(ordered.groupValues[1])
            val indentLevel = (indentSpaces / 2).coerceIn(0, 4)
            val num = ordered.groupValues[2].toIntOrNull() ?: 1
            return ParsedListLine(MdListMarker.Ordered(num), indentLevel, ordered.groupValues[3])
        }
        return null
    }

    private fun isTableStart(headerLine: String, separatorLine: String): Boolean {
        if (!headerLine.contains('|') || !separatorLine.contains('-')) return false
        val headerCells = splitTableRow(headerLine)
        if (headerCells.isEmpty()) return false
        val sepCells = splitTableRow(separatorLine)
        if (sepCells.isEmpty()) return false
        // Require at least 2 columns OR explicit outer pipes so plain text with `-` isn't mistaken for a table
        if (headerCells.size < 2 && !headerLine.trim().startsWith('|')) return false
        return sepCells.all { TABLE_SEPARATOR_CELL_REGEX.matches(it.trim()) }
    }

    private fun isTableBodyLine(line: String): Boolean {
        if (line.isBlank()) return false
        if (!line.contains('|')) return false
        if (matchOpeningFence(line) != null || HEADING_REGEX.matches(line) || BLOCKQUOTE_REGEX.matches(line)) {
            return false
        }
        return true
    }

    private fun parseTableAlignments(separatorLine: String, colCount: Int): List<MdColumnAlignment> {
        val cells = splitTableRow(separatorLine)
        return List(colCount) { idx ->
            val token = cells.getOrElse(idx) { "---" }.trim()
            val left = token.startsWith(':')
            val right = token.endsWith(':')
            when {
                left && right -> MdColumnAlignment.CENTER
                right -> MdColumnAlignment.RIGHT
                else -> MdColumnAlignment.LEFT
            }
        }
    }

    internal fun splitTableRow(line: String): List<String> {
        var trimmed = line.trim()
        if (trimmed.startsWith('|')) trimmed = trimmed.substring(1)
        if (trimmed.endsWith('|') && !trimmed.endsWith("\\|")) {
            trimmed = trimmed.substring(0, trimmed.length - 1)
        }
        if (trimmed.isEmpty()) return emptyList()

        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        var inBackticks = false
        while (i < trimmed.length) {
            val c = trimmed[i]
            when {
                c == '\\' && i + 1 < trimmed.length && trimmed[i + 1] == '|' -> {
                    current.append('|')
                    i += 2
                }
                c == '`' -> {
                    inBackticks = !inBackticks
                    current.append(c)
                    i++
                }
                c == '|' && !inBackticks -> {
                    cells.add(current.toString().trim())
                    current.setLength(0)
                    i++
                }
                else -> {
                    current.append(c)
                    i++
                }
            }
        }
        cells.add(current.toString().trim())
        return cells
    }

    private fun countLeadingSpaces(s: String): Int {
        var count = 0
        for (ch in s) {
            when (ch) {
                ' ' -> count++
                '\t' -> count += 2
                else -> break
            }
        }
        return count
    }

    private fun stripLeadingSpaces(line: String, maxSpaces: Int): String {
        var removed = 0
        var idx = 0
        while (idx < line.length && removed < maxSpaces) {
            when (line[idx]) {
                ' ' -> {
                    removed++
                    idx++
                }
                '\t' -> {
                    removed += 2
                    idx++
                }
                else -> break
            }
        }
        return line.substring(idx)
    }

    // region Inline Parsing

    internal fun parseInlines(text: String, depth: Int = 0): List<MdInline> {
        if (text.isEmpty()) return emptyList()
        if (depth >= MAX_INLINE_DEPTH) return listOf(MdInline.Text(text))

        val out = mutableListOf<MdInline>()
        val buf = StringBuilder()

        fun flushBuf() {
            if (buf.isNotEmpty()) {
                out.add(MdInline.Text(buf.toString()))
                buf.setLength(0)
            }
        }

        val len = text.length
        var i = 0

        while (i < len) {
            val c = text[i]

            // 1. Explicit newline
            if (c == '\n') {
                flushBuf()
                out.add(MdInline.LineBreak)
                i++
                continue
            }

            // 2. HTML <br> / <br/>
            if (c == '<') {
                val brMatch = HTML_BR_REGEX.find(text.substring(i))
                if (brMatch != null) {
                    flushBuf()
                    out.add(MdInline.LineBreak)
                    i += brMatch.value.length
                    continue
                }
            }

            // 3. Backslash escape
            if (c == '\\' && i + 1 < len && isEscapablePunctuation(text[i + 1])) {
                buf.append(text[i + 1])
                i += 2
                continue
            }

            // 4. Inline code span (`...` or ``...``)
            if (c == '`') {
                var tickCount = 0
                while (i + tickCount < len && text[i + tickCount] == '`') tickCount++
                val closeIdx = findClosingBackticks(text, i + tickCount, tickCount)
                if (closeIdx != -1) {
                    flushBuf()
                    var rawCode = text.substring(i + tickCount, closeIdx).replace('\n', ' ')
                    if (rawCode.length >= 2 && rawCode.startsWith(' ') && rawCode.endsWith(' ') && rawCode.any { it != ' ' }) {
                        rawCode = rawCode.substring(1, rawCode.length - 1)
                    }
                    out.add(MdInline.InlineCode(rawCode))
                    i = closeIdx + tickCount
                    continue
                } else {
                    buf.append(text.substring(i, i + tickCount))
                    i += tickCount
                    continue
                }
            }

            // 5. Markdown Link [label](url) or Image ![alt](url)
            if (c == '[' || (c == '!' && i + 1 < len && text[i + 1] == '[')) {
                val isImage = c == '!'
                val bracketStart = if (isImage) i + 1 else i
                val parsedLink = tryParseLink(text, bracketStart, depth)
                if (parsedLink != null) {
                    flushBuf()
                    out.add(parsedLink.link)
                    i = parsedLink.nextIndex
                    continue
                }
            }

            // 6. Bare HTTP(S) autolink
            if (c == 'h' && (text.startsWith("https://", i) || text.startsWith("http://", i)) &&
                (i == 0 || isAutolinkLeftBoundary(text[i - 1]))
            ) {
                val urlEnd = scanAutolinkEnd(text, i)
                val rawUrl = text.substring(i, urlEnd)
                val cleanUrl = trimTrailingUrlPunctuation(rawUrl)
                val protoLen = if (cleanUrl.startsWith("https://")) 8 else 7
                if (cleanUrl.length > protoLen) {
                    flushBuf()
                    out.add(MdInline.Link(listOf(MdInline.Text(cleanUrl)), cleanUrl))
                    i += cleanUrl.length
                    continue
                }
            }

            // 7. Strikethrough ~~...~~
            if (c == '~' && i + 1 < len && text[i + 1] == '~') {
                val closeIdx = findClosingDelim(text, i + 2, '~', 2, requireWordBoundary = false)
                if (closeIdx != -1) {
                    flushBuf()
                    val inner = text.substring(i + 2, closeIdx)
                    out.add(MdInline.Strikethrough(parseInlines(inner, depth + 1)))
                    i = closeIdx + 2
                    continue
                }
            }

            // 8. Emphasis: *** / ___ , ** / __ , * / _
            if (c == '*' || c == '_') {
                val requireWordBoundary = (c == '_')
                val wordBoundaryOk = !requireWordBoundary || i == 0 || !text[i - 1].isLetterOrDigit()
                if (wordBoundaryOk) {
                    // Try triple (*** / ___), then double (** / __), then single (* / _)
                    var matched = false
                    for (dLen in intArrayOf(3, 2, 1)) {
                        if (hasExactOrMoreChars(text, i, c, dLen)) {
                            // For dLen=2 when run is 3, try 3 first (loop order guarantees 3 is tried before 2 & 1)
                            val closeIdx = findClosingDelim(text, i + dLen, c, dLen, requireWordBoundary)
                            if (closeIdx != -1) {
                                flushBuf()
                                val inner = text.substring(i + dLen, closeIdx)
                                val children = parseInlines(inner, depth + 1)
                                out.add(
                                    when (dLen) {
                                        3 -> MdInline.BoldItalic(children)
                                        2 -> MdInline.Bold(children)
                                        else -> MdInline.Italic(children)
                                    }
                                )
                                i = closeIdx + dLen
                                matched = true
                                break
                            }
                        }
                    }
                    if (matched) continue
                }
            }

            buf.append(c)
            i++
        }

        flushBuf()
        return out
    }

    private fun isEscapablePunctuation(c: Char): Boolean {
        return c in "\\`*_{}[]()#+-.!~|>"
    }

    private fun findClosingBackticks(text: String, start: Int, count: Int): Int {
        var i = start
        val len = text.length
        while (i < len) {
            if (text[i] == '`') {
                var run = 0
                while (i + run < len && text[i + run] == '`') run++
                if (run == count) return i
                i += run
            } else {
                i++
            }
        }
        return -1
    }

    private data class ParsedLinkResult(
        val link: MdInline.Link,
        val nextIndex: Int
    )

    private fun tryParseLink(text: String, openBracketIdx: Int, depth: Int): ParsedLinkResult? {
        val len = text.length
        var i = openBracketIdx + 1
        var bracketDepth = 1
        while (i < len && bracketDepth > 0) {
            val ch = text[i]
            when {
                ch == '\\' && i + 1 < len -> i += 2
                ch == '`' -> {
                    var tickCount = 0
                    while (i + tickCount < len && text[i + tickCount] == '`') tickCount++
                    val close = findClosingBackticks(text, i + tickCount, tickCount)
                    i = if (close != -1) close + tickCount else i + tickCount
                }
                ch == '[' -> {
                    bracketDepth++
                    i++
                }
                ch == ']' -> {
                    bracketDepth--
                    if (bracketDepth == 0) break
                    i++
                }
                ch == '\n' -> return null
                else -> i++
            }
        }
        if (bracketDepth != 0 || i >= len) return null
        val closeBracketIdx = i
        if (closeBracketIdx + 1 >= len || text[closeBracketIdx + 1] != '(') return null

        val urlStart = closeBracketIdx + 2
        var j = urlStart
        var parenDepth = 1
        while (j < len && parenDepth > 0) {
            val ch = text[j]
            when {
                ch == '\\' && j + 1 < len -> j += 2
                ch == '(' -> {
                    parenDepth++
                    j++
                }
                ch == ')' -> {
                    parenDepth--
                    if (parenDepth == 0) break
                    j++
                }
                ch == '\n' -> return null
                else -> j++
            }
        }
        if (parenDepth != 0 || j >= len) return null

        val labelRaw = text.substring(openBracketIdx + 1, closeBracketIdx)
        val rawDest = text.substring(urlStart, j).trim()
        // Strip optional title after whitespace (e.g. `https://example.com "Title"`)
        val urlToken = if (rawDest.startsWith('<') && rawDest.contains('>')) {
            rawDest.substring(1, rawDest.indexOf('>')).trim()
        } else {
            rawDest.substringBefore(' ').trim()
        }
        if (urlToken.isEmpty()) return null

        val labelInlines = if (labelRaw.isNotBlank()) {
            parseInlines(labelRaw, depth + 1)
        } else {
            listOf(MdInline.Text(urlToken))
        }
        return ParsedLinkResult(
            link = MdInline.Link(label = labelInlines, url = urlToken),
            nextIndex = j + 1
        )
    }

    private fun isAutolinkLeftBoundary(prev: Char): Boolean {
        return prev.isWhitespace() || prev in "([<:\u2014\u2013\"'"
    }

    private fun scanAutolinkEnd(text: String, start: Int): Int {
        var i = start
        val len = text.length
        while (i < len) {
            val ch = text[i]
            if (ch.isWhitespace() || ch in "<>\"'") break
            i++
        }
        return i
    }

    private fun trimTrailingUrlPunctuation(url: String): String {
        var s = url
        while (s.isNotEmpty()) {
            val last = s.last()
            when {
                last in ".,;:!?]}" -> s = s.dropLast(1)
                last == ')' && s.count { it == ')' } > s.count { it == '(' } -> s = s.dropLast(1)
                else -> return s
            }
        }
        return s
    }

    private fun hasExactOrMoreChars(text: String, start: Int, ch: Char, count: Int): Boolean {
        if (start + count > text.length) return false
        for (k in 0 until count) {
            if (text[start + k] != ch) return false
        }
        return true
    }

    private fun findClosingDelim(
        text: String,
        contentStart: Int,
        delimChar: Char,
        delimLen: Int,
        requireWordBoundary: Boolean
    ): Int {
        val len = text.length
        if (contentStart >= len) return -1
        // Disallow whitespace immediately after opening delimiter (prevents `2 * 3 * 4` becoming italic)
        if (text[contentStart].isWhitespace()) return -1
        // Disallow immediately repeating the same delimiter char unless it opens a nested `**` inside `*`
        if (text[contentStart] == delimChar) {
            val isNestedBoldInsideItalic = delimLen == 1 &&
                contentStart + 2 < len &&
                text[contentStart + 1] == delimChar &&
                !text[contentStart + 2].isWhitespace() &&
                text[contentStart + 2] != delimChar
            if (!isNestedBoldInsideItalic) return -1
        }

        var i = contentStart
        while (i < len) {
            val ch = text[i]
            if (ch == '\\' && i + 1 < len) {
                i += 2
                continue
            }
            if (ch == '`') {
                var tickCount = 0
                while (i + tickCount < len && text[i + tickCount] == '`') tickCount++
                val close = findClosingBackticks(text, i + tickCount, tickCount)
                i = if (close != -1) close + tickCount else i + tickCount
                continue
            }
            if (ch == delimChar) {
                var runLen = 0
                while (i + runLen < len && text[i + runLen] == delimChar) runLen++

                // If looking for single `*`/`_`, skip exact `**`/`__` runs so `*a **b**` doesn't close prematurely
                val canClose = when (delimLen) {
                    1 -> runLen == 1 || runLen >= 3
                    2 -> runLen >= 2
                    else -> runLen >= delimLen
                }
                if (canClose && i > contentStart && !text[i - 1].isWhitespace()) {
                    // When runLen > delimLen (e.g. `*hello **world***` closing with last `*` of `***`),
                    // align to the trailing end of the run so inner bold keeps its `**`.
                    val closePos = if (runLen > delimLen) i + runLen - delimLen else i
                    val afterClose = closePos + delimLen
                    val rightBoundaryOk = !requireWordBoundary ||
                        afterClose >= len ||
                        !text[afterClose].isLetterOrDigit()
                    if (rightBoundaryOk && closePos > contentStart) {
                        return closePos
                    }
                }
                i += runLen
                continue
            }
            i++
        }
        return -1
    }

    // endregion
}
