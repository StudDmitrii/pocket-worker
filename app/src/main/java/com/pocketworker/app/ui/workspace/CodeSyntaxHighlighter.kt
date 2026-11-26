package com.pocketworker.app.ui.workspace

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import kotlin.math.max
import kotlin.math.min

enum class CodeLanguage(val displayName: String) {
    KOTLIN("Kotlin"),
    JAVA("Java"),
    JAVASCRIPT("JavaScript"),
    TYPESCRIPT("TypeScript"),
    PYTHON("Python"),
    C_CPP("C / C++"),
    CSHARP("C#"),
    GO("Go"),
    RUST("Rust"),
    SWIFT("Swift"),
    DART("Dart"),
    PHP("PHP"),
    HTML_XML("HTML / XML"),
    CSS("CSS"),
    JSON("JSON"),
    YAML("YAML"),
    TOML_INI("Config"),
    MARKDOWN("Markdown"),
    SQL("SQL"),
    SHELL("Shell"),
    DOCKERFILE("Dockerfile"),
    PLAIN_TEXT("Text")
}

data class VsCodeEditorPalette(
    val editorBackground: Color,
    val gutterBackground: Color,
    val gutterDivider: Color,
    val lineNumber: Color,
    val plainText: Color,
    val keyword: Color,
    val controlKeyword: Color,
    val type: Color,
    val function: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val annotation: Color,
    val property: Color,
    val tag: Color,
    val attribute: Color,
    val punctuation: Color,
    val markdownHeading: Color,
    val markdownLink: Color
)

object VsCodePalettes {
    val Dark = VsCodeEditorPalette(
        editorBackground = Color(0xFF1E1E1E),
        gutterBackground = Color(0xFF252526),
        gutterDivider = Color(0xFF333333),
        lineNumber = Color(0xFF858585),
        plainText = Color(0xFFD4D4D4),
        keyword = Color(0xFF569CD6),
        controlKeyword = Color(0xFFC586C0),
        type = Color(0xFF4EC9B0),
        function = Color(0xFFDCDCAA),
        string = Color(0xFFCE9178),
        number = Color(0xFFB5CEA8),
        comment = Color(0xFF6A9955),
        annotation = Color(0xFFD7BA7D),
        property = Color(0xFF9CDCFE),
        tag = Color(0xFF569CD6),
        attribute = Color(0xFF9CDCFE),
        punctuation = Color(0xFF808080),
        markdownHeading = Color(0xFF569CD6),
        markdownLink = Color(0xFF4EC9B0)
    )

    val Light = VsCodeEditorPalette(
        editorBackground = Color(0xFFFFFFFF),
        gutterBackground = Color(0xFFF6F8FA),
        gutterDivider = Color(0xFFE2E8F0),
        lineNumber = Color(0xFF6E7681),
        plainText = Color(0xFF1F2937),
        keyword = Color(0xFF0000FF),
        controlKeyword = Color(0xFFAF00DB),
        type = Color(0xFF267F99),
        function = Color(0xFF795E26),
        string = Color(0xFFA31515),
        number = Color(0xFF098658),
        comment = Color(0xFF008000),
        annotation = Color(0xFF795E26),
        property = Color(0xFF001080),
        tag = Color(0xFF800000),
        attribute = Color(0xFFE50000),
        punctuation = Color(0xFF64748B),
        markdownHeading = Color(0xFF0000FF),
        markdownLink = Color(0xFF0369A1)
    )

    fun forTheme(isDark: Boolean): VsCodeEditorPalette = if (isDark) Dark else Light
}

private data class HighlightSpan(
    val start: Int,
    val end: Int,
    val style: SpanStyle
)

private data class LangRules(
    val keywords: Set<String>,
    val controlKeywords: Set<String>,
    val builtinTypes: Set<String>,
    val slashComments: Boolean = true,
    val hashComments: Boolean = false,
    val backtickStrings: Boolean = false,
    val tripleQuotes: Boolean = false,
    val dollarVars: Boolean = false,
    val hashDirectives: Boolean = false,
    val caseInsensitive: Boolean = false
)

object CodeSyntaxHighlighter {
    private const val MAX_HIGHLIGHT_CHARS = 160_000

    fun detectLanguage(filePath: String): CodeLanguage {
        val fileName = filePath.substringAfterLast('/').substringAfterLast('\\')
        val lowerName = fileName.lowercase()

        when {
            lowerName == "dockerfile" || lowerName.startsWith("dockerfile.") -> return CodeLanguage.DOCKERFILE
            lowerName == "makefile" || lowerName == "cmakelists.txt" -> return CodeLanguage.SHELL
            lowerName == "gradlew" || lowerName == ".bashrc" || lowerName == ".zshrc" || lowerName == ".profile" ->
                return CodeLanguage.SHELL
            lowerName == ".gitignore" || lowerName == ".gitattributes" || lowerName == ".editorconfig" ||
                lowerName == ".env" || lowerName.startsWith(".env.") || lowerName == "proguard-rules.pro" ->
                return CodeLanguage.TOML_INI
        }

        val ext = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return when (ext) {
            "kt", "kts" -> CodeLanguage.KOTLIN
            "java", "groovy", "gradle" -> CodeLanguage.JAVA
            "js", "mjs", "cjs", "jsx" -> CodeLanguage.JAVASCRIPT
            "ts", "mts", "cts", "tsx" -> CodeLanguage.TYPESCRIPT
            "py", "pyw", "pyi" -> CodeLanguage.PYTHON
            "c", "h", "cpp", "cc", "cxx", "hpp", "hh", "hxx" -> CodeLanguage.C_CPP
            "cs" -> CodeLanguage.CSHARP
            "go" -> CodeLanguage.GO
            "rs" -> CodeLanguage.RUST
            "swift" -> CodeLanguage.SWIFT
            "dart" -> CodeLanguage.DART
            "php", "phtml" -> CodeLanguage.PHP
            "html", "htm", "xhtml", "xml", "svg", "xsd", "xsl", "xaml", "plist", "csproj", "pom" ->
                CodeLanguage.HTML_XML
            "css", "scss", "sass", "less" -> CodeLanguage.CSS
            "json", "jsonc", "json5", "arb", "geojson" -> CodeLanguage.JSON
            "yml", "yaml" -> CodeLanguage.YAML
            "toml", "ini", "cfg", "conf", "properties", "env", "prefs" -> CodeLanguage.TOML_INI
            "md", "markdown", "mdx" -> CodeLanguage.MARKDOWN
            "sql", "ddl", "dml" -> CodeLanguage.SQL
            "sh", "bash", "zsh", "fish", "ps1", "psm1", "bat", "cmd" -> CodeLanguage.SHELL
            else -> CodeLanguage.PLAIN_TEXT
        }
    }

