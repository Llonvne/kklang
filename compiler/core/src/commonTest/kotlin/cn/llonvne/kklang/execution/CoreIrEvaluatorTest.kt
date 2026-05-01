package cn.llonvne.kklang.execution

import cn.llonvne.kklang.frontend.SourceSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 覆盖 CoreIrEvaluator 的嵌套失败传播路径。
 * Covers nested failure propagation paths in CoreIrEvaluator.
 */
class CoreIrEvaluatorTest {
    private val span = SourceSpan("sample.kk", 0, 1)

    /**
     * 验证一元表达式 operand 求值失败时外层表达式返回失败。
     * Verifies that an outer unary expression fails when its operand evaluation fails.
     */
    @Test
    fun `evaluator returns null when unary operand evaluation fails`() {
        val overflow = IrUnary(
            operator = IrUnaryOperator.Minus,
            operand = IrInt64(Long.MIN_VALUE, span),
            span = span,
        )
        val nested = IrUnary(operator = IrUnaryOperator.Plus, operand = overflow, span = span)

        val result = CoreIrEvaluator().evaluate(nested)

        assertTrue(result.hasErrors)
        assertNull(result.value)
        assertEquals("EXEC003", result.diagnostics.single().code)
    }

    /**
     * 验证二元表达式左右 operand 的失败都会传播。
     * Verifies that failures from either side of a binary expression are propagated.
     */
    @Test
    fun `evaluator returns null when left or right binary evaluation fails`() {
        val overflow = IrBinary(
            left = IrInt64(Long.MAX_VALUE, span),
            operator = IrBinaryOperator.Plus,
            right = IrInt64(1, span),
            span = span,
        )
        val divisionByZero = IrBinary(
            left = IrInt64(1, span),
            operator = IrBinaryOperator.Divide,
            right = IrInt64(0, span),
            span = span,
        )

        val leftFailure = CoreIrEvaluator().evaluate(
            IrBinary(left = overflow, operator = IrBinaryOperator.Plus, right = IrInt64(1, span), span = span),
        )
        val rightFailure = CoreIrEvaluator().evaluate(
            IrBinary(left = IrInt64(1, span), operator = IrBinaryOperator.Plus, right = divisionByZero, span = span),
        )

        assertTrue(leftFailure.hasErrors)
        assertNull(leftFailure.value)
        assertEquals("EXEC003", leftFailure.diagnostics.single().code)
        assertTrue(rightFailure.hasErrors)
        assertNull(rightFailure.value)
        assertEquals("EXEC002", rightFailure.diagnostics.single().code)
    }
}
