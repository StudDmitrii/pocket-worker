package com.pocketworker.app.ui.ai

import com.pocketworker.app.ui.workspace.CodeLanguage
import com.pocketworker.app.ui.workspace.CodeSyntaxHighlighter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiMarkdownParserTest {

    @Test
    fun `parses headings with inline formatting and ignores hash without space`() {
        val md = """
            # Главный заголовок
            ### Метод `sendMessage` и **важно**
            #include <stdio.h>
        """.trimIndent()

        val blocks = AiMarkdownParser.parse(md)
        assertEquals(3, blocks.size)

        val h1 = blocks[0] as MdBlock.Heading
        assertEquals(1, h1.level)
        assertEquals(listOf(MdInline.Text("Главный заголовок")), h1.inlines)

        val h3 = blocks[1] as MdBlock.Heading
        assertEquals(3, h3.level)
        assertTrue(h3.inlines.any { it is MdInline.InlineCode && it.code == "sendMessage" })
        assertTrue(h3.inlines.any { it is MdInline.Bold })

        val para = blocks[2] as MdBlock.Paragraph
        assertEquals(listOf(MdInline.Text("#include <stdio.h>")), para.inlines)
    }

    @Test
    fun `parses fenced code blocks including unclosed streaming fences and nested backticks`() {
        val closedMd = """
            Пример кода:
            ```kotlin
            fun greet(name: String) = "Hi, ${'$'}name"
            ```
        """.trimIndent()

        val closedBlocks = AiMarkdownParser.parse(closedMd)
        assertEquals(2, closedBlocks.size)
        val code = closedBlocks[1] as MdBlock.CodeBlock
        assertEquals("kotlin", code.language)
        assertTrue(code.isClosed)
        assertEquals("fun greet(name: String) = \"Hi, \$name\"", code.code)

        // Streaming partial unclosed code fence
        val streamingMd = "```python\ndef add(a, b):\n    return a + b"
        val streamingBlocks = AiMarkdownParser.parse(streamingMd)
        assertEquals(1, streamingBlocks.size)
        val partialCode = streamingBlocks.single() as MdBlock.CodeBlock
        assertEquals("python", partialCode.language)
        assertFalse(partialCode.isClosed)
        assertTrue(partialCode.code.contains("return a + b"))

        // Four-backtick fence containing triple-backticks inside
        val nestedMd = "````markdown\n```kt\nval x = 1\n```\n````"
        val nestedBlocks = AiMarkdownParser.parse(nestedMd)
        assertEquals(1, nestedBlocks.size)
        val nestedCode = nestedBlocks.single() as MdBlock.CodeBlock
        assertEquals("markdown", nestedCode.language)
        assertTrue(nestedCode.isClosed)
        assertEquals("```kt\nval x = 1\n```", nestedCode.code)
    }

    @Test
    fun `parses GFM tables with alignments, inline code pipes, and br line breaks`() {
        val md = """
            | Файл | Статус | Строк |
            | :--- | :---: | ---: |
            | `App.kt` | **OK** | 42 |
            | `a | b` | Строка 1<br>Строка 2 | 7 |
        """.trimIndent()

        val blocks = AiMarkdownParser.parse(md)
        assertEquals(1, blocks.size)
        val table = blocks.single() as MdBlock.Table

        assertEquals(3, table.headers.size)
        assertEquals(
            listOf(MdColumnAlignment.LEFT, MdColumnAlignment.CENTER, MdColumnAlignment.RIGHT),
            table.alignments
        )
        assertEquals(2, table.rows.size)

        // Row 0
        assertEquals(listOf(MdInline.InlineCode("App.kt")), table.rows[0][0])
        assertEquals(listOf(MdInline.Bold(listOf(MdInline.Text("OK")))), table.rows[0][1])
        assertEquals(listOf(MdInline.Text("42")), table.rows[0][2])

        // Row 1: pipe inside backticks kept intact, <br> converted to LineBreak
        assertEquals(listOf(MdInline.InlineCode("a | b")), table.rows[1][0])
        assertEquals(
            listOf(MdInline.Text("Строка 1"), MdInline.LineBreak, MdInline.Text("Строка 2")),
            table.rows[1][1]
        )
        assertEquals(listOf(MdInline.Text("7")), table.rows[1][2])
    }

    @Test
    fun `parses mixed nested lists, task checklists, and horizontal rules`() {
        val md = """
            1. Первый шаг
               - Подпункт A
                 - Глубокий подпункт
            2. Второй шаг

            ---

            - [x] Сделано
            - [ ] Осталось
        """.trimIndent()

        val blocks = AiMarkdownParser.parse(md)
        assertEquals(3, blocks.size)

        val list1 = blocks[0] as MdBlock.ListBlock
        assertEquals(4, list1.items.size)
        assertEquals(MdListMarker.Ordered(1), list1.items[0].marker)
        assertEquals(0, list1.items[0].indentLevel)
        assertEquals(MdListMarker.Bullet, list1.items[1].marker)
        assertEquals(1, list1.items[1].indentLevel)
        assertEquals(MdListMarker.Bullet, list1.items[2].marker)
        assertEquals(2, list1.items[2].indentLevel)
        assertEquals(MdListMarker.Ordered(2), list1.items[3].marker)
        assertEquals(0, list1.items[3].indentLevel)

        assertTrue(blocks[1] is MdBlock.HorizontalRule)

        val tasks = blocks[2] as MdBlock.ListBlock
        assertEquals(2, tasks.items.size)
        assertEquals(MdListMarker.Task(checked = true), tasks.items[0].marker)
        assertEquals(MdListMarker.Task(checked = false), tasks.items[1].marker)
    }

    @Test
    fun `parses blockquotes and GitHub callouts`() {
        val md = """
            > [!WARNING]
            > Не забудьте сделать **коммит** перед `git reset`.

            > Обычная цитата
            > на две строки
        """.trimIndent()

        val blocks = AiMarkdownParser.parse(md)
        assertEquals(2, blocks.size)

        val warning = blocks[0] as MdBlock.Blockquote
        assertEquals(MdCalloutType.WARNING, warning.callout)
        assertEquals(1, warning.blocks.size)
        val warnPara = warning.blocks.single() as MdBlock.Paragraph
        assertTrue(warnPara.inlines.any { it is MdInline.Bold })
        assertTrue(warnPara.inlines.any { it is MdInline.InlineCode && it.code == "git reset" })

        val quote = blocks[1] as MdBlock.Blockquote
        assertEquals(null, quote.callout)
        assertEquals(1, quote.blocks.size)
    }

    @Test
    fun `parses rich inline styles, nested emphasis, links, autolinks, and protects code identifiers`() {
        val inlines = AiMarkdownParser.parseInlines(
            "Проверь my_snake_case_var и 2 * 3 * 4, а также **жирный с `code`**, " +
                "***жирный курсив***, ~~старое~~, [Wiki](https://en.wikipedia.org/wiki/Kotlin_(language)) " +
                "и https://example.com/docs."
        )

        val plainJoined = inlines.filterIsInstance<MdInline.Text>().joinToString("") { it.text }
        assertTrue("snake_case must stay untouched", plainJoined.contains("my_snake_case_var"))
        assertTrue("multiplication must stay untouched", plainJoined.contains("2 * 3 * 4"))

        val bold = inlines.filterIsInstance<MdInline.Bold>().single()
        assertTrue(bold.children.any { it is MdInline.InlineCode && it.code == "code" })

        assertTrue(inlines.any { it is MdInline.BoldItalic })
        assertTrue(inlines.any { it is MdInline.Strikethrough })

        val links = inlines.filterIsInstance<MdInline.Link>()
        assertEquals(2, links.size)
        assertEquals("https://en.wikipedia.org/wiki/Kotlin_(language)", links[0].url)
        assertEquals("https://example.com/docs", links[1].url)
    }

    @Test
    fun `handles escapes, nested bold inside italic, and unclosed streaming syntax safely`() {
        val escaped = AiMarkdownParser.parseInlines("""\*не курсив\* и \`не код\`""")
        assertEquals(listOf(MdInline.Text("*не курсив* и `не код`")), escaped)

        val nested = AiMarkdownParser.parseInlines("***жирный** и курсив*")
        assertEquals(1, nested.size)
        val italic = nested.single() as MdInline.Italic
        assertEquals(MdInline.Bold(listOf(MdInline.Text("жирный"))), italic.children[0])
        assertEquals(MdInline.Text(" и курсив"), italic.children[1])

        // Partial unclosed streaming text should never throw or lose text
        val partial = AiMarkdownParser.parseInlines("**печатается ответ `без закрытия")
        val flattened = partial.filterIsInstance<MdInline.Text>().joinToString("") { it.text }
        assertEquals("**печатается ответ `без закрытия", flattened)
    }

    @Test
    fun `detectLanguageByFence maps common markdown code fence tags`() {
        assertEquals(CodeLanguage.KOTLIN, CodeSyntaxHighlighter.detectLanguageByFence("kt"))
        assertEquals(CodeLanguage.KOTLIN, CodeSyntaxHighlighter.detectLanguageByFence("kotlin"))
        assertEquals(CodeLanguage.PYTHON, CodeSyntaxHighlighter.detectLanguageByFence("py"))
        assertEquals(CodeLanguage.TYPESCRIPT, CodeSyntaxHighlighter.detectLanguageByFence("tsx"))
        assertEquals(CodeLanguage.C_CPP, CodeSyntaxHighlighter.detectLanguageByFence("c++"))
        assertEquals(CodeLanguage.CSHARP, CodeSyntaxHighlighter.detectLanguageByFence("c#"))
        assertEquals(CodeLanguage.SHELL, CodeSyntaxHighlighter.detectLanguageByFence("bash"))
        assertEquals(CodeLanguage.YAML, CodeSyntaxHighlighter.detectLanguageByFence("yml"))
        assertEquals(CodeLanguage.DOCKERFILE, CodeSyntaxHighlighter.detectLanguageByFence("dockerfile"))
        assertEquals(CodeLanguage.PLAIN_TEXT, CodeSyntaxHighlighter.detectLanguageByFence(""))
    }
}
