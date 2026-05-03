package cn.llonvne.kklang.frontend.lexing

import cn.llonvne.kklang.frontend.SourceText

/**
 * 一条可扩展 lexer rule，返回从指定 offset 开始的匹配长度。
 * Extensible lexer rule that returns a match length starting at a given offset.
 */
class LexerRule private constructor(
    val name: String,
    private val kind: TokenKind,
    private val matcher: (SourceText, Int) -> Int,
) {
    init {
        require(name.isNotBlank()) { "lexer rule name must not be blank" }
    }

    /**
     * 在指定 offset 尝试匹配源码，未匹配时返回 null。
     * Attempts to match source at the given offset and returns null when it does not match.
     */
    fun match(source: SourceText, offset: Int): TokenMatch? {
        require(offset in 0 until source.length) { "offset must point at an existing character" }
        val length = matcher(source, offset)
        return if (length > 0) TokenMatch(kind = kind, length = length) else null
    }

    /**
     * LexerRule 的内建工厂集合。
     * Built-in factory set for LexerRule.
     */
    companion object {
        /**
         * 创建一个匹配固定字面量的 lexer rule。
         * Creates a lexer rule that matches one fixed literal.
         */
        fun literal(name: String, kind: TokenKind, literal: String): LexerRule {
            require(literal.isNotEmpty()) { "literal rule must not be empty" }
            return LexerRule(name = name, kind = kind) { source, offset ->
                if (source.content.startsWith(literal, startIndex = offset)) literal.length else 0
            }
        }

        /**
         * 创建一个先匹配起始字符再连续消费后续字符的 lexer rule。
         * Creates a lexer rule that matches a start character and then consumes continuation characters.
         */
        fun run(
            name: String,
            kind: TokenKind,
            start: (Char) -> Boolean,
            continueWith: (Char) -> Boolean,
        ): LexerRule =
            LexerRule(name = name, kind = kind) { source, offset ->
                if (!start(source.content[offset])) {
                    0
                } else {
                    var endOffset = offset + 1
                    while (endOffset < source.length && continueWith(source.content[endOffset])) {
                        endOffset += 1
                    }
                    endOffset - offset
                }
            }

        /**
         * 创建第一版字符串字面量规则：只接受同一行内闭合的双引号文本。
         * Creates the first string-literal rule: only closed double-quoted text on one line is accepted.
         */
        fun stringLiteral(name: String, kind: TokenKind): LexerRule =
            LexerRule(name = name, kind = kind) { source, offset ->
                if (source.content[offset] != '"') {
                    0
                } else {
                    var endOffset = offset + 1
                    while (endOffset < source.length && source.content[endOffset] != '"' && source.content[endOffset] != '\n') {
                        endOffset += 1
                    }
                    if (endOffset < source.length && source.content[endOffset] == '"') {
                        endOffset + 1 - offset
                    } else {
                        0
                    }
                }
            }
    }
}
