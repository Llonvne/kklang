package cn.llonvne.kklang.spec

import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompilerPipelineSpecTest {
    @Test
    fun `compiler pipeline spec records phase order and result surface`() {
        assertEquals(
            listOf("lexing", "parsing", "lowering"),
            minimalCompilerPipelineSpec.phases,
        )
        assertEquals(
            listOf("CompiledProgram", "CompilationResult.Success", "CompilationResult.Failure"),
            minimalCompilerPipelineSpec.resultTypes,
        )
    }

    @Test
    fun `compiler pipeline spec records stop rules`() {
        assertEquals(
            listOf(
                "lexer diagnostics stop before parsing",
                "parser diagnostics stop before lowering",
                "lowering diagnostics stop before execution",
            ),
            minimalCompilerPipelineSpec.stopRules,
        )
    }

    @Test
    fun `compiler pipeline spec records internal diagnostics`() {
        assertEquals(
            listOf(DiagnosticSpec("COMPILER001", "internal compiler contract violation")),
            minimalCompilerPipelineSpec.diagnostics,
        )
    }

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