    /**
     * Maps a Markdown code fence language identifier (e.g. `kotlin`, `py`, `tsx`, `bash`, `c++`)
     * to a [CodeLanguage] for syntax highlighting.
     */
    fun detectLanguageByFence(fence: String): CodeLanguage {
        val token = fence.trim()
            .substringBefore(' ')
            .substringBefore('{')
            .substringBefore(':')
            .lowercase()
            .removePrefix("language-")
            .removePrefix("lang-")
        if (token.isEmpty()) return CodeLanguage.PLAIN_TEXT
        return when (token) {
            "kotlin", "kt", "kts" -> CodeLanguage.KOTLIN
            "java", "groovy", "gradle" -> CodeLanguage.JAVA
            "javascript", "js", "jsx", "mjs", "cjs", "node" -> CodeLanguage.JAVASCRIPT
            "typescript", "ts", "tsx", "mts", "cts" -> CodeLanguage.TYPESCRIPT
            "python", "py", "py3", "pyi" -> CodeLanguage.PYTHON
            "c", "h", "cpp", "c++", "cc", "cxx", "hpp", "objc", "objective-c" -> CodeLanguage.C_CPP
            "csharp", "c#", "cs", "dotnet" -> CodeLanguage.CSHARP
            "go", "golang" -> CodeLanguage.GO
            "rust", "rs" -> CodeLanguage.RUST
            "swift" -> CodeLanguage.SWIFT
            "dart", "flutter" -> CodeLanguage.DART
            "php", "phtml" -> CodeLanguage.PHP
            "html", "htm", "xhtml", "xml", "svg", "xaml", "plist" -> CodeLanguage.HTML_XML
            "css", "scss", "sass", "less" -> CodeLanguage.CSS
            "json", "jsonc", "json5", "jsonl" -> CodeLanguage.JSON
            "yaml", "yml" -> CodeLanguage.YAML
            "toml", "ini", "cfg", "conf", "properties", "env", "dotenv", "gitconfig" -> CodeLanguage.TOML_INI
            "markdown", "md", "mdx" -> CodeLanguage.MARKDOWN
            "sql", "mysql", "postgresql", "postgres", "psql", "sqlite", "plsql" -> CodeLanguage.SQL
            "sh", "bash", "zsh", "shell", "fish", "console", "terminal", "ps1", "powershell", "pwsh", "bat", "cmd", "makefile" ->
                CodeLanguage.SHELL
            "dockerfile", "docker" -> CodeLanguage.DOCKERFILE
            else -> detectLanguage("file.$token")
        }
    }

    fun highlight(
        text: String,
        language: CodeLanguage,
        isDark: Boolean
    ): AnnotatedString {
        if (text.isEmpty() || language == CodeLanguage.PLAIN_TEXT || text.length > MAX_HIGHLIGHT_CHARS) {
            return AnnotatedString(text)
        }
        val palette = VsCodePalettes.forTheme(isDark)
        val spans = computeSpans(text, language, palette)
        return buildAnnotatedString {
            append(text)
            for (span in spans) {
                if (span.start < span.end && span.start >= 0 && span.end <= text.length) {
                    addStyle(span.style, span.start, span.end)
                }
            }
        }
    }

    fun highlightLines(
        text: String,
        language: CodeLanguage,
        isDark: Boolean
    ): List<AnnotatedString> {
        if (text.isEmpty()) return listOf(AnnotatedString(""))
        val rawLines = text.split('\n')
        if (language == CodeLanguage.PLAIN_TEXT || text.length > MAX_HIGHLIGHT_CHARS) {
            return rawLines.map { AnnotatedString(it.removeSuffix("\r")) }
        }
        val palette = VsCodePalettes.forTheme(isDark)
        val spans = computeSpans(text, language, palette)

        val result = ArrayList<AnnotatedString>(rawLines.size)
        var lineStart = 0
        var spanIdx = 0

        for (rawLine in rawLines) {
            val cleanLine = rawLine.removeSuffix("\r")
            val lineEnd = lineStart + cleanLine.length

            while (spanIdx < spans.size && spans[spanIdx].end <= lineStart) {
                spanIdx++
            }

            val lineAnnotated = buildAnnotatedString {
                append(cleanLine)
                var j = spanIdx
                while (j < spans.size && spans[j].start < lineEnd) {
                    val s = spans[j]
                    val localStart = max(s.start, lineStart) - lineStart
                    val localEnd = min(s.end, lineEnd) - lineStart
                    if (localStart < localEnd) {
                        addStyle(s.style, localStart, localEnd)
                    }
                    j++
                }
            }
            result.add(lineAnnotated)
            lineStart += rawLine.length + 1 // +1 for '\n'
        }
        return result
    }

    fun visualTransformation(
        language: CodeLanguage,
        isDark: Boolean
    ): VisualTransformation = VisualTransformation { annotated ->
        val highlighted = highlight(annotated.text, language, isDark)
        TransformedText(highlighted, OffsetMapping.Identity)
    }

    private fun computeSpans(
        text: String,
        language: CodeLanguage,
        palette: VsCodeEditorPalette
    ): List<HighlightSpan> {
        val spans = ArrayList<HighlightSpan>(min(text.length / 8 + 16, 16_384))
        when (language) {
            CodeLanguage.JSON -> scanJson(text, palette, spans)
            CodeLanguage.HTML_XML -> scanXmlHtml(text, palette, spans)
            CodeLanguage.CSS -> scanCss(text, palette, spans)
            CodeLanguage.YAML -> scanYaml(text, palette, spans)
            CodeLanguage.TOML_INI -> scanTomlIni(text, palette, spans)
            CodeLanguage.MARKDOWN -> scanMarkdown(text, palette, spans)
            CodeLanguage.SQL -> scanSql(text, palette, spans)
            CodeLanguage.PLAIN_TEXT -> Unit
            else -> scanCode(text, rulesFor(language), palette, spans)
        }
        spans.sortBy { it.start }
        return spans
    }

