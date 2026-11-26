package com.pocketworker.app.core.mcp

/**
 * A single syntax issue reported by either an AST parser engine or a lightweight fallback validator.
 */
data class SyntaxIssue(
    val line: Int,
    val column: Int,
    val severity: String,
    val message: String,
    val endLine: Int? = null,
    val endColumn: Int? = null,
    val code: String? = null
)

/**
 * Result returned by a [SyntaxCheckEngine] for a single file.
 * When [handled] is false, [CheckFileSyntaxTool] falls back to its built-in Kotlin lexical validator.
 */
data class SyntaxEngineResult(
    val handled: Boolean,
    val engine: String? = null,
    val issues: List<SyntaxIssue> = emptyList(),
    val fallbackReason: String? = null
)

/**
 * Pluggable offline AST syntax checker backend used by the `check_file_syntax` MCP tool.
 */
interface SyntaxCheckEngine {
    suspend fun check(relativePath: String, content: String): SyntaxEngineResult
    fun close() = Unit
}

/**
 * Default no-op engine used in pure JVM unit tests or when no AST backend is attached.
 */
object NoOpSyntaxCheckEngine : SyntaxCheckEngine {
    override suspend fun check(relativePath: String, content: String): SyntaxEngineResult =
        SyntaxEngineResult(handled = false, fallbackReason = "AST engine not configured")
}
