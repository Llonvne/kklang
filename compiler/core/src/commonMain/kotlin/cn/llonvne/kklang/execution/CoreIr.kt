package cn.llonvne.kklang.execution

import cn.llonvne.kklang.frontend.SourceSpan

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
