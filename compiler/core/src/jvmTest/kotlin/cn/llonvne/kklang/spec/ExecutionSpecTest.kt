package cn.llonvne.kklang.spec

import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 覆盖 execution Kotlin DSL 规范和 Markdown 规范的一致性。
 * Covers consistency between the execution Kotlin DSL spec and Markdown spec.
 */
class ExecutionSpecTest {
    /**
     * 验证 execution DSL 记录当前 pipeline 阶段。
     * Verifies that the execution DSL records the current pipeline phases.
     */
    @Test
    fun `execution dsl spec records pipeline phases`() {
        assertEquals(
            listOf("lexer", "parser", "modifier expansion", "binding", "type checking", "core ir lowering", "core ir evaluation"),
            minimalExecutionSpec.phases,
        )
    }

    /**
     * 验证 execution DSL 记录第一批 Core IR 节点。
     * Verifies that the execution DSL records the first Core IR nodes.
     */
    @Test
    fun `execution dsl spec records first core ir surface`() {
        assertEquals(
            listOf(
                "IrProgram",
                "IrValDeclaration",
                "IrFunctionDeclaration",
                "IrInt64",
                "IrString",
                "IrPrint",
                "IrVariable",
                "IrCall",
                "IrUnary",
                "IrBinary",
            ),
            minimalExecutionSpec.irNodes,
        )
    }

    /**
     * 验证 execution DSL 记录当前可执行源码形式。
     * Verifies that the execution DSL records currently executable source forms.
     */
    @Test
    fun `execution dsl spec records current executable forms`() {
        assertEquals(
            listOf(
                "immutable val declaration",
                "top-level function declaration",
                "function call",
                "identifier reference",
                "integer literal",
                "string literal",
                "builtin print call",
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

    /**
     * 验证 Markdown execution 规范包含 DSL 中的 IR 和 diagnostic 项。
     * Verifies that the Markdown execution spec contains IR and diagnostic items from the DSL.
     */
    @Test
    fun `markdown execution spec contains dsl ir and diagnostics`() {
        val markdown = Path("../../spec/execution.md").readText()
        val expectedTerms = minimalExecutionSpec.irNodes + minimalExecutionSpec.diagnostics.map { it.code }

        for (term in expectedTerms) {
            assertTrue(markdown.contains(term), "missing $term in Markdown execution spec")
        }
    }
}
