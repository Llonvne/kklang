package cn.llonvne.kklang.frontend.parsing

import cn.llonvne.kklang.frontend.lexing.Token
import cn.llonvne.kklang.frontend.lexing.TokenKind
import cn.llonvne.kklang.frontend.lexing.TokenKinds

fun interface PrefixParselet {
    fun parse(parser: Parser, token: Token): Expression
}

enum class Associativity {
    Left,
    Right,
}

class InfixParselet private constructor(
    val precedence: Int,
    private val associativity: Associativity,
) {
    init {
        require(precedence > 0) { "infix precedence must be greater than zero" }
    }

    fun parse(parser: Parser, left: Expression, operator: Token): Expression {
        val rightBindingPower = when (associativity) {
            Associativity.Left -> precedence
            Associativity.Right -> precedence - 1
        }
        return BinaryExpression(left = left, operator = operator, right = parser.parseExpression(rightBindingPower))
    }

    companion object {
        fun binary(precedence: Int, associativity: Associativity): InfixParselet =
            InfixParselet(precedence = precedence, associativity = associativity)
    }
}

class ParserConfig private constructor(
    private val prefixParselets: Map<TokenKind, PrefixParselet>,
    private val infixParselets: Map<TokenKind, InfixParselet>,
) {
    init {
        require(prefixParselets.isNotEmpty()) { "parser config requires at least one prefix parselet" }
    }

    fun prefix(kind: TokenKind): PrefixParselet? = prefixParselets[kind]

    fun infix(kind: TokenKind): InfixParselet? = infixParselets[kind]

    fun withPrefix(kind: TokenKind, parselet: PrefixParselet): ParserConfig =
        ParserConfig(prefixParselets = prefixParselets + (kind to parselet), infixParselets = infixParselets)

    fun withInfix(kind: TokenKind, parselet: InfixParselet): ParserConfig =
        ParserConfig(prefixParselets = prefixParselets, infixParselets = infixParselets + (kind to parselet))

    companion object {
        const val ADDITIVE_PRECEDENCE: Int = 10
        const val MULTIPLICATIVE_PRECEDENCE: Int = 20
        const val PREFIX_PRECEDENCE: Int = 30

        fun default(): ParserConfig =
            builder()
                .defaultParselets()
                .build()

        fun builder(): Builder = Builder()
    }

    class Builder {
        private val prefixParselets = linkedMapOf<TokenKind, PrefixParselet>()
        private val infixParselets = linkedMapOf<TokenKind, InfixParselet>()

        fun defaultParselets(): Builder {
            prefix(TokenKinds.Identifier) { _, token -> IdentifierExpression(token) }
            prefix(TokenKinds.Integer) { _, token -> IntegerExpression(token) }
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

        fun prefix(kind: TokenKind, parselet: PrefixParselet): Builder {
            prefixParselets[kind] = parselet
            return this
        }

        fun infix(kind: TokenKind, parselet: InfixParselet): Builder {
            infixParselets[kind] = parselet
            return this
        }

        fun build(): ParserConfig =
            ParserConfig(prefixParselets = prefixParselets.toMap(), infixParselets = infixParselets.toMap())

        private fun prefixOperator(): PrefixParselet =
            PrefixParselet { parser, token ->
                PrefixExpression(operator = token, operand = parser.parseExpression(PREFIX_PRECEDENCE))
            }
    }
}

