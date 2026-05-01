package cn.llonvne.kklang.frontend.lexing

class LexerConfig private constructor(
    val rules: List<LexerRule>,
    val emitTrivia: Boolean,
) {
    init {
        require(rules.isNotEmpty()) { "lexer config requires at least one rule" }
    }

    fun withRule(rule: LexerRule): LexerConfig =
        LexerConfig(rules = rules + rule, emitTrivia = emitTrivia)

    fun withTrivia(): LexerConfig =
        LexerConfig(rules = rules, emitTrivia = true)

    companion object {
        fun default(): LexerConfig =
            builder()
                .defaultRules()
                .build()

        fun builder(): Builder = Builder()
    }

    class Builder {
        private val rules = mutableListOf<LexerRule>()

        fun defaultRules(): Builder {
            rule(LexerRule.run("identifier", TokenKinds.Identifier, ::isIdentifierStart, ::isIdentifierContinue))
            rule(LexerRule.run("integer", TokenKinds.Integer, Char::isDigit, Char::isDigit))
            rule(LexerRule.literal("left paren", TokenKinds.LeftParen, "("))
            rule(LexerRule.literal("right paren", TokenKinds.RightParen, ")"))
            rule(LexerRule.literal("plus", TokenKinds.Plus, "+"))
            rule(LexerRule.literal("minus", TokenKinds.Minus, "-"))
            rule(LexerRule.literal("star", TokenKinds.Star, "*"))
            rule(LexerRule.literal("slash", TokenKinds.Slash, "/"))
            rule(LexerRule.run("whitespace", TokenKinds.Whitespace, Char::isWhitespace, Char::isWhitespace))
            return this
        }

        fun rule(rule: LexerRule): Builder {
            rules += rule
            return this
        }

        fun build(): LexerConfig =
            LexerConfig(rules = rules.toList(), emitTrivia = false)
    }
}

private fun isIdentifierStart(char: Char): Boolean =
    char == '_' || char in 'A'..'Z' || char in 'a'..'z'

private fun isIdentifierContinue(char: Char): Boolean =
    isIdentifierStart(char) || char in '0'..'9'
