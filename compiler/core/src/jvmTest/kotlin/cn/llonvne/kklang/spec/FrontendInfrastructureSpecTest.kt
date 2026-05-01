package cn.llonvne.kklang.spec

import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 覆盖 frontend infrastructure Kotlin DSL 规范和 Markdown 规范的一致性。
 * Covers consistency between the frontend infrastructure Kotlin DSL spec and Markdown spec.
 */
class FrontendInfrastructureSpecTest {
    /**
     * 验证 DSL 记录当前 frontend token 表面。
     * Verifies that the DSL records the current frontend token surface.
     */
    @Test
    fun `kotlin dsl spec records current frontend token surface`() {
        assertEquals(
            listOf(
                "identifier",
                "integer",
                "val",
                "left_paren",
                "right_paren",
                "equals",
                "semicolon",
                "plus",
                "minus",
                "star",
                "slash",
                "whitespace",
                "unknown",
                "eof",
            ),
            frontendInfrastructureSpec.lexerTokens.map { it.kind },
        )
    }

    /**
     * 验证 DSL 记录当前 parser rule 表面。
     * Verifies that the DSL records the current parser rule surface.
     */
    @Test
    fun `kotlin dsl spec records current parser surface`() {
        assertEquals(
            listOf(
                "program",
                "val declaration",
                "identifier expression",
                "integer expression",
                "grouped expression",
                "prefix plus",
                "prefix minus",
                "add",
                "subtract",
                "multiply",
                "divide",
            ),
            frontendInfrastructureSpec.parserRules.map { it.name },
        )
        assertEquals(setOf("left"), frontendInfrastructureSpec.parserRules.mapNotNull { it.associativity }.toSet())
    }

    /**
     * 验证 Markdown frontend 规范包含 DSL 中的 diagnostic code。
     * Verifies that the Markdown frontend spec contains diagnostic codes from the DSL.
     */
    @Test
    fun `markdown spec contains the same diagnostic codes as dsl spec`() {
        val markdown = Path("../../spec/frontend-infrastructure.md").readText()

        for (diagnostic in frontendInfrastructureSpec.diagnostics) {
            assertTrue(markdown.contains(diagnostic.code), "missing ${diagnostic.code} in Markdown spec")
        }
    }
}
