package cn.llonvne.kklang.frontend.lexing

import cn.llonvne.kklang.frontend.SourceSpan

/**
 * lexer 和 parser 共享的 token 类型标识。
 * Token kind identifier shared by the lexer and parser.
 */
data class TokenKind(val id: String) {
    init {
        require(id.isNotBlank()) { "token kind id must not be blank" }
    }

    /**
     * 返回 token kind 的稳定 id。
     * Returns the stable id of this token kind.
     */
    override fun toString(): String = id
}

/**
 * 当前默认词法器使用的内建 token kind 集合。
 * Built-in token kind set used by the current default lexer.
 */
object TokenKinds {
    val Identifier: TokenKind = TokenKind("identifier")
    val Integer: TokenKind = TokenKind("integer")
    val String: TokenKind = TokenKind("string")
    val Val: TokenKind = TokenKind("val")
    val Modifier: TokenKind = TokenKind("modifier")
    val LeftParen: TokenKind = TokenKind("left_paren")
    val RightParen: TokenKind = TokenKind("right_paren")
    val LeftBrace: TokenKind = TokenKind("left_brace")
    val RightBrace: TokenKind = TokenKind("right_brace")
    val LeftBracket: TokenKind = TokenKind("left_bracket")
    val RightBracket: TokenKind = TokenKind("right_bracket")
    val Equals: TokenKind = TokenKind("equals")
    val Semicolon: TokenKind = TokenKind("semicolon")
    val Colon: TokenKind = TokenKind("colon")
    val Comma: TokenKind = TokenKind("comma")
    val Question: TokenKind = TokenKind("question")
    val Plus: TokenKind = TokenKind("plus")
    val Minus: TokenKind = TokenKind("minus")
    val Star: TokenKind = TokenKind("star")
    val Slash: TokenKind = TokenKind("slash")
    val Whitespace: TokenKind = TokenKind("whitespace")
    val Unknown: TokenKind = TokenKind("unknown")
    val EndOfFile: TokenKind = TokenKind("eof")
}

/**
 * 一个已词法化 token，包含类型、原始词素和源码 span。
 * A lexed token containing kind, original lexeme, and source span.
 */
data class Token(
    val kind: TokenKind,
    val lexeme: String,
    val span: SourceSpan,
)

/**
 * 单条 lexer rule 在某个 offset 的匹配结果。
 * Match result produced by one lexer rule at a given offset.
 */
data class TokenMatch(
    val kind: TokenKind,
    val length: Int,
) {
    init {
        require(length > 0) { "token match length must be greater than zero" }
    }
}
