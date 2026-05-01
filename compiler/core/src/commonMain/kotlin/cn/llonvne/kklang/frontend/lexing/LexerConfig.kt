package cn.llonvne.kklang.frontend.lexing

/**
 * lexer 配置，包含按顺序尝试的规则和 trivia 发出策略。
 * Lexer configuration containing ordered rules and the trivia emission policy.
 */
class LexerConfig private constructor(
    val rules: List<LexerRule>,
    val emitTrivia: Boolean,
) {
    init {
        require(rules.isNotEmpty()) { "lexer config requires at least one rule" }
    }

    /**
     * 返回追加一条规则后的新配置。
     * Returns a new configuration with one rule appended.
     */
    fun withRule(rule: LexerRule): LexerConfig =
        LexerConfig(rules = rules + rule, emitTrivia = emitTrivia)

    /**
     * 返回会发出 whitespace trivia 的新配置。
     * Returns a new configuration that emits whitespace trivia.
     */
    fun withTrivia(): LexerConfig =
        LexerConfig(rules = rules, emitTrivia = true)

    /**
     * LexerConfig 的默认配置和 builder 工厂。
     * Default configuration and builder factories for LexerConfig.
     */
    companion object {
        /**
         * 创建包含 seed 语言默认规则的 lexer 配置。
         * Creates the lexer configuration containing the seed language default rules.
         */
        fun default(): LexerConfig =
            builder()
                .defaultRules()
                .build()

        /**
         * 创建可增量组装 lexer 配置的 builder。
         * Creates a builder for incrementally assembling a lexer configuration.
         */
        fun builder(): Builder = Builder()
    }

    /**
     * lexer 配置 builder，保持规则注册顺序。
     * Builder for lexer configuration that preserves rule registration order.
     */
    class Builder {
        private val rules = mutableListOf<LexerRule>()

        /**
         * 注册 seed 语言的默认 lexer 规则。
         * Registers the seed language default lexer rules.
         */
        fun defaultRules(): Builder {
            rule(LexerRule.literal("val", TokenKinds.Val, "val"))
            rule(LexerRule.run("identifier", TokenKinds.Identifier, ::isIdentifierStart, ::isIdentifierContinue))
            rule(LexerRule.run("integer", TokenKinds.Integer, Char::isDigit, Char::isDigit))
            rule(LexerRule.literal("left paren", TokenKinds.LeftParen, "("))
            rule(LexerRule.literal("right paren", TokenKinds.RightParen, ")"))
            rule(LexerRule.literal("equals", TokenKinds.Equals, "="))
            rule(LexerRule.literal("semicolon", TokenKinds.Semicolon, ";"))
            rule(LexerRule.literal("plus", TokenKinds.Plus, "+"))
            rule(LexerRule.literal("minus", TokenKinds.Minus, "-"))
            rule(LexerRule.literal("star", TokenKinds.Star, "*"))
            rule(LexerRule.literal("slash", TokenKinds.Slash, "/"))
            rule(LexerRule.run("whitespace", TokenKinds.Whitespace, Char::isWhitespace, Char::isWhitespace))
            return this
        }

        /**
         * 追加一条 lexer rule 并返回当前 builder。
         * Appends one lexer rule and returns this builder.
         */
        fun rule(rule: LexerRule): Builder {
            rules += rule
            return this
        }

        /**
         * 构造不可变 lexer 配置。
         * Builds an immutable lexer configuration.
         */
        fun build(): LexerConfig =
            LexerConfig(rules = rules.toList(), emitTrivia = false)
    }
}

/**
 * 判断字符是否能作为默认 identifier 的起始字符。
 * Checks whether a character can start a default identifier.
 */
private fun isIdentifierStart(char: Char): Boolean =
    char == '_' || char in 'A'..'Z' || char in 'a'..'z'

/**
 * 判断字符是否能作为默认 identifier 的后续字符。
 * Checks whether a character can continue a default identifier.
 */
private fun isIdentifierContinue(char: Char): Boolean =
    isIdentifierStart(char) || char in '0'..'9'
