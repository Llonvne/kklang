package cn.llonvne.kklang.execution

import cn.llonvne.kklang.frontend.diagnostics.Diagnostic
import cn.llonvne.kklang.frontend.diagnostics.DiagnosticBag
import cn.llonvne.kklang.frontend.lexing.TokenKinds
import cn.llonvne.kklang.typechecking.TypedBinary
import cn.llonvne.kklang.typechecking.TypedExpression
import cn.llonvne.kklang.typechecking.TypedFunctionCall
import cn.llonvne.kklang.typechecking.TypedFunctionDeclaration
import cn.llonvne.kklang.typechecking.TypedGrouped
import cn.llonvne.kklang.typechecking.TypedInteger
import cn.llonvne.kklang.typechecking.TypedPrintCall
import cn.llonvne.kklang.typechecking.TypedPrefix
import cn.llonvne.kklang.typechecking.TypedProgram
import cn.llonvne.kklang.typechecking.TypedString
import cn.llonvne.kklang.typechecking.TypedValDeclaration
import cn.llonvne.kklang.typechecking.TypedVariable

/**
 * AST 到 Core IR lowering 的结果，成功时 ir 非空，失败时 diagnostics 非空。
 * Result of lowering AST to Core IR; ir is present on success and diagnostics are present on failure.
 */
data class IrLoweringResult(
    val program: IrProgram?,
    val diagnostics: List<Diagnostic>,
) {
    val ir: IrExpression?
        get() = program?.expression

    val hasErrors: Boolean
        get() = diagnostics.isNotEmpty()
}

/**
 * typed program 到 Core IR lowering 的可替换接口。
 * Replaceable interface for lowering typed programs to Core IR.
 */
fun interface IrLowerer {
    /**
     * 降级一个 typed program 并返回 Core IR program 或 diagnostics。
     * Lowers one typed program and returns either a Core IR program or diagnostics.
     */
    fun lower(program: TypedProgram): IrLoweringResult
}

/**
 * 当前最小 lowerer，只把已类型检查的整数表达式降级为 Core IR。
 * Current minimal lowerer that lowers only type-checked integer expressions into Core IR.
 */
class CoreIrLowerer : IrLowerer {
    /**
     * 降级 typed program，并在过程中收集 lowering diagnostics。
     * Lowers the typed program while collecting lowering diagnostics.
     */
    override fun lower(program: TypedProgram): IrLoweringResult {
        val diagnostics = DiagnosticBag()
        val declarations = program.declarations.mapNotNull { lowerDeclaration(it, diagnostics) }
        val functions = program.functions.mapNotNull { lowerFunction(it, diagnostics) }
        val expression = lowerExpression(program.expression, diagnostics)
            ?: return IrLoweringResult(program = null, diagnostics = diagnostics.toList())
        val diagnosticsList = diagnostics.toList()
        if (diagnosticsList.isNotEmpty()) {
            return IrLoweringResult(program = null, diagnostics = diagnosticsList)
        }
        return IrLoweringResult(
            program = IrProgram(declarations = declarations, expression = expression, functions = functions),
            diagnostics = diagnosticsList,
        )
    }

    /**
     * 降级单个 typed expression，测试和局部调用可使用此便捷入口。
     * Lowers one typed expression; tests and local callers may use this convenience entry point.
     */
    fun lower(expression: TypedExpression): IrLoweringResult =
        lower(TypedProgram(declarations = emptyList(), expression = expression))

    /**
     * 降级一个 typed val declaration。
     * Lowers one typed val declaration.
     */
    private fun lowerDeclaration(declaration: TypedValDeclaration, diagnostics: DiagnosticBag): IrValDeclaration? {
        val initializer = lowerExpression(declaration.initializer, diagnostics) ?: return null
        return IrValDeclaration(name = declaration.name, initializer = initializer, span = declaration.syntax.span)
    }

    /**
     * 降级一个 typed function declaration。
     * Lowers one typed function declaration.
     */
    private fun lowerFunction(declaration: TypedFunctionDeclaration, diagnostics: DiagnosticBag): IrFunctionDeclaration? {
        val bodyDeclarations = declaration.declarations.mapNotNull { lowerDeclaration(it, diagnostics) }
        val bodyExpression = lowerExpression(declaration.expression, diagnostics) ?: return null
        return IrFunctionDeclaration(
            name = declaration.name,
            parameters = declaration.parameters.map { it.name },
            body = IrProgram(declarations = bodyDeclarations, expression = bodyExpression),
            span = declaration.syntax.span,
        )
    }

    /**
     * 按 typed AST expression 类型分派 lowering。
     * Dispatches lowering by typed AST expression type.
     */
    private fun lowerExpression(expression: TypedExpression, diagnostics: DiagnosticBag): IrExpression? =
        when (expression) {
            is TypedInteger -> lowerInteger(expression, diagnostics)
            is TypedString -> IrString(value = expression.syntax.text, span = expression.syntax.span)
            is TypedPrintCall -> lowerPrintCall(expression, diagnostics)
            is TypedFunctionCall -> lowerFunctionCall(expression, diagnostics)
            is TypedVariable -> IrVariable(name = expression.symbol.name, span = expression.syntax.span)
            is TypedGrouped -> lowerExpression(expression.inner, diagnostics)
            is TypedPrefix -> lowerPrefix(expression, diagnostics)
            is TypedBinary -> lowerBinary(expression, diagnostics)
        }

    /**
     * 降级内建 print 调用，并传播 argument lowering 失败。
     * Lowers a builtin print call and propagates argument lowering failures.
     */
    private fun lowerPrintCall(expression: TypedPrintCall, diagnostics: DiagnosticBag): IrExpression? {
        val argument = lowerExpression(expression.argument, diagnostics) ?: return null
        return IrPrint(argument = argument, span = expression.syntax.span)
    }

    /**
     * 降级函数调用，并传播任一 argument lowering 失败。
     * Lowers a function call and propagates any argument lowering failure.
     */
    private fun lowerFunctionCall(expression: TypedFunctionCall, diagnostics: DiagnosticBag): IrExpression? {
        val arguments = expression.arguments.mapNotNull { lowerExpression(it, diagnostics) }
        if (arguments.size != expression.arguments.size) {
            return null
        }
        return IrCall(callee = expression.symbol.name, arguments = arguments, span = expression.syntax.span)
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
