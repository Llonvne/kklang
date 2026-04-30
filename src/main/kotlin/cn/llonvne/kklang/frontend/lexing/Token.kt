package cn.llonvne.kklang.frontend.lexing

import cn.llonvne.kklang.frontend.SourceSpan

@JvmInline
value class TokenKind(val id: String) {
    init {
        require(id.isNotBlank()) { "token kind id must not be blank" }
    }

    override fun toString(): String = id
}

object TokenKinds {
    val Identifier: TokenKind = TokenKind("identifier")
    val Integer: TokenKind = TokenKind("integer")
    val LeftParen: TokenKind = TokenKind("left_paren")
    val RightParen: TokenKind = TokenKind("right_paren")
    val Plus: TokenKind = TokenKind("plus")
    val Minus: TokenKind = TokenKind("minus")
    val Star: TokenKind = TokenKind("star")
    val Slash: TokenKind = TokenKind("slash")
    val Whitespace: TokenKind = TokenKind("whitespace")
    val Unknown: TokenKind = TokenKind("unknown")
    val EndOfFile: TokenKind = TokenKind("eof")
}

data class Token(
    val kind: TokenKind,
    val lexeme: String,
    val span: SourceSpan,
)

data class TokenMatch(
    val kind: TokenKind,
    val length: Int,
) {
    init {
        require(length > 0) { "token match length must be greater than zero" }
    }
}

