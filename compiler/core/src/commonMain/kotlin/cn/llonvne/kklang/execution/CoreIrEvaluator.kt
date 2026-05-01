package cn.llonvne.kklang.execution

import cn.llonvne.kklang.frontend.diagnostics.Diagnostic
import cn.llonvne.kklang.frontend.diagnostics.DiagnosticBag

/**
 * Core IR 求值的结果，成功时 value 非空，失败时 diagnostics 非空。
 * Result of Core IR evaluation; value is present on success and diagnostics are present on failure.
 */
data class EvaluationResult(
    val value: ExecutionValue?,
    val diagnostics: List<Diagnostic>,
) {
    val hasErrors: Boolean
        get() = diagnostics.isNotEmpty()
}

/**
 * Core IR evaluator 的可替换接口，允许执行器接入不同后端。
 * Replaceable Core IR evaluator interface that lets the execution engine use different backends.
 */
fun interface IrEvaluator {
    /**
     * 求值单个 Core IR expression 并返回值或 diagnostics。
     * Evaluates one Core IR expression and returns either a value or diagnostics.
     */
    fun evaluate(expression: IrExpression): EvaluationResult
}

/**
 * 当前最小 Core IR evaluator，只支持 Int64 表达式和受检整数运算。
 * Current minimal Core IR evaluator supporting only Int64 expressions and checked integer operations.
 */
class CoreIrEvaluator : IrEvaluator {
    /**
     * 求值 Core IR 根节点并收集所有求值期 diagnostics。
     * Evaluates the Core IR root node and collects all evaluation diagnostics.
     */
    override fun evaluate(expression: IrExpression): EvaluationResult {
        val diagnostics = DiagnosticBag()
        val value = evaluateExpression(expression, diagnostics)
        return EvaluationResult(value = value, diagnostics = diagnostics.toList())
    }

    /**
     * 按节点类型分派 Core IR expression 求值。
     * Dispatches Core IR expression evaluation by node type.
     */
    private fun evaluateExpression(expression: IrExpression, diagnostics: DiagnosticBag): ExecutionValue? =
        when (expression) {
            is IrInt64 -> ExecutionValue.Int64(expression.value)
            is IrUnary -> evaluateUnary(expression, diagnostics)
            is IrBinary -> evaluateBinary(expression, diagnostics)
        }

    /**
     * 求值一元运算，并在 operand 已失败时传播失败。
     * Evaluates a unary operation and propagates failure when the operand already failed.
     */
    private fun evaluateUnary(expression: IrUnary, diagnostics: DiagnosticBag): ExecutionValue? {
        val operand = evaluateExpression(expression.operand, diagnostics)?.asInt64() ?: return null
        return when (expression.operator) {
            IrUnaryOperator.Plus -> ExecutionValue.Int64(operand)
            IrUnaryOperator.Minus -> negate(expression, operand, diagnostics)
        }
    }

    /**
     * 求值二元运算，并在任一 operand 已失败时传播失败。
     * Evaluates a binary operation and propagates failure when either operand already failed.
     */
    private fun evaluateBinary(expression: IrBinary, diagnostics: DiagnosticBag): ExecutionValue? {
        val left = evaluateExpression(expression.left, diagnostics)?.asInt64()
        val right = evaluateExpression(expression.right, diagnostics)?.asInt64()
        if (left == null || right == null) {
            return null
        }

        return when (expression.operator) {
            IrBinaryOperator.Plus -> add(expression, left, right, diagnostics)
            IrBinaryOperator.Minus -> subtract(expression, left, right, diagnostics)
            IrBinaryOperator.Multiply -> multiply(expression, left, right, diagnostics)
            IrBinaryOperator.Divide -> divide(expression, left, right, diagnostics)
        }
    }

    /**
     * 对 Int64 执行受检取负，覆盖 Long.MIN_VALUE 溢出。
     * Performs checked Int64 negation and covers Long.MIN_VALUE overflow.
     */
    private fun negate(
        expression: IrUnary,
        operand: Long,
        diagnostics: DiagnosticBag,
    ): ExecutionValue? {
        if (operand == Long.MIN_VALUE) {
            return overflow(expression, diagnostics)
        }
        return ExecutionValue.Int64(-operand)
    }

    /**
     * 对 Int64 执行受检加法。
     * Performs checked Int64 addition.
     */
    private fun add(
        expression: IrBinary,
        left: Long,
        right: Long,
        diagnostics: DiagnosticBag,
    ): ExecutionValue? {
        val result = left + right
        if (((left xor result) and (right xor result)) < 0) {
            return overflow(expression, diagnostics)
        }
        return ExecutionValue.Int64(result)
    }

    /**
     * 对 Int64 执行受检减法。
     * Performs checked Int64 subtraction.
     */
    private fun subtract(
        expression: IrBinary,
        left: Long,
        right: Long,
        diagnostics: DiagnosticBag,
    ): ExecutionValue? {
        val result = left - right
        if (((left xor right) and (left xor result)) < 0) {
            return overflow(expression, diagnostics)
        }
        return ExecutionValue.Int64(result)
    }

    /**
     * 对 Int64 执行受检乘法，左操作数为零时直接允许零结果。
     * Performs checked Int64 multiplication and directly accepts a zero result when the left operand is zero.
     */
    private fun multiply(
        expression: IrBinary,
        left: Long,
        right: Long,
        diagnostics: DiagnosticBag,
    ): ExecutionValue? {
        val result = left * right
        if (left != 0L && result / left != right) {
            return overflow(expression, diagnostics)
        }
        return ExecutionValue.Int64(result)
    }

    /**
     * 对 Int64 执行除法，报告除零和 Long.MIN_VALUE / -1 溢出。
     * Performs Int64 division and reports division by zero plus Long.MIN_VALUE / -1 overflow.
     */
    private fun divide(
        expression: IrBinary,
        left: Long,
        right: Long,
        diagnostics: DiagnosticBag,
    ): ExecutionValue? {
        if (right == 0L) {
            diagnostics.report("EXEC002", "division by zero", expression.span)
            return null
        }
        if (left == Long.MIN_VALUE && right == -1L) {
            return overflow(expression, diagnostics)
        }
        return ExecutionValue.Int64(left / right)
    }

    /**
     * 报告统一的 Int64 溢出 diagnostic 并返回失败值。
     * Reports the shared Int64 overflow diagnostic and returns a failed value.
     */
    private fun overflow(
        expression: IrExpression,
        diagnostics: DiagnosticBag,
    ): ExecutionValue? {
        diagnostics.report("EXEC003", "Int64 overflow", expression.span)
        return null
    }

    /**
     * 将当前执行值拆成 Int64；当前值模型只包含 Int64。
     * Extracts the current execution value as Int64; the current value model only contains Int64.
     */
    private fun ExecutionValue.asInt64(): Long =
        when (this) {
            is ExecutionValue.Int64 -> value
        }
}
