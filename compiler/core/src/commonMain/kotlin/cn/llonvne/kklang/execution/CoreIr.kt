package cn.llonvne.kklang.execution

import cn.llonvne.kklang.frontend.SourceSpan

sealed interface IrExpression {
    val span: SourceSpan
}

data class IrInt64(
    val value: Long,
    override val span: SourceSpan,
) : IrExpression

data class IrUnary(
    val operator: IrUnaryOperator,
    val operand: IrExpression,
    override val span: SourceSpan,
) : IrExpression

data class IrBinary(
    val left: IrExpression,
    val operator: IrBinaryOperator,
    val right: IrExpression,
    override val span: SourceSpan,
) : IrExpression

enum class IrUnaryOperator(val text: String) {
    Plus("pos"),
    Minus("neg"),
}

enum class IrBinaryOperator(val text: String) {
    Plus("+"),
    Minus("-"),
    Multiply("*"),
    Divide("/"),
}

