package cn.llonvne.kklang.execution

import cn.llonvne.kklang.frontend.diagnostics.Diagnostic
import cn.llonvne.kklang.frontend.diagnostics.DiagnosticBag

data class EvaluationResult(
    val value: ExecutionValue?,
    val diagnostics: List<Diagnostic>,
) {
    val hasErrors: Boolean
        get() = diagnostics.isNotEmpty()
}

fun interface IrEvaluator {
    fun evaluate(expression: IrExpression): EvaluationResult
}

class CoreIrEvaluator : IrEvaluator {
    override fun evaluate(expression: IrExpression): EvaluationResult {
        val diagnostics = DiagnosticBag()
        val value = evaluateExpression(expression, diagnostics)
        return EvaluationResult(value = value, diagnostics = diagnostics.toList())
    }

    private fun evaluateExpression(expression: IrExpression, diagnostics: DiagnosticBag): ExecutionValue? =
        when (expression) {
            is IrInt64 -> ExecutionValue.Int64(expression.value)
            is IrUnary -> evaluateUnary(expression, diagnostics)
            is IrBinary -> evaluateBinary(expression, diagnostics)
        }

    private fun evaluateUnary(expression: IrUnary, diagnostics: DiagnosticBag): ExecutionValue? {
        val operand = evaluateExpression(expression.operand, diagnostics)?.asInt64() ?: return null
        return when (expression.operator) {
            IrUnaryOperator.Plus -> ExecutionValue.Int64(operand)
            IrUnaryOperator.Minus -> negate(expression, operand, diagnostics)
        }
    }

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

    private fun overflow(
        expression: IrExpression,
        diagnostics: DiagnosticBag,
    ): ExecutionValue? {
        diagnostics.report("EXEC003", "Int64 overflow", expression.span)
        return null
    }

    private fun ExecutionValue.asInt64(): Long =
        when (this) {
            is ExecutionValue.Int64 -> value
        }
}