    // region JSON scanner
    private fun scanJson(text: String, p: VsCodeEditorPalette, out: MutableList<HighlightSpan>) {
        val n = text.length
        var i = 0
        while (i < n) {
            val c = text[i]
            when {
                c == '/' && i + 1 < n && text[i + 1] == '/' -> {
                    val end = findLineEnd(text, i)
                    out.add(HighlightSpan(i, end, commentStyle(p)))
                    i = end
                }
                c == '/' && i + 1 < n && text[i + 1] == '*' -> {
                    val end = findBlockCommentEnd(text, i + 2)
                    out.add(HighlightSpan(i, end, commentStyle(p)))
                    i = end
                }
                c == '"' -> {
                    val end = findQuotedEnd(text, i, '"')
                    var look = end
                    while (look < n && (text[look] == ' ' || text[look] == '\t')) look++
                    val isKey = look < n && text[look] == ':'
                    out.add(HighlightSpan(i, end, SpanStyle(color = if (isKey) p.property else p.string)))
                    i = end
                }
                c == '-' || c.isDigit() -> {
                    if (c == '-' && (i + 1 >= n || !text[i + 1].isDigit())) {
                        i++
                    } else {
                        val end = scanNumberEnd(text, if (c == '-') i + 1 else i)
                        out.add(HighlightSpan(i, end, SpanStyle(color = p.number)))
                        i = end
                    }
                }
                c.isLetter() -> {
                    val end = scanIdentEnd(text, i)
                    val word = text.substring(i, end)
                    if (word == "true" || word == "false" || word == "null") {
                        out.add(HighlightSpan(i, end, SpanStyle(color = p.keyword)))
                    }
                    i = end
                }
                else -> i++
            }
        }
    }
    // endregion

    // region HTML / XML scanner
    private fun scanXmlHtml(text: String, p: VsCodeEditorPalette, out: MutableList<HighlightSpan>) {
        val n = text.length
        var i = 0
        while (i < n) {
            val c = text[i]
            when {
                c == '<' && text.startsWith("<!--", i) -> {
                    val close = text.indexOf("-->", i + 4)
                    val end = if (close == -1) n else close + 3
                    out.add(HighlightSpan(i, end, commentStyle(p)))
                    i = end
                }
                c == '<' && (text.startsWith("<?", i) || text.startsWith("<!", i)) -> {
                    val close = text.indexOf('>', i + 2)
                    val end = if (close == -1) n else close + 1
                    out.add(HighlightSpan(i, end, SpanStyle(color = p.keyword)))
                    i = end
                }
                c == '<' -> {
                    val openStart = i
                    i++
                    val isClosing = i < n && text[i] == '/'
                    if (isClosing) i++
                    out.add(HighlightSpan(openStart, i, SpanStyle(color = p.punctuation)))

                    val tagStart = i
                    while (i < n && (text[i].isLetterOrDigit() || text[i] in ":_-.")) i++
                    if (i > tagStart) {
                        out.add(HighlightSpan(tagStart, i, SpanStyle(color = p.tag, fontWeight = FontWeight.Medium)))
                    }

                    // Attributes inside tag
                    while (i < n && text[i] != '>') {
                        val ch = text[i]
                        when {
                            ch == '/' && i + 1 < n && text[i + 1] == '>' -> {
                                out.add(HighlightSpan(i, i + 2, SpanStyle(color = p.punctuation)))
                                i += 2
                                break
                            }
                            ch == '"' || ch == '\'' -> {
                                val end = findQuotedEnd(text, i, ch)
                                out.add(HighlightSpan(i, end, SpanStyle(color = p.string)))
                                i = end
                            }
                            ch.isLetter() || ch == '_' || ch == ':' -> {
                                val attrStart = i
                                while (i < n && (text[i].isLetterOrDigit() || text[i] in ":_-.")) i++
                                out.add(HighlightSpan(attrStart, i, SpanStyle(color = p.attribute)))
                            }
                            else -> i++
                        }
                    }
                    if (i < n && text[i] == '>') {
                        out.add(HighlightSpan(i, i + 1, SpanStyle(color = p.punctuation)))
                        i++
                    }
                }
                c == '&' -> {
                    val semi = text.indexOf(';', i + 1)
                    if (semi in (i + 2)..(i + 10) && text.substring(i + 1, semi).all { it.isLetterOrDigit() || it == '#' }) {
                        out.add(HighlightSpan(i, semi + 1, SpanStyle(color = p.keyword)))
                        i = semi + 1
                    } else {
                        i++
                    }
                }
                else -> i++
            }
        }
    }
    // endregion

    // region CSS / SCSS scanner
    private fun scanCss(text: String, p: VsCodeEditorPalette, out: MutableList<HighlightSpan>) {
        val n = text.length
        var i = 0
        var braceDepth = 0
        while (i < n) {
            val c = text[i]
            when {
                c == '/' && i + 1 < n && text[i + 1] == '*' -> {
                    val end = findBlockCommentEnd(text, i + 2)
                    out.add(HighlightSpan(i, end, commentStyle(p)))
                    i = end
                }
                c == '/' && i + 1 < n && text[i + 1] == '/' -> {
                    val end = findLineEnd(text, i)
                    out.add(HighlightSpan(i, end, commentStyle(p)))
                    i = end
                }
                c == '"' || c == '\'' -> {
                    val end = findQuotedEnd(text, i, c)
                    out.add(HighlightSpan(i, end, SpanStyle(color = p.string)))
                    i = end
                }
                c == '{' -> {
                    braceDepth++
                    i++
                }
                c == '}' -> {
                    braceDepth = max(0, braceDepth - 1)
                    i++
                }
                c == '@' -> {
                    val end = scanCssIdentEnd(text, i + 1)
                    out.add(HighlightSpan(i, end, SpanStyle(color = p.controlKeyword)))
                    i = end
                }
                c == '!' && text.startsWith("!important", i, ignoreCase = true) -> {
                    out.add(HighlightSpan(i, i + 10, SpanStyle(color = p.keyword, fontWeight = FontWeight.SemiBold)))
                    i += 10
                }
                c == '#' -> {
                    val end = scanCssIdentEnd(text, i + 1)
                    val color = if (braceDepth > 0) p.number else p.annotation
                    out.add(HighlightSpan(i, end, SpanStyle(color = color)))
                    i = end
                }
                c == '.' && braceDepth == 0 && i + 1 < n && (text[i + 1].isLetter() || text[i + 1] == '_' || text[i + 1] == '-') -> {
                    val end = scanCssIdentEnd(text, i + 1)
                    out.add(HighlightSpan(i, end, SpanStyle(color = p.annotation)))
                    i = end
                }
                c.isDigit() -> {
                    var end = scanNumberEnd(text, i)
                    while (end < n && (text[end].isLetter() || text[end] == '%')) end++
                    out.add(HighlightSpan(i, end, SpanStyle(color = p.number)))
                    i = end
                }
                c.isLetter() || c == '-' || c == '_' -> {
                    val end = scanCssIdentEnd(text, i)
                    var look = end
                    while (look < n && (text[look] == ' ' || text[look] == '\t')) look++
                    when {
                        braceDepth > 0 && look < n && text[look] == ':' ->
                            out.add(HighlightSpan(i, end, SpanStyle(color = p.property)))
                        look < n && text[look] == '(' ->
                            out.add(HighlightSpan(i, end, SpanStyle(color = p.function)))
                        braceDepth == 0 ->
                            out.add(HighlightSpan(i, end, SpanStyle(color = p.tag)))
                    }
                    i = end
                }
                else -> i++
            }
        }
    }

