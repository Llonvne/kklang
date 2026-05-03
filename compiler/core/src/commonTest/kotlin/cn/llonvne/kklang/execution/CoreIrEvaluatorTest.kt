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
     * 验证 program 中的 val declaration 会按顺序求值并绑定变量。
     * Verifies that val declarations in a program are evaluated in order and bind variables.
     */
    @Test
    fun `evaluator evaluates immutable val program`() {
        val program = IrProgram(
            declarations = listOf(
                IrValDeclaration(name = "x", initializer = IrInt64(1, span), span = span),
                IrValDeclaration(
                    name = "y",
                    initializer = IrBinary(
                        left = IrVariable("x", span),
                        operator = IrBinaryOperator.Plus,
                        right = IrInt64(2, span),
                        span = span,
                    ),
                    span = span,
                ),
            ),
            expression = IrVariable("y", span),
        )

        val result = CoreIrEvaluator().evaluate(program)

        assertEquals(ExecutionValue.Int64(3), result.value)
    }

    /**
     * 验证 evaluator 可以返回字符串值并通过 val 引用传播。
     * Verifies that the evaluator can return string values and propagate them through val references.
     */
    @Test
    fun `evaluator evaluates string literals and variables`() {
        val program = IrProgram(
            declarations = listOf(IrValDeclaration(name = "text", initializer = IrString("hello", span), span = span)),
            expression = IrVariable("text", span),
        )

        val result = CoreIrEvaluator().evaluate(program)

        assertEquals(ExecutionValue.String("hello"), result.value)
    }

    /**
     * 验证 evaluator 执行 print 副作用并返回 Unit。
     * Verifies that the evaluator executes print side effects and returns Unit.
     */
    @Test
    fun `evaluator evaluates print output and returns unit`() {
        val program = IrProgram(
            declarations = listOf(IrValDeclaration(name = "text", initializer = IrString("hello", span), span = span)),
            expression = IrPrint(
                IrPrint(IrVariable("text", span), span),
                span,
            ),
        )

        val result = CoreIrEvaluator().evaluate(program)

        assertEquals(ExecutionValue.Unit, result.value)
        assertEquals("helloUnit", result.output)
    }

    /**
     * 验证 print 使用 Int64 的十进制文本形式。
     * Verifies that print uses the decimal text form for Int64.
     */
    @Test
    fun `evaluator prints integer text`() {
        val result = CoreIrEvaluator().evaluate(IrPrint(IrInt64(42, span), span))

        assertEquals(ExecutionValue.Unit, result.value)
        assertEquals("42", result.output)
    }

    /**
     * 验证未绑定变量和 declaration initializer 失败都会让 program 求值失败。
     * Verifies that unbound variables and declaration-initializer failures make program evaluation fail.
     */
    @Test
    fun `evaluator reports unbound variables and declaration failures`() {
        val unbound = CoreIrEvaluator().evaluate(IrProgram(emptyList(), IrVariable("x", span)))
        val declarationFailure = CoreIrEvaluator().evaluate(
            IrProgram(
                declarations = listOf(IrValDeclaration(name = "x", initializer = IrVariable("missing", span), span = span)),
                expression = IrInt64(1, span),
            ),
        )

        assertTrue(unbound.hasErrors)
        assertNull(unbound.value)
        assertEquals("EXEC001", unbound.diagnostics.single().code)
        assertTrue(declarationFailure.hasErrors)
        assertNull(declarationFailure.value)
        assertEquals("EXEC001", declarationFailure.diagnostics.single().code)
    }

    /**
     * 验证 print operand 求值失败时外层表达式失败并保留已经产生的输出。
     * Verifies that print fails when its operand fails and preserves output already produced.
     */
    @Test
    fun `evaluator reports print operand failure`() {
        val result = CoreIrEvaluator().evaluate(
            IrProgram(
                declarations = listOf(IrValDeclaration("printed", IrPrint(IrString("before", span), span), span)),
                expression = IrPrint(IrVariable("missing", span), span),
            ),
        )

        assertTrue(result.hasErrors)
        assertNull(result.value)
        assertEquals("before", result.output)
        assertEquals("EXEC001", result.diagnostics.single().code)
    }

    /**
     * 验证一元取负的成功路径和 Long.MIN_VALUE 溢出路径。
     * Verifies successful unary negation and the Long.MIN_VALUE overflow path.
     */
    @Test
    fun `evaluator covers unary negation success and overflow`() {
        val success = CoreIrEvaluator().evaluate(
            IrUnary(operator = IrUnaryOperator.Minus, operand = IrInt64(1, span), span = span),
        )
        val overflow = CoreIrEvaluator().evaluate(
            IrUnary(operator = IrUnaryOperator.Minus, operand = IrInt64(Long.MIN_VALUE, span), span = span),
        )

        assertEquals(ExecutionValue.Int64(-1), success.value)
        assertTrue(overflow.hasErrors)
        assertNull(overflow.value)
        assertEquals("EXEC003", overflow.diagnostics.single().code)
    }

    /**
     * 验证二元加法的成功路径和 Int64 溢出路径。
     * Verifies successful binary addition and the Int64 overflow path.
     */
    @Test
    fun `evaluator covers addition success and overflow`() {
        val success = CoreIrEvaluator().evaluate(
            IrBinary(
                left = IrInt64(1, span),
                operator = IrBinaryOperator.Plus,
                right = IrInt64(2, span),
                span = span,
            ),
        )
        val overflow = CoreIrEvaluator().evaluate(
            IrBinary(
                left = IrInt64(Long.MAX_VALUE, span),
                operator = IrBinaryOperator.Plus,
                right = IrInt64(1, span),
                span = span,
            ),
        )

        assertEquals(ExecutionValue.Int64(3), success.value)
        assertTrue(overflow.hasErrors)
        assertNull(overflow.value)
        assertEquals("EXEC003", overflow.diagnostics.single().code)
    }

    /**
     * 验证二元减法的成功路径和 Int64 溢出路径。
     * Verifies successful binary subtraction and the Int64 overflow path.
     */
    @Test
    fun `evaluator covers subtraction success and overflow`() {
        val success = CoreIrEvaluator().evaluate(
            IrBinary(
                left = IrInt64(3, span),
                operator = IrBinaryOperator.Minus,
                right = IrInt64(2, span),
                span = span,
            ),
        )
        val overflow = CoreIrEvaluator().evaluate(
            IrBinary(
                left = IrInt64(Long.MIN_VALUE, span),
                operator = IrBinaryOperator.Minus,
                right = IrInt64(1, span),
                span = span,
            ),
        )

        assertEquals(ExecutionValue.Int64(1), success.value)
        assertTrue(overflow.hasErrors)
        assertNull(overflow.value)
        assertEquals("EXEC003", overflow.diagnostics.single().code)
    }

    /**
     * 验证二元除法的成功、除零和 Int64 溢出路径。
     * Verifies successful binary division, division by zero, and Int64 overflow paths.
     */
    @Test
    fun `evaluator covers division success division by zero and overflow`() {
        val success = CoreIrEvaluator().evaluate(
            IrBinary(
                left = IrInt64(8, span),
                operator = IrBinaryOperator.Divide,
                right = IrInt64(3, span),
                span = span,
            ),
        )
        val divisionByZero = CoreIrEvaluator().evaluate(
            IrBinary(
                left = IrInt64(1, span),
                operator = IrBinaryOperator.Divide,
                right = IrInt64(0, span),
                span = span,
            ),
        )
        val overflow = CoreIrEvaluator().evaluate(
            IrBinary(
                left = IrInt64(Long.MIN_VALUE, span),
                operator = IrBinaryOperator.Divide,
                right = IrInt64(-1, span),
                span = span,
            ),
        )

        assertEquals(ExecutionValue.Int64(2), success.value)
        assertTrue(divisionByZero.hasErrors)
        assertNull(divisionByZero.value)
        assertEquals("EXEC002", divisionByZero.diagnostics.single().code)
        assertTrue(overflow.hasErrors)
        assertNull(overflow.value)
        assertEquals("EXEC003", overflow.diagnostics.single().code)
    }

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

    /**
     * 验证 malformed IR 中字符串参与整数运算会产生 EXEC001。
     * Verifies that strings in integer operations inside malformed IR produce EXEC001.
     */
    @Test
    fun `evaluator reports string operands in malformed integer operations`() {
        val result = CoreIrEvaluator().evaluate(
            IrBinary(left = IrString("x", span), operator = IrBinaryOperator.Plus, right = IrInt64(1, span), span = span),
        )
        val unit = CoreIrEvaluator().evaluate(
            IrBinary(
                left = IrPrint(IrString("x", span), span),
                operator = IrBinaryOperator.Plus,
                right = IrInt64(1, span),
                span = span,
            ),
        )

        assertTrue(result.hasErrors)
        assertNull(result.value)
        assertEquals("EXEC001", result.diagnostics.single().code)
        assertTrue(unit.hasErrors)
        assertNull(unit.value)
        assertEquals("x", unit.output)
        assertEquals("EXEC001", unit.diagnostics.single().code)
    }
}
