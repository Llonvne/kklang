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
            IrUnaryOperator.Minus -> checked(expression, diagnostics) { Math.negateExact(operand) }
        }
    }

    private fun evaluateBinary(expression: IrBinary, diagnostics: DiagnosticBag): ExecutionValue? {
        val left = evaluateExpression(expression.left, diagnostics)?.asInt64()
        val right = evaluateExpression(expression.right, diagnostics)?.asInt64()
        if (left == null || right == null) {
            return null
        }

        return when (expression.operator) {
            IrBinaryOperator.Plus -> checked(expression, diagnostics) { Math.addExact(left, right) }
            IrBinaryOperator.Minus -> checked(expression, diagnostics) { Math.subtractExact(left, right) }
            IrBinaryOperator.Multiply -> checked(expression, diagnostics) { Math.multiplyExact(left, right) }
            IrBinaryOperator.Divide -> divide(expression, left, right, diagnostics)
        }
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
        return checked(expression, diagnostics) { left / right }
    }

    private fun checked(
        expression: IrExpression,
        diagnostics: DiagnosticBag,
        operation: () -> Long,
    ): ExecutionValue? =
        try {
            ExecutionValue.Int64(operation())
        } catch (_: ArithmeticException) {
            diagnostics.report("EXEC003", "Int64 overflow", expression.span)
            null
        }

    private fun ExecutionValue.asInt64(): Long =
        when (this) {
            is ExecutionValue.Int64 -> value
        }
}