    private fun scanCssIdentEnd(text: String, start: Int): Int {
        var i = start
        while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '-' || text[i] == '_')) i++
        return i
    }
    // endregion

    // region YAML scanner
    private fun scanYaml(text: String, p: VsCodeEditorPalette, out: MutableList<HighlightSpan>) {
        val n = text.length
        var lineStart = 0
        while (lineStart < n) {
            val lineEnd = findLineEnd(text, lineStart)
            var i = lineStart
            while (i < lineEnd && (text[i] == ' ' || text[i] == '\t')) i++

            if (i < lineEnd) {
                when {
                    text[i] == '#' -> {
                        out.add(HighlightSpan(i, lineEnd, commentStyle(p)))
                    }
                    text.startsWith("---", i) || text.startsWith("...", i) -> {
                        out.add(HighlightSpan(i, min(i + 3, lineEnd), SpanStyle(color = p.keyword, fontWeight = FontWeight.Bold)))
                    }
                    else -> {
                        if (text[i] == '-' && (i + 1 >= lineEnd || text[i + 1] == ' ')) {
                            out.add(HighlightSpan(i, i + 1, SpanStyle(color = p.keyword)))
                            i++
                            while (i < lineEnd && text[i] == ' ') i++
                        }
                        val colonIdx = findYamlKeyColon(text, i, lineEnd)
                        if (colonIdx > i) {
                            out.add(HighlightSpan(i, colonIdx, SpanStyle(color = p.property)))
                            scanYamlValue(text, colonIdx + 1, lineEnd, p, out)
                        } else {
                            scanYamlValue(text, i, lineEnd, p, out)
                        }
                    }
                }
            }
            lineStart = if (lineEnd < n) lineEnd + 1 else n
        }
    }

    private fun findYamlKeyColon(text: String, start: Int, lineEnd: Int): Int {
        var i = start
        if (i < lineEnd && (text[i] == '"' || text[i] == '\'')) return -1
        while (i < lineEnd) {
            val c = text[i]
            if (c == '#') return -1
            if (c == ':' && (i + 1 == lineEnd || text[i + 1] == ' ' || text[i + 1] == '\t' || text[i + 1] == '\r')) {
                return i
            }
            i++
        }
        return -1
    }

    private fun scanYamlValue(text: String, start: Int, end: Int, p: VsCodeEditorPalette, out: MutableList<HighlightSpan>) {
        var i = start
        while (i < end) {
            val c = text[i]
            when {
                c == '#' && (i == start || text[i - 1].isWhitespace()) -> {
                    out.add(HighlightSpan(i, end, commentStyle(p)))
                    return
                }
                c == '"' || c == '\'' -> {
                    val qEnd = min(findQuotedEnd(text, i, c), end)
                    out.add(HighlightSpan(i, qEnd, SpanStyle(color = p.string)))
                    i = qEnd
                }
                c == '&' || c == '*' -> {
                    var aEnd = i + 1
                    while (aEnd < end && !text[aEnd].isWhitespace()) aEnd++
                    out.add(HighlightSpan(i, aEnd, SpanStyle(color = p.type)))
                    i = aEnd
                }
                c.isDigit() || (c == '-' && i + 1 < end && text[i + 1].isDigit()) -> {
                    val numEnd = min(scanNumberEnd(text, if (c == '-') i + 1 else i), end)
                    out.add(HighlightSpan(i, numEnd, SpanStyle(color = p.number)))
                    i = numEnd
                }
                c.isLetter() || c == '~' -> {
                    val wEnd = min(scanIdentEnd(text, i), end)
                    val word = text.substring(i, wEnd).lowercase()
                    if (word in YAML_LITERALS) {
                        out.add(HighlightSpan(i, wEnd, SpanStyle(color = p.keyword)))
                    }
                    i = max(wEnd, i + 1)
                }
                else -> i++
            }
        }
    }
    // endregion

    // region TOML / INI / Properties / .env scanner
    private fun scanTomlIni(text: String, p: VsCodeEditorPalette, out: MutableList<HighlightSpan>) {
        val n = text.length
        var lineStart = 0
        while (lineStart < n) {
            val lineEnd = findLineEnd(text, lineStart)
            var i = lineStart
            while (i < lineEnd && (text[i] == ' ' || text[i] == '\t')) i++
            if (i < lineEnd) {
                when {
                    text[i] == '#' || text[i] == ';' -> {
                        out.add(HighlightSpan(i, lineEnd, commentStyle(p)))
                    }
                    text[i] == '[' -> {
                        val close = text.indexOf(']', i + 1)
                        val end = if (close in i until lineEnd) {
                            if (close + 1 < lineEnd && text[close + 1] == ']') close + 2 else close + 1
                        } else {
                            lineEnd
                        }
                        out.add(HighlightSpan(i, end, SpanStyle(color = p.type, fontWeight = FontWeight.SemiBold)))
                    }
                    else -> {
                        val eqIdx = text.indexOf('=', i)
                        if (eqIdx in (i + 1) until lineEnd) {
                            out.add(HighlightSpan(i, eqIdx, SpanStyle(color = p.property)))
                            scanYamlValue(text, eqIdx + 1, lineEnd, p, out)
                        }
                    }
                }
            }
            lineStart = if (lineEnd < n) lineEnd + 1 else n
        }
    }
    // endregion

    // region Markdown scanner
    private fun scanMarkdown(text: String, p: VsCodeEditorPalette, out: MutableList<HighlightSpan>) {
        val n = text.length
        var lineStart = 0
        var inFence = false
        var fenceLang = CodeLanguage.PLAIN_TEXT

        while (lineStart < n) {
            val lineEnd = findLineEnd(text, lineStart)
            var i = lineStart
            while (i < lineEnd && text[i] == ' ') i++

            if (text.startsWith("```", i)) {
                out.add(HighlightSpan(i, lineEnd, SpanStyle(color = p.punctuation, fontWeight = FontWeight.SemiBold)))
                if (!inFence) {
                    inFence = true
                    val hint = text.substring(i + 3, lineEnd).trim()
                    fenceLang = if (hint.isNotEmpty()) detectLanguage("code.$hint") else CodeLanguage.PLAIN_TEXT
                } else {
                    inFence = false
                    fenceLang = CodeLanguage.PLAIN_TEXT
                }
                lineStart = if (lineEnd < n) lineEnd + 1 else n
                continue
            }

            if (inFence) {
                if (fenceLang != CodeLanguage.PLAIN_TEXT && fenceLang != CodeLanguage.MARKDOWN) {
                    val lineSlice = text.substring(lineStart, lineEnd)
                    val innerSpans = computeSpans(lineSlice, fenceLang, p)
                    for (s in innerSpans) {
                        out.add(HighlightSpan(lineStart + s.start, lineStart + s.end, s.style))
                    }
                } else {
                    out.add(HighlightSpan(lineStart, lineEnd, SpanStyle(color = p.string)))
                }
                lineStart = if (lineEnd < n) lineEnd + 1 else n
                continue
            }

            if (i < lineEnd) {
                when {
                    text[i] == '#' -> {
                        var h = i
                        while (h < lineEnd && text[h] == '#') h++
                        if (h < lineEnd && text[h] == ' ') {
                            out.add(
                                HighlightSpan(
                                    i,
                                    lineEnd,
                                    SpanStyle(color = p.markdownHeading, fontWeight = FontWeight.Bold)
                                )
                            )
                        } else {
                            scanMarkdownInline(text, i, lineEnd, p, out)
                        }
                    }
                    text[i] == '>' -> {
                        out.add(HighlightSpan(i, lineEnd, commentStyle(p)))
                    }
                    (text[i] == '-' || text[i] == '*' || text[i] == '+') && i + 1 < lineEnd && text[i + 1] == ' ' -> {
                        out.add(HighlightSpan(i, i + 1, SpanStyle(color = p.keyword, fontWeight = FontWeight.Bold)))
                        scanMarkdownInline(text, i + 2, lineEnd, p, out)
                    }
                    else -> {
                        scanMarkdownInline(text, i, lineEnd, p, out)
                    }
                }
            }
            lineStart = if (lineEnd < n) lineEnd + 1 else n
        }
    }

    private fun scanMarkdownInline(text: String, start: Int, end: Int, p: VsCodeEditorPalette, out: MutableList<HighlightSpan>) {
        var i = start
        while (i < end) {
            when {
                text[i] == '`' -> {
                    val close = text.indexOf('`', i + 1)
                    if (close in (i + 1) until end) {
                        out.add(HighlightSpan(i, close + 1, SpanStyle(color = p.string)))
                        i = close + 1
                    } else {
                        i++
                    }
                }
                text.startsWith("**", i) -> {
                    val close = text.indexOf("**", i + 2)
                    if (close in (i + 2) until end) {
                        out.add(HighlightSpan(i, close + 2, SpanStyle(color = p.keyword, fontWeight = FontWeight.Bold)))
                        i = close + 2
                    } else {
                        i += 2
                    }
                }
                text[i] == '*' -> {
                    val close = text.indexOf('*', i + 1)
                    if (close in (i + 1) until end) {
                        out.add(HighlightSpan(i, close + 1, SpanStyle(fontStyle = FontStyle.Italic)))
                        i = close + 1
                    } else {
                        i++
                    }
                }
                text[i] == '[' -> {
                    val closeBracket = text.indexOf(']', i + 1)
                    if (closeBracket in (i + 1) until end && closeBracket + 1 < end && text[closeBracket + 1] == '(') {
                        val closeParen = text.indexOf(')', closeBracket + 2)
                        if (closeParen in (closeBracket + 2) until end) {
                            out.add(HighlightSpan(i, closeBracket + 1, SpanStyle(color = p.markdownLink)))
                            out.add(HighlightSpan(closeBracket + 1, closeParen + 1, SpanStyle(color = p.string)))
                            i = closeParen + 1
                        } else {
                            i++
                        }
                    } else {
                        i++
                    }
                }
                else -> i++
            }
        }
    }
    // endregion

    // region SQL scanner
    private fun scanSql(text: String, p: VsCodeEditorPalette, out: MutableList<HighlightSpan>) {
        val n = text.length
        var i = 0
        while (i < n) {
            val c = text[i]
            when {
                c == '-' && i + 1 < n && text[i + 1] == '-' -> {
                    val end = findLineEnd(text, i)
                    out.add(HighlightSpan(i, end, commentStyle(p)))
                    i = end
                }
                c == '/' && i + 1 < n && text[i + 1] == '*' -> {
                    val end = findBlockCommentEnd(text, i + 2)
                    out.add(HighlightSpan(i, end, commentStyle(p)))
                    i = end
                }
                c == '\'' -> {
                    val end = findQuotedEnd(text, i, '\'')
                    out.add(HighlightSpan(i, end, SpanStyle(color = p.string)))
                    i = end
                }
                c == '`' || c == '"' -> {
                    val end = findQuotedEnd(text, i, c)
                    out.add(HighlightSpan(i, end, SpanStyle(color = p.property)))
                    i = end
                }
                c.isDigit() -> {
                    val end = scanNumberEnd(text, i)
                    out.add(HighlightSpan(i, end, SpanStyle(color = p.number)))
                    i = end
                }
                c.isLetter() || c == '_' -> {
                    val end = scanIdentEnd(text, i)
                    val upper = text.substring(i, end).uppercase()
                    var look = end
                    while (look < n && (text[look] == ' ' || text[look] == '\t')) look++
                    when {
                        upper in SQL_KEYWORDS -> out.add(HighlightSpan(i, end, SpanStyle(color = p.keyword)))
                        upper in SQL_TYPES -> out.add(HighlightSpan(i, end, SpanStyle(color = p.type)))
                        look < n && text[look] == '(' -> out.add(HighlightSpan(i, end, SpanStyle(color = p.function)))
                    }
                    i = end
                }
                else -> i++
            }
        }
    }
    // endregion

    // region General code scanner (Kotlin, Java, JS/TS, Python, C/C++, C#, Go, Rust, Swift, Dart, PHP, Shell, Dockerfile)
    private fun scanCode(
        text: String,
        rules: LangRules,
        p: VsCodeEditorPalette,
        out: MutableList<HighlightSpan>
    ) {
        val n = text.length
        var i = 0
        var prevNonWs = ' '
        while (i < n) {
            val c = text[i]
            when {
                c.isWhitespace() -> i++
                rules.slashComments && c == '/' && i + 1 < n && text[i + 1] == '/' -> {
                    val end = findLineEnd(text, i)
                    out.add(HighlightSpan(i, end, commentStyle(p)))
                    i = end
                }
                rules.slashComments && c == '/' && i + 1 < n && text[i + 1] == '*' -> {
                    val end = findBlockCommentEnd(text, i + 2)
                    out.add(HighlightSpan(i, end, commentStyle(p)))
                    i = end
                }
                rules.hashComments && c == '#' -> {
                    val end = findLineEnd(text, i)
                    out.add(HighlightSpan(i, end, commentStyle(p)))
                    i = end
                }
                rules.hashDirectives && c == '#' -> {
                    var end = i + 1
                    while (end < n && (text[end].isLetterOrDigit() || text[end] == '_' || text[end] == '[' || text[end] == ']')) {
                        end++
                    }
                    out.add(HighlightSpan(i, end, SpanStyle(color = p.controlKeyword)))
                    prevNonWs = '#'
                    i = end
                }
                rules.tripleQuotes && (text.startsWith("\"\"\"", i) || text.startsWith("'''", i)) -> {
                    val delim = text.substring(i, i + 3)
                    val close = text.indexOf(delim, i + 3)
                    val end = if (close == -1) n else close + 3
                    out.add(HighlightSpan(i, end, SpanStyle(color = p.string)))
                    prevNonWs = delim[0]
                    i = end
                }
                c == '"' || c == '\'' || (rules.backtickStrings && c == '`') -> {
                    val end = findQuotedEnd(text, i, c)
                    out.add(HighlightSpan(i, end, SpanStyle(color = p.string)))
                    prevNonWs = c
                    i = end
                }
                c == '@' && i + 1 < n && (text[i + 1].isLetter() || text[i + 1] == '_') -> {
                    val end = scanIdentEnd(text, i + 1)
                    out.add(HighlightSpan(i, end, SpanStyle(color = p.annotation)))
                    prevNonWs = '@'
                    i = end
                }
                rules.dollarVars && c == '$' && i + 1 < n && (text[i + 1].isLetter() || text[i + 1] == '_' || text[i + 1] == '{') -> {
                    val end = if (text[i + 1] == '{') {
                        val close = text.indexOf('}', i + 2)
                        if (close == -1) i + 2 else close + 1
                    } else {
                        scanIdentEnd(text, i + 1)
                    }
                    out.add(HighlightSpan(i, end, SpanStyle(color = p.property)))
                    prevNonWs = '$'
                    i = end
                }
                c.isDigit() -> {
                    val end = scanNumberEnd(text, i)
                    out.add(HighlightSpan(i, end, SpanStyle(color = p.number)))
                    prevNonWs = '0'
                    i = end
                }
                c.isLetter() || c == '_' -> {
                    val end = scanIdentEnd(text, i)
                    val rawWord = text.substring(i, end)
                    val word = if (rules.caseInsensitive) rawWord.uppercase() else rawWord
                    var look = end
                    while (look < n && (text[look] == ' ' || text[look] == '\t')) look++
                    val followedByParen = look < n && text[look] == '('

                    val color = when {
                        word in rules.controlKeywords -> p.controlKeyword
                        word in rules.keywords -> p.keyword
                        word in rules.builtinTypes -> p.type
                        followedByParen -> p.function
                        rawWord.length >= 2 && rawWord[0].isUpperCase() && rawWord.any { it.isLowerCase() } -> p.type
                        prevNonWs == '.' -> p.property
                        else -> null
                    }
                    if (color != null) {
                        out.add(HighlightSpan(i, end, SpanStyle(color = color)))
                    }
                    prevNonWs = 'a'
                    i = end
                }
                else -> {
                    prevNonWs = c
                    i++
                }
            }
        }
    }
    // endregion

    // region Low-level helpers
    private fun commentStyle(p: VsCodeEditorPalette): SpanStyle =
        SpanStyle(color = p.comment, fontStyle = FontStyle.Italic)

    private fun findLineEnd(text: String, start: Int): Int {
        val idx = text.indexOf('\n', start)
        return if (idx == -1) text.length else idx
    }

    private fun findBlockCommentEnd(text: String, afterOpen: Int): Int {
        val idx = text.indexOf("*/", afterOpen)
        return if (idx == -1) text.length else idx + 2
    }

    private fun findQuotedEnd(text: String, quoteStart: Int, quoteChar: Char): Int {
        var i = quoteStart + 1
        val n = text.length
        while (i < n) {
            val ch = text[i]
            if (ch == '\\' && i + 1 < n) {
                i += 2
                continue
            }
            if (ch == quoteChar) return i + 1
            if (ch == '\n' && quoteChar != '`') return i
            i++
        }
        return n
    }

    private fun scanIdentEnd(text: String, start: Int): Int {
        var i = start
        while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_')) i++
        return i
    }

    private fun scanNumberEnd(text: String, start: Int): Int {
        var i = start
        val n = text.length
        if (i + 1 < n && text[i] == '0' && (text[i + 1] == 'x' || text[i + 1] == 'X' || text[i + 1] == 'b' || text[i + 1] == 'B')) {
            i += 2
            while (i < n && (text[i].isDigit() || text[i] in 'a'..'f' || text[i] in 'A'..'F' || text[i] == '_')) i++
            while (i < n && text[i] in "uUlL") i++
            return i
        }
        while (i < n && (text[i].isDigit() || text[i] == '_')) i++
        if (i + 1 < n && text[i] == '.' && text[i + 1].isDigit()) {
            i++
            while (i < n && (text[i].isDigit() || text[i] == '_')) i++
        }
        if (i < n && (text[i] == 'e' || text[i] == 'E')) {
            i++
            if (i < n && (text[i] == '+' || text[i] == '-')) i++
            while (i < n && (text[i].isDigit() || text[i] == '_')) i++
        }
        while (i < n && text[i] in "fFdDlLuU") i++
        return i
    }
    // endregion

    // region Language rule tables
    private fun rulesFor(language: CodeLanguage): LangRules = when (language) {
        CodeLanguage.KOTLIN -> KOTLIN_RULES
        CodeLanguage.JAVA -> JAVA_RULES
        CodeLanguage.JAVASCRIPT, CodeLanguage.TYPESCRIPT -> JS_TS_RULES
        CodeLanguage.PYTHON -> PYTHON_RULES
        CodeLanguage.C_CPP -> C_CPP_RULES
        CodeLanguage.CSHARP -> CSHARP_RULES
        CodeLanguage.GO -> GO_RULES
        CodeLanguage.RUST -> RUST_RULES
        CodeLanguage.SWIFT -> SWIFT_RULES
        CodeLanguage.DART -> DART_RULES
        CodeLanguage.PHP -> PHP_RULES
        CodeLanguage.SHELL -> SHELL_RULES
        CodeLanguage.DOCKERFILE -> DOCKERFILE_RULES
        else -> KOTLIN_RULES
    }

    private val YAML_LITERALS = setOf("true", "false", "yes", "no", "null", "on", "off", "~")

    private val SQL_KEYWORDS = setOf(
        "SELECT", "FROM", "WHERE", "JOIN", "INNER", "LEFT", "RIGHT", "OUTER", "FULL", "CROSS",
        "ON", "AS", "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE", "CREATE", "ALTER",
        "DROP", "TABLE", "VIEW", "INDEX", "TRIGGER", "DATABASE", "SCHEMA", "PRIMARY", "FOREIGN",
        "KEY", "REFERENCES", "NOT", "NULL", "UNIQUE", "DEFAULT", "CHECK", "CONSTRAINT",
        "GROUP", "ORDER", "BY", "HAVING", "LIMIT", "OFFSET", "ASC", "DESC", "DISTINCT",
        "UNION", "ALL", "AND", "OR", "IN", "IS", "LIKE", "ILIKE", "BETWEEN", "EXISTS",
        "CASE", "WHEN", "THEN", "ELSE", "END", "BEGIN", "COMMIT", "ROLLBACK", "TRANSACTION",
        "IF", "WITH", "RECURSIVE", "RETURNING", "CONFLICT", "DO", "NOTHING"
    )

    private val SQL_TYPES = setOf(
        "INT", "INTEGER", "BIGINT", "SMALLINT", "TINYINT", "SERIAL", "BIGSERIAL",
        "VARCHAR", "CHAR", "TEXT", "BOOLEAN", "BOOL", "FLOAT", "DOUBLE", "DECIMAL",
        "NUMERIC", "DATE", "TIME", "TIMESTAMP", "TIMESTAMPTZ", "JSON", "JSONB", "UUID", "BLOB"
    )

    private val KOTLIN_RULES = LangRules(
        keywords = setOf(
            "val", "var", "fun", "class", "interface", "object", "enum", "data", "sealed",
            "inner", "open", "abstract", "override", "private", "protected", "public", "internal",
            "companion", "constructor", "init", "this", "super", "true", "false", "null",
            "is", "in", "as", "by", "get", "set", "const", "lateinit", "suspend", "inline",
            "noinline", "crossinline", "reified", "operator", "infix", "tailrec", "external",
            "vararg", "out", "typealias", "annotation", "value", "expect", "actual"
        ),
        controlKeywords = setOf(
            "package", "import", "if", "else", "when", "for", "while", "do", "return",
            "break", "continue", "try", "catch", "finally", "throw"
        ),
        builtinTypes = setOf(
            "Int", "Long", "Short", "Byte", "Float", "Double", "Boolean", "Char", "String",
            "Unit", "Any", "Nothing", "List", "MutableList", "Set", "MutableSet", "Map",
            "MutableMap", "Array", "Pair", "Triple", "Result", "Sequence", "Flow", "StateFlow"
        ),
        tripleQuotes = true
    )

    private val JAVA_RULES = LangRules(
        keywords = setOf(
            "class", "interface", "enum", "record", "extends", "implements", "new",
            "public", "private", "protected", "static", "final", "abstract", "synchronized",
            "volatile", "transient", "native", "strictfp", "default", "this", "super",
            "true", "false", "null", "void", "instanceof", "var", "sealed", "permits", "def"
        ),
        controlKeywords = setOf(
            "package", "import", "if", "else", "switch", "case", "for", "while", "do",
            "return", "break", "continue", "try", "catch", "finally", "throw", "throws", "yield"
        ),
        builtinTypes = setOf(
            "int", "long", "short", "byte", "float", "double", "boolean", "char",
            "String", "Object", "Integer", "Long", "Boolean", "List", "Map", "Set", "Optional"
        ),
        tripleQuotes = true
    )

    private val JS_TS_RULES = LangRules(
        keywords = setOf(
            "const", "let", "var", "function", "class", "extends", "implements", "interface",
            "type", "enum", "namespace", "declare", "abstract", "readonly", "public", "private",
            "protected", "static", "async", "new", "delete", "typeof", "instanceof", "in", "of",
            "keyof", "as", "is", "this", "super", "true", "false", "null", "undefined", "void",
            "get", "set", "constructor", "override", "satisfies"
        ),
        controlKeywords = setOf(
            "import", "export", "from", "default", "if", "else", "switch", "case", "for",
            "while", "do", "return", "break", "continue", "try", "catch", "finally", "throw",
            "await", "yield"
        ),
        builtinTypes = setOf(
            "string", "number", "boolean", "any", "unknown", "never", "object", "symbol", "bigint",
            "Array", "Promise", "Record", "Partial", "Required", "Map", "Set", "Date", "RegExp"
        ),
        backtickStrings = true
    )

    private val PYTHON_RULES = LangRules(
        keywords = setOf(
            "def", "class", "lambda", "global", "nonlocal", "and", "or", "not", "in", "is",
            "True", "False", "None", "self", "cls", "async", "del", "assert", "pass"
        ),
        controlKeywords = setOf(
            "import", "from", "as", "if", "elif", "else", "for", "while", "return",
            "break", "continue", "try", "except", "finally", "raise", "with", "yield",
            "await", "match", "case"
        ),
        builtinTypes = setOf(
            "int", "float", "str", "bool", "bytes", "list", "dict", "set", "tuple",
            "object", "type", "Optional", "List", "Dict", "Set", "Tuple", "Any", "Union"
        ),
        slashComments = false,
        hashComments = true,
        tripleQuotes = true
    )

    private val C_CPP_RULES = LangRules(
        keywords = setOf(
            "struct", "class", "union", "enum", "typedef", "using", "namespace", "template",
            "typename", "public", "private", "protected", "virtual", "override", "final",
            "static", "extern", "const", "constexpr", "volatile", "mutable", "inline", "explicit",
            "friend", "operator", "new", "delete", "sizeof", "alignof", "decltype", "auto",
            "this", "nullptr", "NULL", "true", "false", "noexcept", "register"
        ),
        controlKeywords = setOf(
            "if", "else", "switch", "case", "default", "for", "while", "do", "return",
            "break", "continue", "goto", "try", "catch", "throw", "co_await", "co_return", "co_yield"
        ),
        builtinTypes = setOf(
            "void", "bool", "char", "wchar_t", "short", "int", "long", "float", "double",
            "signed", "unsigned", "size_t", "int8_t", "int16_t", "int32_t", "int64_t",
            "uint8_t", "uint16_t", "uint32_t", "uint64_t", "string", "vector", "map", "set"
        ),
        hashDirectives = true
    )

    private val CSHARP_RULES = LangRules(
        keywords = setOf(
            "class", "struct", "interface", "enum", "record", "delegate", "event", "namespace",
            "public", "private", "protected", "internal", "static", "readonly", "const", "sealed",
            "abstract", "virtual", "override", "partial", "async", "new", "this", "base", "var",
            "ref", "out", "in", "params", "is", "as", "typeof", "sizeof", "nameof", "true", "false",
            "null", "void", "get", "set", "init", "required"
        ),
        controlKeywords = setOf(
            "using", "if", "else", "switch", "case", "default", "for", "foreach", "while", "do",
            "return", "break", "continue", "try", "catch", "finally", "throw", "await", "yield", "lock"
        ),
        builtinTypes = setOf(
            "bool", "byte", "sbyte", "char", "decimal", "double", "float", "int", "uint",
            "long", "ulong", "short", "ushort", "string", "object", "dynamic", "Task", "List", "Dictionary"
        ),
        hashDirectives = true
    )

    private val GO_RULES = LangRules(
        keywords = setOf(
            "func", "type", "struct", "interface", "map", "chan", "var", "const",
            "true", "false", "nil", "iota", "make", "new", "len", "cap", "append", "delete"
        ),
        controlKeywords = setOf(
            "package", "import", "if", "else", "switch", "case", "default", "for", "range",
            "return", "break", "continue", "goto", "fallthrough", "defer", "go", "select"
        ),
        builtinTypes = setOf(
            "bool", "string", "int", "int8", "int16", "int32", "int64", "uint", "uint8",
            "uint16", "uint32", "uint64", "uintptr", "byte", "rune", "float32", "float64", "error", "any"
        ),
        backtickStrings = true
    )

    private val RUST_RULES = LangRules(
        keywords = setOf(
            "fn", "let", "mut", "const", "static", "struct", "enum", "trait", "impl", "type",
            "pub", "crate", "mod", "ref", "move", "async", "unsafe", "extern", "where", "as",
            "in", "dyn", "self", "Self", "super", "true", "false"
        ),
        controlKeywords = setOf(
            "use", "if", "else", "match", "for", "while", "loop", "return", "break", "continue", "await"
        ),
        builtinTypes = setOf(
            "bool", "char", "str", "i8", "i16", "i32", "i64", "i128", "isize",
            "u8", "u16", "u32", "u64", "u128", "usize", "f32", "f64",
            "String", "Vec", "Option", "Result", "Box", "Rc", "Arc"
        ),
        hashDirectives = true
    )

    private val SWIFT_RULES = LangRules(
        keywords = setOf(
            "func", "var", "let", "class", "struct", "enum", "protocol", "extension", "actor",
            "init", "deinit", "subscript", "typealias", "associatedtype", "public", "private",
            "fileprivate", "internal", "open", "static", "final", "override", "mutating", "lazy",
            "weak", "unowned", "async", "throws", "rethrows", "inout", "some", "any", "self",
            "Self", "super", "true", "false", "nil", "is", "as"
        ),
        controlKeywords = setOf(
            "import", "if", "else", "guard", "switch", "case", "default", "for", "in", "while",
            "repeat", "return", "break", "continue", "fallthrough", "do", "try", "catch", "throw",
            "defer", "await"
        ),
        builtinTypes = setOf(
            "Int", "Double", "Float", "Bool", "String", "Character", "Void", "Any", "Array", "Dictionary", "Set"
        ),
        tripleQuotes = true
    )

    private val DART_RULES = LangRules(
        keywords = setOf(
            "class", "mixin", "enum", "extension", "typedef", "extends", "implements", "with",
            "abstract", "final", "const", "var", "late", "static", "factory", "get", "set",
            "operator", "async", "sync", "external", "covariant", "required", "new", "this",
            "super", "true", "false", "null", "void", "is", "as"
        ),
        controlKeywords = setOf(
            "import", "export", "part", "library", "show", "hide", "if", "else", "switch", "case",
            "default", "for", "in", "while", "do", "return", "break", "continue", "try", "on",
            "catch", "finally", "throw", "rethrow", "await", "yield"
        ),
        builtinTypes = setOf(
            "int", "double", "num", "bool", "String", "dynamic", "Object", "List", "Map", "Set", "Future", "Stream"
        ),
        tripleQuotes = true
    )

    private val PHP_RULES = LangRules(
        keywords = setOf(
            "function", "fn", "class", "interface", "trait", "enum", "extends", "implements",
            "public", "private", "protected", "static", "final", "abstract", "readonly", "const",
            "var", "new", "clone", "instanceof", "echo", "print", "isset", "unset", "empty",
            "true", "false", "null", "array", "callable"
        ),
        controlKeywords = setOf(
            "namespace", "use", "require", "require_once", "include", "include_once",
            "if", "elseif", "else", "switch", "case", "default", "match", "for", "foreach",
            "as", "while", "do", "return", "break", "continue", "try", "catch", "finally", "throw", "yield"
        ),
        builtinTypes = setOf(
            "int", "float", "string", "bool", "void", "mixed", "never", "object", "iterable"
        ),
        hashComments = true,
        dollarVars = true
    )

    private val SHELL_RULES = LangRules(
        keywords = setOf(
            "function", "local", "export", "readonly", "declare", "typeset", "unset", "alias",
            "true", "false", "echo", "printf", "read", "test", "cd", "pwd", "exit", "set", "shift"
        ),
        controlKeywords = setOf(
            "if", "then", "elif", "else", "fi", "case", "esac", "for", "in", "while", "until",
            "do", "done", "return", "break", "continue", "source"
        ),
        builtinTypes = emptySet(),
        slashComments = false,
        hashComments = true,
        backtickStrings = true,
        dollarVars = true
    )

    private val DOCKERFILE_RULES = LangRules(
        keywords = setOf(
            "FROM", "AS", "ARG", "ENV", "WORKDIR", "COPY", "ADD", "RUN", "CMD", "ENTRYPOINT",
            "EXPOSE", "VOLUME", "USER", "LABEL", "HEALTHCHECK", "STOPSIGNAL", "SHELL", "ONBUILD"
        ),
        controlKeywords = emptySet(),
        builtinTypes = emptySet(),
        slashComments = false,
        hashComments = true,
        dollarVars = true,
        caseInsensitive = true
    )
    // endregion
}
