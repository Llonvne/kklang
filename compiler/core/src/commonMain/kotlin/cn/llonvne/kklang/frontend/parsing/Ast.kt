package cn.llonvne.kklang.frontend.parsing

import cn.llonvne.kklang.frontend.SourceSpan
import cn.llonvne.kklang.frontend.lexing.Token

/**
 * seed parser 产生的所有 expression AST 节点的共同接口。
 * Shared interface for every expression AST node produced by the seed parser.
 */
sealed interface Expression {
    val span: SourceSpan
}

/**
 * 当前 AST program，包含不可变 val declarations 和最终 expression。
 * Current AST program containing immutable val declarations and a final expression.
 */
data class AstProgram(
    val expression: Expression,
    val declarations: List<ValDeclaration> = emptyList(),
) {
    val span: SourceSpan
        get() {
            val firstDeclaration = declarations.firstOrNull()
            return if (firstDeclaration == null) {
                expression.span
            } else {
                firstDeclaration.span.covering(expression.span)
            }
        }
}

/**
 * 不可变 val declaration AST 节点。
 * AST node for an immutable val declaration.
 */
data class ValDeclaration(
    val valToken: Token,
    val nameToken: Token,
    val equalsToken: Token,
    val initializer: Expression,
    val semicolonToken: Token,
) {
    val name: String
        get() = nameToken.lexeme

    val span: SourceSpan
        get() = valToken.span.covering(semicolonToken.span)
}

/**
 * 标识符表达式，保留原始 token 并从 token 派生名字。
 * Identifier expression that keeps the original token and derives the name from it.
 */
data class IdentifierExpression(val token: Token) : Expression {
    val name: String
        get() = token.lexeme

    override val span: SourceSpan
        get() = token.span
}

/**
 * 整数字面量表达式，保留原始数字文本。
 * Integer literal expression that preserves the original digit text.
 */
data class IntegerExpression(val token: Token) : Expression {
    val digits: String
        get() = token.lexeme

    override val span: SourceSpan
        get() = token.span
}

/**
 * 前缀表达式，span 覆盖 operator 和 operand。
 * Prefix expression whose span covers the operator and operand.
 */
data class PrefixExpression(
    val operator: Token,
    val operand: Expression,
) : Expression {
    override val span: SourceSpan =
        operator.span.covering(operand.span)
}

/**
 * 二元表达式，span 覆盖左右 operand。
 * Binary expression whose span covers both operands.
 */
data class BinaryExpression(
    val left: Expression,
    val operator: Token,
    val right: Expression,
) : Expression {
    override val span: SourceSpan =
        left.span.covering(right.span)
}

/**
 * 分组表达式，保留左右括号 token。
 * Grouped expression that preserves both parenthesis tokens.
 */
data class GroupedExpression(
    val leftParen: Token,
    val expression: Expression,
    val rightParen: Token,
) : Expression {
    override val span: SourceSpan =
        leftParen.span.covering(rightParen.span)
}

/**
 * parser 恢复用的缺失表达式节点。
 * Missing expression node used by parser recovery.
 */
data class MissingExpression(
    override val span: SourceSpan,
) : Expression
