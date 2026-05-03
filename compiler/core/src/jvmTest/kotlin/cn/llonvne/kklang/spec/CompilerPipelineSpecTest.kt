package cn.llonvne.kklang.spec

import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 覆盖 compiler pipeline Kotlin DSL 规范和 Markdown 规范的一致性。
 * Covers consistency between the compiler pipeline Kotlin DSL spec and Markdown spec.
 */
class CompilerPipelineSpecTest {
    /**
     * 验证 compiler pipeline DSL 记录阶段顺序和结果类型。
     * Verifies that the compiler pipeline DSL records phase order and result types.
     */
    @Test
    fun `compiler pipeline spec records phase order and result surface`() {
        assertEquals(
            listOf("lexing", "parsing", "modifier expansion", "binding", "type checking", "lowering"),
            minimalCompilerPipelineSpec.phases,
        )
        assertEquals(
            listOf("CompiledProgram", "CompilationResult.Success", "CompilationResult.Failure"),
            minimalCompilerPipelineSpec.resultTypes,
        )
    }

    /**
     * 验证 compiler pipeline DSL 记录阶段短路规则。
     * Verifies that the compiler pipeline DSL records phase stop rules.
     */
    @Test
    fun `compiler pipeline spec records stop rules`() {
        assertEquals(
            listOf(
                "lexer diagnostics stop before parsing",
                "parser diagnostics stop before modifier expansion",
                "modifier diagnostics stop before binding",
                "binding diagnostics stop before type checking",
                "type checker diagnostics stop before lowering",
                "lowering diagnostics stop before execution",
            ),
            minimalCompilerPipelineSpec.stopRules,
        )
    }

    /**
     * 验证 compiler pipeline DSL 记录内部 diagnostic。
     * Verifies that the compiler pipeline DSL records internal diagnostics.
     */
    @Test
    fun `compiler pipeline spec records internal diagnostics`() {
        assertEquals(
            listOf(DiagnosticSpec("COMPILER001", "internal compiler contract violation")),
            minimalCompilerPipelineSpec.diagnostics,
        )
    }

    /**
     * 验证 Markdown compiler pipeline 规范包含 DSL 暴露面。
     * Verifies that the Markdown compiler pipeline spec contains the DSL surface.
     */
    @Test
    fun `markdown compiler pipeline spec contains dsl surface`() {
        val markdown = Path("../../spec/compiler-pipeline.md").readText()
        val expectedTerms = minimalCompilerPipelineSpec.phases +
            minimalCompilerPipelineSpec.resultTypes +
            minimalCompilerPipelineSpec.diagnostics.map { it.code }

        for (term in expectedTerms) {
            assertTrue(markdown.contains(term), "missing $term in Markdown compiler pipeline spec")
        }
    }
}
