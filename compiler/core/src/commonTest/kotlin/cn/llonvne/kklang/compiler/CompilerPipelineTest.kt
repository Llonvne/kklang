package cn.llonvne.kklang.compiler

import cn.llonvne.kklang.execution.CoreIrEvaluator
import cn.llonvne.kklang.execution.EvaluationResult
import cn.llonvne.kklang.execution.ExecutionEngine
import cn.llonvne.kklang.execution.ExecutionResult
import cn.llonvne.kklang.execution.ExecutionValue
import cn.llonvne.kklang.execution.IrEvaluator
import cn.llonvne.kklang.execution.IrInt64
import cn.llonvne.kklang.execution.IrLowerer
import cn.llonvne.kklang.execution.IrLoweringResult
import cn.llonvne.kklang.frontend.SourceSpan
import cn.llonvne.kklang.frontend.SourceText
import cn.llonvne.kklang.frontend.parsing.Parser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CompilerPipelineTest {
    @Test
    fun `pipeline compiles seed expression into compiled program`() {
        val result = CompilerPipeline().compile(CompilationInput(SourceText.of("sample.kk", "1 + 2 * 3")))

        assertIs<CompilationResult.Success>(result)
        assertEquals(listOf(CompilerPhase.Lexing, CompilerPhase.Parsing, CompilerPhase.Lowering), result.phaseTrace)
        assertEquals(SourceSpan("sample.kk", 0, 9), result.program.span)
        assertFalse(result.hasErrors)
    }

    @Test
    fun `pipeline stops before parsing when lexing fails`() {
        var parserWasRequested = false
        val result = CompilerPipeline(
            parserFactory = {
                parserWasRequested = true
                Parser(it)
            },
        ).compile(CompilationInput(SourceText.of("sample.kk", "@")))

        assertIs<CompilationResult.Failure>(result)
        assertEquals(listOf(CompilerPhase.Lexing), result.phaseTrace)
        assertEquals(listOf("LEX001"), result.diagnostics.map { it.code })
        assertFalse(parserWasRequested)
        assertTrue(result.hasErrors)
    }

    @Test
    fun `pipeline stops before lowering when parsing fails`() {
        var lowererWasCalled = false
        val result = CompilerPipeline(
            lowerer = IrLowerer {
                lowererWasCalled = true
                IrLoweringResult(IrInt64(1, it.span), emptyList())
            },
        ).compile(CompilationInput(SourceText.of("sample.kk", "1 +")))

        assertIs<CompilationResult.Failure>(result)
        assertEquals(listOf(CompilerPhase.Lexing, CompilerPhase.Parsing), result.phaseTrace)
        assertEquals(listOf("PARSE001"), result.diagnostics.map { it.code })
        assertFalse(lowererWasCalled)
    }

    @Test
    fun `pipeline returns lowering diagnostics without compiled program`() {
        val source = SourceText.of("sample.kk", "name")
        val result = CompilerPipeline().compile(CompilationInput(source))

        assertIs<CompilationResult.Failure>(result)
        assertEquals(listOf(CompilerPhase.Lexing, CompilerPhase.Parsing, CompilerPhase.Lowering), result.phaseTrace)
        assertEquals(listOf("EXEC001"), result.diagnostics.map { it.code })
    }

    @Test
    fun `pipeline rejects lowerer success without ir`() {
        val result = CompilerPipeline(
            lowerer = IrLowerer { IrLoweringResult(null, emptyList()) },
        ).compile(CompilationInput(SourceText.of("sample.kk", "1")))

        assertIs<CompilationResult.Failure>(result)
        assertEquals(listOf("COMPILER001"), result.diagnostics.map { it.code })
    }

    @Test
    fun `execution engine uses pipeline and does not evaluate failed compilation`() {
        var evaluatorWasCalled = false
        val result = ExecutionEngine(
            compiler = CompilerPipeline(),
            evaluator = IrEvaluator {
                evaluatorWasCalled = true
                EvaluationResult(ExecutionValue.Int64(1), emptyList())
            },
        ).execute(SourceText.of("sample.kk", "@"))

        assertIs<ExecutionResult.Failure>(result)
        assertEquals(listOf("LEX001"), result.diagnostics.map { it.code })
        assertFalse(evaluatorWasCalled)
    }

    @Test
    fun `execution engine evaluates compiled program`() {
        val result = ExecutionEngine(
            compiler = CompilerPipeline(),
            evaluator = CoreIrEvaluator(),
        ).execute(SourceText.of("sample.kk", "1 + 2"))

        assertIs<ExecutionResult.Success>(result)
        assertEquals(ExecutionValue.Int64(3), result.value)
    }

    @Test
    fun `internal diagnostics preserve source spans`() {
        val source = SourceText.of("sample.kk", "1")
        val result = CompilerPipeline(
            lowerer = IrLowerer { IrLoweringResult(null, emptyList()) },
        ).compile(CompilationInput(source))

        assertIs<CompilationResult.Failure>(result)
        assertEquals(SourceSpan("sample.kk", 0, 1), result.diagnostics.single().span)
    }
}
