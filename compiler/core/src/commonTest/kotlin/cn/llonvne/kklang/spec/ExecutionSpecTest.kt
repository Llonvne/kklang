package cn.llonvne.kklang.spec

import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExecutionSpecTest {
    @Test
    fun `execution dsl spec records pipeline phases`() {
        assertEquals(
            listOf("lexer", "parser", "core ir lowering", "core ir evaluation"),
            minimalExecutionSpec.phases,
        )
    }

    @Test
    fun `execution dsl spec records first core ir surface`() {
        assertEquals(listOf("IrInt64", "IrUnary", "IrBinary"), minimalExecutionSpec.irNodes)
    }

    @Test
    fun `execution dsl spec records current executable forms`() {
        assertEquals(
            listOf(
                "integer literal",
                "grouped expression",
                "unary plus",
                "unary minus",
                "binary plus",
                "binary minus",
                "binary multiply",
                "binary divide",
            ),
            minimalExecutionSpec.supportedForms,
        )
    }

    @Test
    fun `markdown execution spec contains dsl ir and diagnostics`() {
        val markdown = Path("../../spec/execution.md").readText()
        val expectedTerms = minimalExecutionSpec.irNodes + minimalExecutionSpec.diagnostics.map { it.code }

        for (term in expectedTerms) {
            assertTrue(markdown.contains(term), "missing $term in Markdown execution spec")
        }
    }
}
