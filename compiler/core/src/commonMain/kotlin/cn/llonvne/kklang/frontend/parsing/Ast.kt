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
 * AST 中可出现在 program 顶层的 declaration 共同接口。
 * Shared interface for declarations that may appear at the top level of an AST program.
 */
sealed interface Declaration {
    val span: SourceSpan
}

/**
 * 可被 binding symbol 指向的语法来源。
 * Syntax source that may be referenced by a binding symbol.
 */
sealed interface SymbolSyntax {
    val span: SourceSpan
}

/**
 * 当前 AST program，包含 declarations 和最终 expression。
 * Current AST program containing declarations and a final expression.
 */
data class AstProgram(
    val expression: Expression,
    val declarations: List<Declaration> = emptyList(),
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
) : Declaration, SymbolSyntax {
    val name: String
        get() = nameToken.lexeme

    override val span: SourceSpan
        get() = valToken.span.covering(semicolonToken.span)
}

/**
 * modifier declaration AST 节点，保留 outer braces 内的 pattern tokens。
 * AST node for a modifier declaration, preserving pattern tokens inside the outer braces.
 */
data class ModifierDeclaration(
    val modifierToken: Token,
    val nameToken: Token,
    val leftBraceToken: Token,
    val patternTokens: List<Token>,
    val rightBraceToken: Token,
) : Declaration {
    val name: String
        get() = nameToken.lexeme

    override val span: SourceSpan
        get() = modifierToken.span.covering(rightBraceToken.span)
}

/**
 * raw modifier application，parser 只保留完整 token 序列，后续 expansion 再解释。
 * Raw modifier application where the parser only preserves the full token sequence and later expansion interprets it.
 */
data class RawModifierApplication(
    val nameToken: Token,
    val tokens: List<Token>,
) : Declaration {
    init {
        require(tokens.isNotEmpty()) { "raw modifier application requires at least one token" }
    }

    val name: String
        get() = nameToken.lexeme

    override val span: SourceSpan
        get() = tokens.first().span.covering(tokens.last().span)
}

/**
 * 函数参数语法，类型注解在语法上可为空。
 * Function parameter syntax whose type annotation may be absent syntactically.
 */
data class FunctionParameter(
    val nameToken: Token,
    val colonToken: Token?,
    val typeToken: Token?,
) : SymbolSyntax {
    val name: String
        get() = nameToken.lexeme

    val typeName: String?
        get() = typeToken?.lexeme

    override val span: SourceSpan
        get() = if (typeToken == null) nameToken.span else nameToken.span.covering(typeToken.span)
}

/**
 * 函数体 block program，包含局部 val declarations 和最终 expression。
 * Function-body block program containing local val declarations and a final expression.
 */
data class FunctionBody(
    val leftBraceToken: Token,
    val declarations: List<ValDeclaration>,
    val expression: Expression,
    val rightBraceToken: Token,
) {
    val span: SourceSpan
        get() = leftBraceToken.span.covering(rightBraceToken.span)
}

/**
 * `fn` modifier expansion 后的结构化函数声明。
 * Structured function declaration produced after `fn` modifier expansion.
 */
data class FunctionDeclaration(
    val modifierToken: Token,
    val nameToken: Token,
    val leftParenToken: Token,
    val parameters: List<FunctionParameter>,
    val rightParenToken: Token,
    val body: FunctionBody,
) : Declaration, SymbolSyntax {
    val modifierName: String
        get() = modifierToken.lexeme

    val name: String
        get() = nameToken.lexeme

    override val span: SourceSpan
        get() = modifierToken.span.covering(body.rightBraceToken.span)
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
 * 字符串字面量表达式，第一版只去掉包围的双引号。
 * String-literal expression that only strips surrounding double quotes in the first version.
 */
data class StringExpression(val token: Token) : Expression {
    val text: String
        get() = token.lexeme.substring(1, token.lexeme.length - 1)

    override val span: SourceSpan
        get() = token.span
}

/**
 * 内建调用表达式，当前 callee 只允许 identifier。
 * Builtin call expression whose callee is currently limited to an identifier.
 */
data class CallExpression(
    val callee: IdentifierExpression,
    val leftParen: Token,
    val arguments: List<Expression>,
    val rightParen: Token,
) : Expression {
    constructor(
        callee: IdentifierExpression,
        leftParen: Token,
        argument: Expression,
        rightParen: Token,
    ) : this(callee, leftParen, listOf(argument), rightParen)

    val argument: Expression
        get() = arguments.single()

    override val span: SourceSpan =
        callee.span.covering(rightParen.span)
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
