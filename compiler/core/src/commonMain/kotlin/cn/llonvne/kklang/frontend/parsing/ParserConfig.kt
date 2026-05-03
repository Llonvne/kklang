package cn.llonvne.kklang.frontend.parsing

import cn.llonvne.kklang.frontend.lexing.Token
import cn.llonvne.kklang.frontend.lexing.TokenKind
import cn.llonvne.kklang.frontend.lexing.TokenKinds

/**
 * prefix parselet 接口，用于解析以某个 token 开头的 expression。
 * Prefix parselet interface used to parse an expression that starts with a token.
 */
fun interface PrefixParselet {
    /**
     * 使用已消费的 prefix token 解析 expression。
     * Parses an expression using the already consumed prefix token.
     */
    fun parse(parser: Parser, token: Token): Expression
}

/**
 * 二元运算符的结合性。
 * Associativity for binary operators.
 */
enum class Associativity {
    Left,
    Right,
}

/**
 * infix parselet，保存优先级和结合性以解析二元 expression。
 * Infix parselet that stores precedence and associativity for parsing binary expressions.
 */
class InfixParselet private constructor(
    val precedence: Int,
    private val associativity: Associativity,
) {
    init {
        require(precedence > 0) { "infix precedence must be greater than zero" }
    }

    /**
     * 使用左 operand 和已消费的 operator 解析右 operand。
     * Parses the right operand using the left operand and the already consumed operator.
     */
    fun parse(parser: Parser, left: Expression, operator: Token): Expression {
        val rightBindingPower = when (associativity) {
            Associativity.Left -> precedence
            Associativity.Right -> precedence - 1
        }
        return BinaryExpression(left = left, operator = operator, right = parser.parseExpression(rightBindingPower))
    }

    /**
     * InfixParselet 的工厂入口。
     * Factory entry point for InfixParselet.
     */
    companion object {
        /**
         * 创建一个二元 infix parselet。
         * Creates one binary infix parselet.
         */
        fun binary(precedence: Int, associativity: Associativity): InfixParselet =
            InfixParselet(precedence = precedence, associativity = associativity)
    }
}

/**
 * parser 配置，按 token kind 注册 prefix 和 infix parselet。
 * Parser configuration that registers prefix and infix parselets by token kind.
 */
class ParserConfig private constructor(
    private val prefixParselets: Map<TokenKind, PrefixParselet>,
    private val infixParselets: Map<TokenKind, InfixParselet>,
) {
    init {
        require(prefixParselets.isNotEmpty()) { "parser config requires at least one prefix parselet" }
    }

    /**
     * 查找指定 token kind 的 prefix parselet。
     * Looks up the prefix parselet for the given token kind.
     */
    fun prefix(kind: TokenKind): PrefixParselet? = prefixParselets[kind]

    /**
     * 查找指定 token kind 的 infix parselet。
     * Looks up the infix parselet for the given token kind.
     */
    fun infix(kind: TokenKind): InfixParselet? = infixParselets[kind]

    /**
     * 返回替换或新增一个 prefix parselet 的新配置。
     * Returns a new configuration with one prefix parselet replaced or added.
     */
    fun withPrefix(kind: TokenKind, parselet: PrefixParselet): ParserConfig =
        ParserConfig(prefixParselets = prefixParselets + (kind to parselet), infixParselets = infixParselets)

    /**
     * 返回替换或新增一个 infix parselet 的新配置。
     * Returns a new configuration with one infix parselet replaced or added.
     */
    fun withInfix(kind: TokenKind, parselet: InfixParselet): ParserConfig =
        ParserConfig(prefixParselets = prefixParselets, infixParselets = infixParselets + (kind to parselet))

    /**
     * ParserConfig 的默认配置和 builder 工厂。
     * Default configuration and builder factories for ParserConfig.
     */
    companion object {
        const val ADDITIVE_PRECEDENCE: Int = 10
        const val MULTIPLICATIVE_PRECEDENCE: Int = 20
        const val PREFIX_PRECEDENCE: Int = 30
        const val CALL_PRECEDENCE: Int = 40

        /**
         * 创建 seed grammar 的默认 parser 配置。
         * Creates the default parser configuration for the seed grammar.
         */
        fun default(): ParserConfig =
            builder()
                .defaultParselets()
                .build()

        /**
         * 创建 parser 配置 builder。
         * Creates a parser configuration builder.
         */
        fun builder(): Builder = Builder()
    }

    /**
     * parser 配置 builder，保持 parselet 注册顺序。
     * Builder for parser configuration that preserves parselet registration order.
     */
    class Builder {
        private val prefixParselets = linkedMapOf<TokenKind, PrefixParselet>()
        private val infixParselets = linkedMapOf<TokenKind, InfixParselet>()

        /**
         * 注册 seed grammar 的默认 parselet。
         * Registers the default parselets for the seed grammar.
         */
        fun defaultParselets(): Builder {
            prefix(TokenKinds.Identifier) { _, token -> IdentifierExpression(token) }
            prefix(TokenKinds.Integer) { _, token -> IntegerExpression(token) }
            prefix(TokenKinds.String) { _, token -> StringExpression(token) }
            prefix(TokenKinds.LeftParen) { parser, token ->
                val expression = parser.parseExpression()
                val rightParen = parser.expect(TokenKinds.RightParen, "PARSE003", "expected right_paren")
                GroupedExpression(leftParen = token, expression = expression, rightParen = rightParen)
            }
            prefix(TokenKinds.Plus, prefixOperator())
            prefix(TokenKinds.Minus, prefixOperator())
            infix(TokenKinds.Plus, InfixParselet.binary(ADDITIVE_PRECEDENCE, Associativity.Left))
            infix(TokenKinds.Minus, InfixParselet.binary(ADDITIVE_PRECEDENCE, Associativity.Left))
            infix(TokenKinds.Star, InfixParselet.binary(MULTIPLICATIVE_PRECEDENCE, Associativity.Left))
            infix(TokenKinds.Slash, InfixParselet.binary(MULTIPLICATIVE_PRECEDENCE, Associativity.Left))
            return this
        }

        /**
         * 注册或替换一个 prefix parselet。
         * Registers or replaces one prefix parselet.
         */
        fun prefix(kind: TokenKind, parselet: PrefixParselet): Builder {
            prefixParselets[kind] = parselet
            return this
        }

        /**
         * 注册或替换一个 infix parselet。
         * Registers or replaces one infix parselet.
         */
        fun infix(kind: TokenKind, parselet: InfixParselet): Builder {
            infixParselets[kind] = parselet
            return this
        }

        /**
         * 构造不可变 parser 配置。
         * Builds an immutable parser configuration.
         */
        fun build(): ParserConfig =
            ParserConfig(prefixParselets = prefixParselets.toMap(), infixParselets = infixParselets.toMap())

        /**
         * 创建默认的一元前缀运算 parselet。
         * Creates the default unary prefix-operator parselet.
         */
        private fun prefixOperator(): PrefixParselet =
            PrefixParselet { parser, token ->
                PrefixExpression(operator = token, operand = parser.parseExpression(PREFIX_PRECEDENCE))
            }
    }
}
