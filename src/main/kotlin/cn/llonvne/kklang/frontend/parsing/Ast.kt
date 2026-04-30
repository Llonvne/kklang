package cn.llonvne.kklang.frontend.parsing

import cn.llonvne.kklang.frontend.SourceSpan
import cn.llonvne.kklang.frontend.lexing.Token

sealed interface Expression {
    val span: SourceSpan
}

data class IdentifierExpression(val token: Token) : Expression {
    val name: String
        get() = token.lexeme

    override val span: SourceSpan
        get() = token.span
}

data class IntegerExpression(val token: Token) : Expression {
    val digits: String
        get() = token.lexeme

    override val span: SourceSpan
        get() = token.span
}

data class PrefixExpression(
    val operator: Token,
    val operand: Expression,
) : Expression {
    override val span: SourceSpan =
        operator.span.covering(operand.span)
}

data class BinaryExpression(
    val left: Expression,
    val operator: Token,
    val right: Expression,
) : Expression {
    override val span: SourceSpan =
        left.span.covering(right.span)
}

data class GroupedExpression(
    val leftParen: Token,
    val expression: Expression,
    val rightParen: Token,
) : Expression {
    override val span: SourceSpan =
        leftParen.span.covering(rightParen.span)
}

data class MissingExpression(
    override val span: SourceSpan,
) : Expression

