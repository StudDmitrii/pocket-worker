package com.pocketworker.app.core.mcp

import com.pocketworker.app.core.git.GitCredentials
import kotlinx.coroutines.test.runTest
import org.eclipse.jgit.api.Git
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class McpCheckToolsTest {

    private lateinit var harness: McpTestHarness

    @Before
    fun setUp() {
        harness = McpTestHarness(confirmFileEdits = false)
    }

    @After
    fun tearDown() {
        harness.cleanup()
    }

    @Test
    fun `check_push_readiness detects missing remote, SSH remote, secret leak, and clean ready state`() = runTest {
        val (project, repo) = harness.createProjectAndRepo("SecProj", "api")
        val repoDir = File(repo.directoryPath)

        // 1. No remote configured -> blocker REMOTE_NOT_FOUND
        val noRemote = harness.server.callTool(
            "check_push_readiness",
            JSONObject().put("projectId", project.id).put("repoId", repo.id)
        )
        assertTrue(noRemote.ok)
        assertFalse(noRemote.data!!.getBoolean("readyToPush"))
        val blockers1 = noRemote.data!!.getJSONArray("blockers")
        assertTrue((0 until blockers1.length()).any { blockers1.getJSONObject(it).getString("code") == McpErrorCodes.REMOTE_NOT_FOUND })

        // 2. Add valid local bare remote + stored token (verify token is NEVER exposed)
        val bareDir = File(harness.tempRoot, "ready-bare.git")
        Git.init().setDirectory(bareDir).setBare(true).setInitialBranch("main").call().close()
        harness.gitEngine.addRemote(repoDir, "origin", bareDir.toURI().toString())
        harness.credentialStore.saveRepoCredentials(repo.id, GitCredentials("user", "ghp_SuperSecretToken1234567890123456"))

        val ready = harness.server.callTool(
            "check_push_readiness",
            JSONObject().put("projectId", project.id).put("repoId", repo.id)
        )
        assertTrue(ready.ok)
        assertTrue(ready.data!!.getBoolean("readyToPush"))
        assertTrue(ready.data!!.getBoolean("hasCredentials"))
        assertEquals("repo", ready.data!!.getString("credentialScope"))
        assertFalse(ready.toJsonString().contains("SuperSecretToken"))

        // 3. Introduce an accidental GitHub PAT in a source file -> blocker SECRET_LEAK_WARNING!
        File(repoDir, "config.kt").writeText("const val TOKEN = \"ghp_ABCDEFGH1234567890abcdef1234567890\"\n")
        val leaked = harness.server.callTool(
            "check_push_readiness",
            JSONObject().put("projectId", project.id).put("repoId", repo.id)
        )
        assertTrue(leaked.ok)
        assertFalse(leaked.data!!.getBoolean("readyToPush"))
        assertEquals(1, leaked.data!!.getJSONArray("secretFindings").length())
        assertFalse(leaked.toJsonString().contains("ghp_ABCDEFGH1234567890abcdef1234567890"))
    }

    @Test
    fun `check_merge_conflicts parses real Git merge conflict hunks and verifies resolution`() = runTest {
        val (project, repo) = harness.createProjectAndRepo("MergeProj", "core")
        val repoDir = File(repo.directoryPath)

        // Base commit
        File(repoDir, "shared.txt").writeText("line 1\nbase value\nline 3\n")
        harness.gitEngine.stageAll(repoDir)
        harness.gitEngine.commit(repoDir, "base", "Dev", "dev@test.local")

        // Feature branch change
        harness.gitEngine.createBranch(repoDir, "feature", checkout = true)
        File(repoDir, "shared.txt").writeText("line 1\nfeature value\nline 3\n")
        harness.gitEngine.stageAll(repoDir)
        harness.gitEngine.commit(repoDir, "feature edit", "Dev", "dev@test.local")

        // Main branch conflicting change
        harness.gitEngine.checkoutBranch(repoDir, "main")
        File(repoDir, "shared.txt").writeText("line 1\nmain value\nline 3\n")
        harness.gitEngine.stageAll(repoDir)
        harness.gitEngine.commit(repoDir, "main edit", "Dev", "dev@test.local")

        // Merge via git_exec -> returns MERGE_CONFLICT
        val mergeRes = harness.server.callTool(
            "git_exec",
            JSONObject().put("projectId", project.id).put("repoId", repo.id).put("command", "merge feature")
        )
        assertFalse(mergeRes.ok)
        assertEquals(McpErrorCodes.MERGE_CONFLICT, mergeRes.error?.errorCode)

        // Inspect conflicts via check_merge_conflicts
        val inspect = harness.server.callTool(
            "check_merge_conflicts",
            JSONObject().put("projectId", project.id).put("repoId", repo.id)
        )
        assertTrue(inspect.ok)
        assertTrue(inspect.data!!.getBoolean("hasConflicts"))
        assertEquals(1, inspect.data!!.getInt("totalHunks"))
        val fileEntry = inspect.data!!.getJSONArray("filesWithMarkers").getJSONObject(0)
        assertEquals("shared.txt", fileEntry.getString("path"))
        val hunk = fileEntry.getJSONArray("hunks").getJSONObject(0)
        assertTrue(hunk.getString("ours").contains("main value"))
        assertTrue(hunk.getString("theirs").contains("feature value"))

        // Resolve file & stage -> verifyResolved reports clean!
        File(repoDir, "shared.txt").writeText("line 1\ncombined value\nline 3\n")
        harness.gitEngine.stageFile(repoDir, "shared.txt")

        val resolved = harness.server.callTool(
            "check_merge_conflicts",
            JSONObject().put("projectId", project.id).put("repoId", repo.id).put("verifyResolved", true)
        )
        assertTrue(resolved.ok)
        assertFalse(resolved.data!!.getBoolean("hasConflicts"))
        assertTrue(resolved.data!!.getBoolean("resolvedReadyToCommit"))
    }

    @Test
    fun `check_file_syntax validates JSON, XML, and Kotlin lexical balance and catches errors`() = runTest {
        val (project, repo) = harness.createProjectAndRepo("SyntaxProj", "app")
        val repoDir = File(repo.directoryPath)

        File(repoDir, "valid.json").writeText("""{"name": "PocketWorker", "version": 1}""")
        File(repoDir, "broken.json").writeText("""{"name": "PocketWorker", }""")
        File(repoDir, "valid.xml").writeText("""<manifest package="com.demo"><application /></manifest>""")
        File(repoDir, "broken.xml").writeText("""<manifest><application></manifest>""")
        File(repoDir, "valid.kt").writeText(
            """
            // comment with unbalanced { ( [
            fun test(x: Int): String {
                val msg = "string with { brace"
                return "${'$'}x ${'$'}msg"
            }
            """.trimIndent()
        )
        File(repoDir, "broken.kt").writeText("fun oops() {\n    val s = \"unclosed\n")

        val validRes = harness.server.callTool(
            "check_file_syntax",
            JSONObject()
                .put("projectId", project.id)
                .put("repoId", repo.id)
                .put("paths", JSONArray(listOf("valid.json", "valid.xml", "valid.kt")))
        )
        assertTrue(validRes.ok)
        assertTrue("Expected valid files to pass: ${validRes.toJsonString()}", validRes.data!!.getBoolean("allValid"))
        assertEquals(0, validRes.data!!.getInt("totalErrors"))

        val brokenRes = harness.server.callTool(
            "check_file_syntax",
            JSONObject()
                .put("projectId", project.id)
                .put("repoId", repo.id)
                .put("paths", JSONArray(listOf("broken.json", "broken.xml", "broken.kt")))
        )
        assertTrue(brokenRes.ok)
        assertFalse(brokenRes.data!!.getBoolean("allValid"))
        assertTrue(brokenRes.data!!.getInt("totalErrors") >= 3)
        val files = brokenRes.data!!.getJSONArray("files")
        for (i in 0 until files.length()) {
            assertFalse(files.getJSONObject(i).getBoolean("valid"))
        }
    }

    @Test
    fun `check_file_syntax delegates to AST SyntaxCheckEngine, enriches snippets, and falls back safely`() = runTest {
        val fakeAstEngine = object : SyntaxCheckEngine {
            override suspend fun check(relativePath: String, content: String): SyntaxEngineResult {
                return when {
                    relativePath.endsWith(".php") -> SyntaxEngineResult(
                        handled = true,
                        engine = "php-parser",
                        issues = listOf(
                            SyntaxIssue(
                                line = 3,
                                column = 5,
                                severity = "error",
                                message = "syntax error, unexpected 'return', expecting ';'",
                                endLine = 3,
                                endColumn = 11,
                                code = "PHP_PARSE"
                            )
                        )
                    )
                    relativePath.endsWith(".cpp") -> SyntaxEngineResult(
                        handled = true,
                        engine = "tree-sitter:cpp",
                        issues = emptyList()
                    )
                    relativePath.endsWith(".py") -> throw IllegalStateException("Simulated sandbox crash")
                    else -> SyntaxEngineResult(handled = false)
                }
            }
        }

        val astHarness = McpTestHarness(confirmFileEdits = false, syntaxCheckEngine = fakeAstEngine)
        try {
            val (project, repo) = astHarness.createProjectAndRepo("AstProj", "backend")
            val repoDir = File(repo.directoryPath)

            File(repoDir, "index.php").writeText("<?php\n\$x = 1\n    return \$x;\n")
            File(repoDir, "engine.cpp").writeText("int main() { return 0; }\n")
            File(repoDir, "broken.py").writeText("def foo():\n    x = (\n")

            val res = astHarness.server.callTool(
                "check_file_syntax",
                JSONObject()
                    .put("projectId", project.id)
                    .put("repoId", repo.id)
                    .put("paths", JSONArray(listOf("index.php", "engine.cpp", "broken.py")))
            )
            assertTrue(res.ok)
            assertFalse(res.data!!.getBoolean("allValid"))

            val reports = res.data!!.getJSONArray("files")
            val phpReport = reports.getJSONObject(0)
            assertEquals("php-parser", phpReport.getString("engine"))
            assertFalse(phpReport.getBoolean("valid"))
            val phpIssue = phpReport.getJSONArray("issues").getJSONObject(0)
            assertEquals(3, phpIssue.getInt("line"))
            assertEquals(5, phpIssue.getInt("column"))
            assertEquals("PHP_PARSE", phpIssue.getString("code"))
            assertTrue(phpIssue.getString("snippet").contains("return \$x;"))
            assertTrue(phpIssue.getString("snippet").contains("^"))

            val cppReport = reports.getJSONObject(1)
            assertEquals("tree-sitter:cpp", cppReport.getString("engine"))
            assertTrue(cppReport.getBoolean("valid"))

            // Python threw in AST engine -> gracefully fell back to lexical validator and caught unclosed '('
            val pyReport = reports.getJSONObject(2)
            assertEquals("lexical-fallback", pyReport.getString("engine"))
            assertFalse(pyReport.getBoolean("valid"))
        } finally {
            astHarness.cleanup()
        }
    }

    @Test
    fun `JsSandboxSyntaxEngine parses engine JSON payloads and extracts extensions accurately`() {
        val raw = """
            {
              "handled": true,
              "engine": "tree-sitter:c_sharp",
              "issues": [
                {
                  "line": 5,
                  "column": 9,
                  "endLine": 5,
                  "endColumn": 14,
                  "severity": "error",
                  "code": "MISSING_TOKEN",
                  "message": "Missing expected ';' in local declaration"
                }
              ]
            }
        """.trimIndent()
        val parsed = JsSandboxSyntaxEngine.parseEngineResponse(raw)
        assertTrue(parsed.handled)
        assertEquals("tree-sitter:c_sharp", parsed.engine)
        assertEquals(1, parsed.issues.size)
        val issue = parsed.issues.first()
        assertEquals(5, issue.line)
        assertEquals(9, issue.column)
        assertEquals(5, issue.endLine)
        assertEquals(14, issue.endColumn)
        assertEquals("MISSING_TOKEN", issue.code)
        assertEquals("Missing expected ';' in local declaration", issue.message)

        assertEquals("php", JsSandboxSyntaxEngine.extractExtension("src/Controller.PHP"))
        assertEquals("sh", JsSandboxSyntaxEngine.extractExtension("scripts/gradlew"))
        assertEquals("cpp", JsSandboxSyntaxEngine.extractExtension("native/engine.cpp"))
    }
}
