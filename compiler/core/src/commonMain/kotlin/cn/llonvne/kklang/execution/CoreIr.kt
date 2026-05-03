package cn.llonvne.kklang.execution

import cn.llonvne.kklang.frontend.SourceSpan

/**
 * Core IR program，包含不可变 val declarations 和最终 expression。
 * Core IR program containing immutable val declarations and a final expression.
 */
data class IrProgram(
    val declarations: List<IrValDeclaration>,
    val expression: IrExpression,
    val functions: List<IrFunctionDeclaration> = emptyList(),
) {
    val span: SourceSpan
        get() {
            val firstDeclaration = declarations.firstOrNull()
            if (firstDeclaration != null) {
                return firstDeclaration.span.covering(expression.span)
            }
            val firstFunction = functions.firstOrNull()
            return if (firstFunction == null) {
                expression.span
            } else {
                firstFunction.span.covering(expression.span)
            }
        }
}

/**
 * Core IR 中的不可变 val declaration。
 * Immutable val declaration in Core IR.
 */
data class IrValDeclaration(
    val name: String,
    val initializer: IrExpression,
    val span: SourceSpan,
)

/**
 * Core IR 中的顶层函数声明。
 * Top-level function declaration in Core IR.
 */
data class IrFunctionDeclaration(
    val name: String,
    val parameters: List<String>,
    val body: IrProgram,
    val span: SourceSpan,
)

/**
 * Core IR expression 的共同接口，所有节点都保留来源 span。
 * Shared interface for Core IR expressions; every node preserves its source span.
 */
sealed interface IrExpression {
    val span: SourceSpan
}

/**
 * 64 位有符号整数字面量的 Core IR 节点。
 * Core IR node for a signed 64-bit integer literal.
 */
data class IrInt64(
    val value: Long,
    override val span: SourceSpan,
) : IrExpression

/**
 * 字符串字面量的 Core IR 节点。
 * Core IR node for a string literal.
 */
data class IrString(
    val value: String,
    override val span: SourceSpan,
) : IrExpression

/**
 * 内建 print 调用的 Core IR 节点。
 * Core IR node for a builtin print call.
 */
data class IrPrint(
    val argument: IrExpression,
    override val span: SourceSpan,
) : IrExpression

/**
 * 不可变变量引用的 Core IR 节点。
 * Core IR node for an immutable variable reference.
 */
data class IrVariable(
    val name: String,
    override val span: SourceSpan,
) : IrExpression

/**
 * 函数调用的 Core IR 节点。
 * Core IR node for a function call.
 */
data class IrCall(
    val callee: String,
    val arguments: List<IrExpression>,
    override val span: SourceSpan,
) : IrExpression

/**
 * 一元整数运算的 Core IR 节点。
 * Core IR node for a unary integer operation.
 */
data class IrUnary(
    val operator: IrUnaryOperator,
    val operand: IrExpression,
    override val span: SourceSpan,
) : IrExpression

/**
 * 二元整数运算的 Core IR 节点。
 * Core IR node for a binary integer operation.
 */
data class IrBinary(
    val left: IrExpression,
    val operator: IrBinaryOperator,
    val right: IrExpression,
    override val span: SourceSpan,
) : IrExpression

/**
 * 当前支持的一元 Core IR 运算符。
 * Unary Core IR operators supported by the current execution scope.
 */
enum class IrUnaryOperator(val text: String) {
    Plus("pos"),
    Minus("neg"),
}

/**
 * 当前支持的二元 Core IR 运算符。
 * Binary Core IR operators supported by the current execution scope.
 */
enum class IrBinaryOperator(val text: String) {
    Plus("+"),
    Minus("-"),
    Multiply("*"),
    Divide("/"),
}
