package cn.llonvne.kklang.frontend.lexing

import cn.llonvne.kklang.frontend.SourceText

class LexerRule private constructor(
    val name: String,
    private val kind: TokenKind,
    private val matcher: (SourceText, Int) -> Int,
) {
    init {
        require(name.isNotBlank()) { "lexer rule name must not be blank" }
    }

    fun match(source: SourceText, offset: Int): TokenMatch? {
        require(offset in 0 until source.length) { "offset must point at an existing character" }
        val length = matcher(source, offset)
        return if (length > 0) TokenMatch(kind = kind, length = length) else null
    }

    companion object {
        fun literal(name: String, kind: TokenKind, literal: String): LexerRule {
            require(literal.isNotEmpty()) { "literal rule must not be empty" }
            return LexerRule(name = name, kind = kind) { source, offset ->
                if (source.content.startsWith(literal, startIndex = offset)) literal.length else 0
            }
        }

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
    }
}

