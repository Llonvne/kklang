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

/**
 * AST 到 Core IR lowering 的结果，成功时 ir 非空，失败时 diagnostics 非空。
 * Result of lowering AST to Core IR; ir is present on success and diagnostics are present on failure.
 */
data class IrLoweringResult(
    val ir: IrExpression?,
    val diagnostics: List<Diagnostic>,
) {
    val hasErrors: Boolean
        get() = diagnostics.isNotEmpty()
}

/**
 * AST 到 Core IR lowering 的可替换接口。
 * Replaceable interface for lowering AST expressions to Core IR.
 */
fun interface IrLowerer {
    /**
     * 降级一个 AST expression 并返回 Core IR 或 diagnostics。
     * Lowers one AST expression and returns either Core IR or diagnostics.
     */
    fun lower(expression: cn.llonvne.kklang.frontend.parsing.Expression): IrLoweringResult
}

/**
 * 当前最小 lowerer，只把已规范化的整数表达式降级为 Core IR。
 * Current minimal lowerer that lowers only specified integer expressions into Core IR.
 */
class CoreIrLowerer : IrLowerer {
    /**
     * 降级根 expression，并在过程中收集 lowering diagnostics。
     * Lowers the root expression while collecting lowering diagnostics.
     */
    override fun lower(expression: Expression): IrLoweringResult {
        val diagnostics = DiagnosticBag()
        val ir = lowerExpression(expression, diagnostics)
        return IrLoweringResult(ir = ir, diagnostics = diagnostics.toList())
    }

    /**
     * 按 AST expression 类型分派 lowering。
     * Dispatches lowering by AST expression type.
     */
    private fun lowerExpression(expression: Expression, diagnostics: DiagnosticBag): IrExpression? =
        when (expression) {
            is IntegerExpression -> lowerInteger(expression, diagnostics)
            is GroupedExpression -> lowerExpression(expression.expression, diagnostics)
            is PrefixExpression -> lowerPrefix(expression, diagnostics)
            is BinaryExpression -> lowerBinary(expression, diagnostics)
            is IdentifierExpression -> unsupported(expression, diagnostics)
            is MissingExpression -> unsupported(expression, diagnostics)
        }

    /**
     * 将整数字面量文本转换为 Int64 IR，超出范围时报告 EXEC003。
     * Converts integer literal text to Int64 IR and reports EXEC003 when it is out of range.
     */
    private fun lowerInteger(expression: IntegerExpression, diagnostics: DiagnosticBag): IrExpression? {
        val value = expression.digits.toLongOrNull()
        return if (value == null) {
            diagnostics.report("EXEC003", "Int64 overflow", expression.span)
            null
        } else {
            IrInt64(value = value, span = expression.span)
        }
    }

    /**
     * 降级当前支持的一元前缀表达式。
     * Lowers the currently supported unary prefix expressions.
     */
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

    /**
     * 降级当前支持的二元表达式，并传播左右 operand 的失败。
     * Lowers the currently supported binary expressions and propagates left or right operand failures.
     */
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

    /**
     * 报告当前执行范围不支持的 AST expression。
     * Reports an AST expression unsupported by the current execution scope.
     */
    private fun unsupported(expression: Expression, diagnostics: DiagnosticBag): IrExpression? {
        diagnostics.report("EXEC001", "unsupported expression", expression.span)
        return null
    }
}
