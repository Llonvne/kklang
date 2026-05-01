package cn.llonvne.kklang.execution

import cn.llonvne.kklang.frontend.SourceText
import cn.llonvne.kklang.frontend.diagnostics.Diagnostic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 覆盖 execution engine 的成功求值、失败传播和 evaluator 防御分支。
 * Covers execution-engine successful evaluation, failure propagation, and defensive evaluator branches.
 */
class ExecutionEngineTest {
    /**
     * 验证 parser 优先级会影响最终整数算术结果。
     * Verifies that parser precedence affects the final integer arithmetic result.
     */
    @Test
    fun `engine executes integer arithmetic with parser precedence`() {
        val result = ExecutionEngine().execute(SourceText.of("sample.kk", "1 + 2 * 3"))

        assertIs<ExecutionResult.Success>(result)
        assertEquals(ExecutionValue.Int64(7), result.value)
    }

    /**
     * 验证分组、前缀、除法和零乘法的成功执行路径。
     * Verifies successful execution paths for grouping, prefix operators, division, and multiplication by zero.
     */
    @Test
    fun `engine executes grouping prefix subtraction and division`() {
        assertEquals(ExecutionValue.Int64(-7), executeValue("-(10 - 3)"))
        assertEquals(ExecutionValue.Int64(2), executeValue("8 / 3"))
        assertEquals(ExecutionValue.Int64(Long.MIN_VALUE), executeValue("(-9223372036854775807 - 1) / 1"))
        assertEquals(ExecutionValue.Int64(0), executeValue("0 * 9223372036854775807"))
        assertEquals(ExecutionValue.Int64(1), executeValue("+1"))
    }

    /**
     * 验证 lexer、parser 和 type checking 失败会被 execution engine 直接返回。
     * Verifies that lexer, parser, and type-checking failures are returned directly by the execution engine.
     */
    @Test
    fun `engine returns lexer parser and type checking failures without evaluation`() {
        assertFailureCodes("@", "LEX001")
        assertFailureCodes("1 +", "PARSE001")
        assertFailureCodes("name", "TYPE001")
    }

    /**
     * 验证 evaluator 报告除零和 Int64 溢出 diagnostics。
     * Verifies that the evaluator reports division-by-zero and Int64 overflow diagnostics.
     */
    @Test
    fun `engine reports evaluator diagnostics`() {
        assertFailureCodes("1 / 0", "EXEC002")
        assertFailureCodes("9223372036854775807 + 1", "EXEC003")
        assertFailureCodes("-9223372036854775808", "EXEC003")
        assertFailureCodes("9223372036854775807 * 2", "EXEC003")
        assertFailureCodes("-9223372036854775808 - 1", "EXEC003")
        assertFailureCodes("(-9223372036854775807 - 1) - 1", "EXEC003")
        assertFailureCodes("(-9223372036854775807 - 1) / -1", "EXEC003")
    }

    /**
     * 验证 success/failure 的 hasErrors 语义。
     * Verifies hasErrors semantics for success and failure.
     */
    @Test
    fun `execution failure exposes diagnostics and success has no diagnostics`() {
        val failure = ExecutionEngine().execute(SourceText.of("sample.kk", "1 / 0"))
        val success = ExecutionEngine().execute(SourceText.of("sample.kk", "1"))

        assertIs<ExecutionResult.Failure>(failure)
        assertTrue(failure.hasErrors)
        assertIs<ExecutionResult.Success>(success)
        assertFalse(success.hasErrors)
    }

    /**
     * 验证 evaluator 返回不一致结果时 engine 仍按 diagnostics 失败。
     * Verifies that the engine still fails by diagnostics when an evaluator returns inconsistent results.
     */
    @Test
    fun `engine handles defensive evaluator failure combinations`() {
        val source = SourceText.of("sample.kk", "1")
        val diagnostic = Diagnostic("EXEC001", "unsupported expression", source.positionSpan())
        val value = ExecutionValue.Int64(1)

        assertEquals(
            listOf("EXEC001"),
            failureCodes(ExecutionEngine(evaluator = IrEvaluator { EvaluationResult(null, listOf(diagnostic)) }), source),
        )
        assertEquals(
            listOf("EXEC001"),
            failureCodes(ExecutionEngine(evaluator = IrEvaluator { EvaluationResult(value, listOf(diagnostic)) }), source),
        )
    }

    /**
     * 执行源码并取出成功值，测试中用于压缩重复断言。
     * Executes source and extracts the success value to reduce repeated assertions in tests.
     */
    private fun executeValue(text: String): ExecutionValue {
        val result = ExecutionEngine().execute(SourceText.of("sample.kk", text))
        assertIs<ExecutionResult.Success>(result)
        return result.value
    }

    /**
     * 断言源码执行失败并产生指定 diagnostic code 序列。
     * Asserts that source execution fails with the specified diagnostic code sequence.
     */
    private fun assertFailureCodes(text: String, vararg codes: String) {
        val result = ExecutionEngine().execute(SourceText.of("sample.kk", text))
        assertIs<ExecutionResult.Failure>(result)
        assertEquals(codes.toList(), result.diagnostics.map { it.code })
    }

    /**
     * 返回指定 engine 执行失败时产生的 diagnostic code。
     * Returns diagnostic codes produced when the given engine fails to execute.
     */
    private fun failureCodes(engine: ExecutionEngine, source: SourceText): List<String> {
        val result = engine.execute(source)
        assertIs<ExecutionResult.Failure>(result)
        return result.diagnostics.map { it.code }
    }

    /**
     * 为测试 diagnostics 创建覆盖源码第一个字符的 span。
     * Creates a span covering the first source character for diagnostic tests.
     */
    private fun SourceText.positionSpan() = cn.llonvne.kklang.frontend.SourceSpan(name, 0, 1)
}
