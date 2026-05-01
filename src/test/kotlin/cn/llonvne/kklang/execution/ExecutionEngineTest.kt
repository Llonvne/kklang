package cn.llonvne.kklang.execution

import cn.llonvne.kklang.frontend.SourceText
import cn.llonvne.kklang.frontend.diagnostics.Diagnostic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ExecutionEngineTest {
    @Test
    fun `engine executes integer arithmetic with parser precedence`() {
        val result = ExecutionEngine().execute(SourceText.of("sample.kk", "1 + 2 * 3"))

        assertIs<ExecutionResult.Success>(result)
        assertEquals(ExecutionValue.Int64(7), result.value)
    }

    @Test
    fun `engine executes grouping prefix subtraction and division`() {
        assertEquals(ExecutionValue.Int64(-7), executeValue("-(10 - 3)"))
        assertEquals(ExecutionValue.Int64(2), executeValue("8 / 3"))
        assertEquals(ExecutionValue.Int64(1), executeValue("+1"))
    }

    @Test
    fun `engine returns lexer parser and lowering failures without evaluation`() {
        assertFailureCodes("@", "LEX001", "PARSE001")
        assertFailureCodes("1 +", "PARSE001")
        assertFailureCodes("name", "EXEC001")
    }

    @Test
    fun `engine reports evaluator diagnostics`() {
        assertFailureCodes("1 / 0", "EXEC002")
        assertFailureCodes("9223372036854775807 + 1", "EXEC003")
        assertFailureCodes("-9223372036854775808", "EXEC003")
        assertFailureCodes("9223372036854775807 * 2", "EXEC003")
        assertFailureCodes("-9223372036854775808 - 1", "EXEC003")
    }

    @Test
    fun `execution failure exposes diagnostics and success has no diagnostics`() {
        val failure = ExecutionEngine().execute(SourceText.of("sample.kk", "1 / 0"))
        val success = ExecutionEngine().execute(SourceText.of("sample.kk", "1"))

        assertIs<ExecutionResult.Failure>(failure)
        assertTrue(failure.hasErrors)
        assertIs<ExecutionResult.Success>(success)
        assertFalse(success.hasErrors)
    }

    @Test
    fun `engine handles defensive lowerer and evaluator failure combinations`() {
        val source = SourceText.of("sample.kk", "1")
        val diagnostic = Diagnostic("EXEC001", "unsupported expression", source.positionSpan())
        val ir = IrInt64(1, source.positionSpan())
        val value = ExecutionValue.Int64(1)

        assertEquals(
            listOf("EXEC001"),
            failureCodes(ExecutionEngine(lowerer = IrLowerer { IrLoweringResult(null, listOf(diagnostic)) }, evaluator = CoreIrEvaluator()), source),
        )
        assertEquals(
            listOf("EXEC001"),
            failureCodes(ExecutionEngine(lowerer = IrLowerer { IrLoweringResult(ir, listOf(diagnostic)) }, evaluator = CoreIrEvaluator()), source),
        )
        assertEquals(
            listOf("EXEC001"),
            failureCodes(ExecutionEngine(lowerer = CoreIrLowerer(), evaluator = IrEvaluator { EvaluationResult(null, listOf(diagnostic)) }), source),
        )
        assertEquals(
            listOf("EXEC001"),
            failureCodes(ExecutionEngine(lowerer = CoreIrLowerer(), evaluator = IrEvaluator { EvaluationResult(value, listOf(diagnostic)) }), source),
        )
    }

    private fun executeValue(text: String): ExecutionValue {
        val result = ExecutionEngine().execute(SourceText.of("sample.kk", text))
        assertIs<ExecutionResult.Success>(result)
        return result.value
    }

    private fun assertFailureCodes(text: String, vararg codes: String) {
        val result = ExecutionEngine().execute(SourceText.of("sample.kk", text))
        assertIs<ExecutionResult.Failure>(result)
        assertEquals(codes.toList(), result.diagnostics.map { it.code })
    }

    private fun failureCodes(engine: ExecutionEngine, source: SourceText): List<String> {
        val result = engine.execute(source)
        assertIs<ExecutionResult.Failure>(result)
        return result.diagnostics.map { it.code }
    }

    private fun SourceText.positionSpan() = cn.llonvne.kklang.frontend.SourceSpan(name, 0, 1)
}
