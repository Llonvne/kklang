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

/**
 * 覆盖 compiler pipeline 阶段顺序、短路行为和 execution engine 边界。
 * Covers compiler pipeline phase order, short-circuit behavior, and execution-engine boundaries.
 */
class CompilerPipelineTest {
    /**
     * 验证合法 seed expression 会编译成包含完整阶段轨迹的 compiled program。
     * Verifies that a valid seed expression compiles into a compiled program with the full phase trace.
     */
    @Test
    fun `pipeline compiles seed expression into compiled program`() {
        val result = CompilerPipeline().compile(CompilationInput(SourceText.of("sample.kk", "1 + 2 * 3")))

        assertIs<CompilationResult.Success>(result)
        assertEquals(listOf(CompilerPhase.Lexing, CompilerPhase.Parsing, CompilerPhase.Lowering), result.phaseTrace)
        assertEquals(SourceSpan("sample.kk", 0, 9), result.program.span)
        assertFalse(result.hasErrors)
    }

    /**
     * 验证 lexing diagnostics 会阻止 parser 创建和后续阶段运行。
     * Verifies that lexing diagnostics prevent parser creation and later phases.
     */
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

    /**
     * 验证 parsing diagnostics 会阻止 lowering 运行。
     * Verifies that parsing diagnostics prevent lowering from running.
     */
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

    /**
     * 验证 lowering diagnostics 会产生失败结果且不会返回 compiled program。
     * Verifies that lowering diagnostics produce failure without returning a compiled program.
     */
    @Test
    fun `pipeline returns lowering diagnostics without compiled program`() {
        val source = SourceText.of("sample.kk", "name")
        val result = CompilerPipeline().compile(CompilationInput(source))

        assertIs<CompilationResult.Failure>(result)
        assertEquals(listOf(CompilerPhase.Lexing, CompilerPhase.Parsing, CompilerPhase.Lowering), result.phaseTrace)
        assertEquals(listOf("EXEC001"), result.diagnostics.map { it.code })
    }

    /**
     * 验证 lowerer 违反内部契约时会被转换为 COMPILER001。
     * Verifies that a lowerer contract violation is converted into COMPILER001.
     */
    @Test
    fun `pipeline rejects lowerer success without ir`() {
        val result = CompilerPipeline(
            lowerer = IrLowerer { IrLoweringResult(null, emptyList()) },
        ).compile(CompilationInput(SourceText.of("sample.kk", "1")))

        assertIs<CompilationResult.Failure>(result)
        assertEquals(listOf("COMPILER001"), result.diagnostics.map { it.code })
    }

    /**
     * 验证 execution engine 在编译失败时不会调用 evaluator。
     * Verifies that the execution engine does not call the evaluator after compilation failure.
     */
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

    /**
     * 验证 execution engine 会求值成功编译出的 program。
     * Verifies that the execution engine evaluates a successfully compiled program.
     */
    @Test
    fun `execution engine evaluates compiled program`() {
        val result = ExecutionEngine(
            compiler = CompilerPipeline(),
            evaluator = CoreIrEvaluator(),
        ).execute(SourceText.of("sample.kk", "1 + 2"))

        assertIs<ExecutionResult.Success>(result)
        assertEquals(ExecutionValue.Int64(3), result.value)
    }

    /**
     * 验证 pipeline 产生的内部 diagnostics 保留原始源码 span。
     * Verifies that internal diagnostics produced by the pipeline preserve the original source span.
     */
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
