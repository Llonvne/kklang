package cn.llonvne.kklang.spec

import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FrontendInfrastructureSpecTest {
    @Test
    fun `kotlin dsl spec records current frontend token surface`() {
        assertEquals(
            listOf(
                "identifier",
                "integer",
                "left_paren",
                "right_paren",
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

    @Test
    fun `kotlin dsl spec records current parser surface`() {
        assertEquals(
            listOf(
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

    @Test
    fun `markdown spec contains the same diagnostic codes as dsl spec`() {
        val markdown = Path("spec/frontend-infrastructure.md").readText()

        for (diagnostic in frontendInfrastructureSpec.diagnostics) {
            assertTrue(markdown.contains(diagnostic.code), "missing ${diagnostic.code} in Markdown spec")
        }
    }
}

