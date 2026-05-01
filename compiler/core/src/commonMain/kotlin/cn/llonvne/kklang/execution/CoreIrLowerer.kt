package cn.llonvne.kklang.execution

import cn.llonvne.kklang.frontend.diagnostics.Diagnostic
import cn.llonvne.kklang.frontend.diagnostics.DiagnosticBag
import cn.llonvne.kklang.frontend.lexing.TokenKinds
import cn.llonvne.kklang.typechecking.TypedBinary
import cn.llonvne.kklang.typechecking.TypedExpression
import cn.llonvne.kklang.typechecking.TypedGrouped
import cn.llonvne.kklang.typechecking.TypedInteger
import cn.llonvne.kklang.typechecking.TypedPrefix

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
 * typed AST 到 Core IR lowering 的可替换接口。
 * Replaceable interface for lowering typed AST expressions to Core IR.
 */
fun interface IrLowerer {
    /**
     * 降级一个 typed AST expression 并返回 Core IR 或 diagnostics。
     * Lowers one typed AST expression and returns either Core IR or diagnostics.
     */
    fun lower(expression: TypedExpression): IrLoweringResult
}

/**
 * 当前最小 lowerer，只把已类型检查的整数表达式降级为 Core IR。
 * Current minimal lowerer that lowers only type-checked integer expressions into Core IR.
 */
class CoreIrLowerer : IrLowerer {
    /**
     * 降级 typed 根 expression，并在过程中收集 lowering diagnostics。
     * Lowers the typed root expression while collecting lowering diagnostics.
     */
    override fun lower(expression: TypedExpression): IrLoweringResult {
        val diagnostics = DiagnosticBag()
        val ir = lowerExpression(expression, diagnostics)
        return IrLoweringResult(ir = ir, diagnostics = diagnostics.toList())
    }

    /**
     * 按 typed AST expression 类型分派 lowering。
     * Dispatches lowering by typed AST expression type.
     */
    private fun lowerExpression(expression: TypedExpression, diagnostics: DiagnosticBag): IrExpression? =
        when (expression) {
            is TypedInteger -> lowerInteger(expression, diagnostics)
            is TypedGrouped -> lowerExpression(expression.inner, diagnostics)
            is TypedPrefix -> lowerPrefix(expression, diagnostics)
            is TypedBinary -> lowerBinary(expression, diagnostics)
        }

    /**
     * 将整数字面量文本转换为 Int64 IR，超出范围时报告 EXEC003。
     * Converts integer literal text to Int64 IR and reports EXEC003 when it is out of range.
     */
    private fun lowerInteger(expression: TypedInteger, diagnostics: DiagnosticBag): IrExpression? {
        val value = expression.syntax.digits.toLongOrNull()
        return if (value == null) {
            diagnostics.report("EXEC003", "Int64 overflow", expression.syntax.span)
            null
        } else {
            IrInt64(value = value, span = expression.syntax.span)
        }
    }

    /**
     * 降级当前支持的一元前缀表达式。
     * Lowers the currently supported unary prefix expressions.
     */
    private fun lowerPrefix(expression: TypedPrefix, diagnostics: DiagnosticBag): IrExpression? {
        val operand = lowerExpression(expression.operand, diagnostics) ?: return null
        val operator = when (expression.syntax.operator.kind) {
            TokenKinds.Plus -> IrUnaryOperator.Plus
            TokenKinds.Minus -> IrUnaryOperator.Minus
            else -> {
                unsupported(expression, diagnostics)
                return null
            }
        }
        return IrUnary(operator = operator, operand = operand, span = expression.syntax.span)
    }

    /**
     * 降级当前支持的二元表达式，并传播左右 operand 的失败。
     * Lowers the currently supported binary expressions and propagates left or right operand failures.
     */
    private fun lowerBinary(expression: TypedBinary, diagnostics: DiagnosticBag): IrExpression? {
        val left = lowerExpression(expression.left, diagnostics)
        val right = lowerExpression(expression.right, diagnostics)
        if (left == null || right == null) {
            return null
        }

        val operator = when (expression.syntax.operator.kind) {
            TokenKinds.Plus -> IrBinaryOperator.Plus
            TokenKinds.Minus -> IrBinaryOperator.Minus
            TokenKinds.Star -> IrBinaryOperator.Multiply
            TokenKinds.Slash -> IrBinaryOperator.Divide
            else -> {
                unsupported(expression, diagnostics)
                return null
            }
        }
        return IrBinary(left = left, operator = operator, right = right, span = expression.syntax.span)
    }

    /**
     * 报告 malformed typed expression 的 lowering 防御诊断。
     * Reports a lowering defensive diagnostic for a malformed typed expression.
     */
    private fun unsupported(expression: TypedExpression, diagnostics: DiagnosticBag): IrExpression? {
        diagnostics.report("EXEC001", "unsupported expression", expression.syntax.span)
        return null
    }
}
