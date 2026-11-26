package com.pocketworker.app.ui.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeSyntaxHighlighterTest {

    @Test
    fun `detectLanguage resolves extensions and special filenames`() {
        assertEquals(CodeLanguage.KOTLIN, CodeSyntaxHighlighter.detectLanguage("app/src/Main.kt"))
        assertEquals(CodeLanguage.TYPESCRIPT, CodeSyntaxHighlighter.detectLanguage("web/index.tsx"))
        assertEquals(CodeLanguage.PYTHON, CodeSyntaxHighlighter.detectLanguage("scripts/run.py"))
        assertEquals(CodeLanguage.HTML_XML, CodeSyntaxHighlighter.detectLanguage("res/layout/activity_main.xml"))
        assertEquals(CodeLanguage.JSON, CodeSyntaxHighlighter.detectLanguage("package.json"))
        assertEquals(CodeLanguage.YAML, CodeSyntaxHighlighter.detectLanguage(".github/workflows/ci.yml"))
        assertEquals(CodeLanguage.MARKDOWN, CodeSyntaxHighlighter.detectLanguage("README.md"))
        assertEquals(CodeLanguage.DOCKERFILE, CodeSyntaxHighlighter.detectLanguage("Dockerfile"))
        assertEquals(CodeLanguage.SHELL, CodeSyntaxHighlighter.detectLanguage("gradlew"))
        assertEquals(CodeLanguage.TOML_INI, CodeSyntaxHighlighter.detectLanguage(".env.local"))
        assertEquals(CodeLanguage.PLAIN_TEXT, CodeSyntaxHighlighter.detectLanguage("notes.unknownext"))
    }

    @Test
    fun `highlights Kotlin keywords, strings, functions, annotations and multiline comments across lines`() {
        val code = """
            @Composable
            fun greet(name: String) {
                /* line one
                   line two */
                val msg = "Hello"
                return
            }
        """.trimIndent()

        val lines = CodeSyntaxHighlighter.highlightLines(code, CodeLanguage.KOTLIN, isDark = true)
        assertEquals(7, lines.size)

        // @Composable annotation on line 0
        assertTrue(lines[0].spanStyles.any { it.item.color == VsCodePalettes.Dark.annotation })
        // fun keyword + greet function + String type on line 1
        assertTrue(lines[1].spanStyles.any { it.item.color == VsCodePalettes.Dark.keyword })
        assertTrue(lines[1].spanStyles.any { it.item.color == VsCodePalettes.Dark.function })
        assertTrue(lines[1].spanStyles.any { it.item.color == VsCodePalettes.Dark.type })
        // multiline comment spans both line 2 and line 3
        assertTrue(lines[2].spanStyles.any { it.item.color == VsCodePalettes.Dark.comment })
        assertTrue(lines[3].spanStyles.any { it.item.color == VsCodePalettes.Dark.comment })
        // string literal on line 4
        assertTrue(lines[4].spanStyles.any { it.item.color == VsCodePalettes.Dark.string })
        // control keyword 'return' on line 5
        assertTrue(lines[5].spanStyles.any { it.item.color == VsCodePalettes.Dark.controlKeyword })
    }

    @Test
    fun `highlights JSON keys separately from string values and numbers`() {
        val json = """{"name": "PocketWorker", "count": 42, "active": true}"""
        val highlighted = CodeSyntaxHighlighter.highlight(json, CodeLanguage.JSON, isDark = false)

        assertTrue(highlighted.spanStyles.any { it.item.color == VsCodePalettes.Light.property })
        assertTrue(highlighted.spanStyles.any { it.item.color == VsCodePalettes.Light.string })
        assertTrue(highlighted.spanStyles.any { it.item.color == VsCodePalettes.Light.number })
        assertTrue(highlighted.spanStyles.any { it.item.color == VsCodePalettes.Light.keyword })
    }

    @Test
    fun `highlights XML tags, attributes, values and Markdown headings`() {
        val xml = """<TextView android:text="Hi" />"""
        val xmlStyled = CodeSyntaxHighlighter.highlight(xml, CodeLanguage.HTML_XML, isDark = true)
        assertTrue(xmlStyled.spanStyles.any { it.item.color == VsCodePalettes.Dark.tag })
        assertTrue(xmlStyled.spanStyles.any { it.item.color == VsCodePalettes.Dark.attribute })
        assertTrue(xmlStyled.spanStyles.any { it.item.color == VsCodePalettes.Dark.string })

        val md = "# Title\nUse `git status` and [docs](https://example.com)"
        val mdLines = CodeSyntaxHighlighter.highlightLines(md, CodeLanguage.MARKDOWN, isDark = true)
        assertTrue(mdLines[0].spanStyles.any { it.item.color == VsCodePalettes.Dark.markdownHeading })
        assertTrue(mdLines[1].spanStyles.any { it.item.color == VsCodePalettes.Dark.string })
        assertTrue(mdLines[1].spanStyles.any { it.item.color == VsCodePalettes.Dark.markdownLink })
    }
}
