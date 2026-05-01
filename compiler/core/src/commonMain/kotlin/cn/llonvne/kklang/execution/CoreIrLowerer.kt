package cn.llonvne.kklang.execution

import cn.llonvne.kklang.frontend.diagnostics.Diagnostic
import cn.llonvne.kklang.frontend.diagnostics.DiagnosticBag
import cn.llonvne.kklang.frontend.lexing.TokenKinds
import cn.llonvne.kklang.frontend.parsing.BinaryExpression
import cn.llonvne.kklang.frontend.parsing.Expression
import cn.llonvne.kklang.frontend.parsing.GroupedExpression
import cn.llonvne.kklang.frontend.parsing.IdentifierExpression
import cn.llonvne.kklang.frontend.parsing.IntegerExpression
import cn.llonvne.kklang.frontend.parsing.MissingExpression
import cn.llonvne.kklang.frontend.parsing.PrefixExpression

data class IrLoweringResult(
    val ir: IrExpression?,
    val diagnostics: List<Diagnostic>,
) {
    val hasErrors: Boolean
        get() = diagnostics.isNotEmpty()
}

fun interface IrLowerer {
    fun lower(expression: cn.llonvne.kklang.frontend.parsing.Expression): IrLoweringResult
}

class CoreIrLowerer : IrLowerer {
    override fun lower(expression: Expression): IrLoweringResult {
        val diagnostics = DiagnosticBag()
        val ir = lowerExpression(expression, diagnostics)
        return IrLoweringResult(ir = ir, diagnostics = diagnostics.toList())
    }

    private fun lowerExpression(expression: Expression, diagnostics: DiagnosticBag): IrExpression? =
        when (expression) {
            is IntegerExpression -> lowerInteger(expression, diagnostics)
            is GroupedExpression -> lowerExpression(expression.expression, diagnostics)
            is PrefixExpression -> lowerPrefix(expression, diagnostics)
            is BinaryExpression -> lowerBinary(expression, diagnostics)
            is IdentifierExpression -> unsupported(expression, diagnostics)
            is MissingExpression -> unsupported(expression, diagnostics)
        }

    private fun lowerInteger(expression: IntegerExpression, diagnostics: DiagnosticBag): IrExpression? {
        val value = expression.digits.toLongOrNull()
        return if (value == null) {
            diagnostics.report("EXEC003", "Int64 overflow", expression.span)
            null
        } else {
            IrInt64(value = value, span = expression.span)
        }
    }

    private fun lowerPrefix(expression: PrefixExpression, diagnostics: DiagnosticBag): IrExpression? {
        val operand = lowerExpression(expression.operand, diagnostics) ?: return null
        val operator = when (expression.operator.kind) {
            TokenKinds.Plus -> IrUnaryOperator.Plus
            TokenKinds.Minus -> IrUnaryOperator.Minus
            else -> {
                unsupported(expression, diagnostics)
                return null
            }
        }
        return IrUnary(operator = operator, operand = operand, span = expression.span)
    }

    private fun lowerBinary(expression: BinaryExpression, diagnostics: DiagnosticBag): IrExpression? {
        val left = lowerExpression(expression.left, diagnostics)
        val right = lowerExpression(expression.right, diagnostics)
        if (left == null || right == null) {
            return null
        }

        val operator = when (expression.operator.kind) {
            TokenKinds.Plus -> IrBinaryOperator.Plus
            TokenKinds.Minus -> IrBinaryOperator.Minus
            TokenKinds.Star -> IrBinaryOperator.Multiply
            TokenKinds.Slash -> IrBinaryOperator.Divide
            else -> {
                unsupported(expression, diagnostics)
                return null
            }
        }
        return IrBinary(left = left, operator = operator, right = right, span = expression.span)
    }

    private fun unsupported(expression: Expression, diagnostics: DiagnosticBag): IrExpression? {
        diagnostics.report("EXEC001", "unsupported expression", expression.span)
        return null
    }
}
