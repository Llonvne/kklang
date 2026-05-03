package cn.llonvne.kklang.tooling.highlighting

import cn.llonvne.kklang.frontend.SourceText
import cn.llonvne.kklang.frontend.lexing.Lexer
import cn.llonvne.kklang.frontend.lexing.LexerConfig
import cn.llonvne.kklang.frontend.lexing.Token
import cn.llonvne.kklang.frontend.lexing.TokenKind
import cn.llonvne.kklang.frontend.lexing.TokenKinds

/**
 * `.kk` 工具链共享的语法高亮分类。
 * Shared syntax-highlighting categories for `.kk` tooling.
 */
enum class KkHighlightTokenCategory {
    Keyword,
    Identifier,
    Integer,
    String,
    Operator,
    Delimiter,
    Whitespace,
    Unknown,
    EndOfFile,
}

/**
 * 一个供编辑器工具使用的已分类 token。
 * One classified token used by editor tooling.
 */
data class KkHighlightToken(
    val category: KkHighlightTokenCategory,
    val lexeme: String,
    val startOffset: Int,
    val endOffset: Int,
) {
    init {
        require(startOffset >= 0) { "startOffset must be zero or greater" }
        require(endOffset >= startOffset) { "endOffset must be greater than or equal to startOffset" }
    }
}

/**
 * 基于编译器 lexer 的 `.kk` 语法分类器，供 LSP 和 IDEA 插件共享。
 * `.kk` syntax classifier backed by the compiler lexer and shared by the LSP and IDEA plugin.
 */
class KkSyntaxClassifier(
    private val lexerConfig: LexerConfig = LexerConfig.default(),
) {
    /**
     * 将源码分类为工具可消费的 token；默认省略 whitespace trivia 和 EOF。
     * Classifies source into tooling tokens; whitespace trivia and EOF are omitted by default.
     */
    fun classify(source: SourceText, includeTrivia: Boolean = false): List<KkHighlightToken> {
        val config = if (includeTrivia) lexerConfig.withTrivia() else lexerConfig
        return Lexer(config)
            .tokenize(source)
            .tokens
            .map(::classifyToken)
            .filter { includeTrivia || (it.category != KkHighlightTokenCategory.Whitespace && it.category != KkHighlightTokenCategory.EndOfFile) }
    }

    /**
     * 将 compiler token 映射为共享高亮 token。
     * Maps a compiler token to a shared highlighting token.
     */
    private fun classifyToken(token: Token): KkHighlightToken =
        KkHighlightToken(
            category = categoryFor(token.kind),
            lexeme = token.lexeme,
            startOffset = token.span.startOffset,
            endOffset = token.span.endOffset,
        )

    /**
     * 将 token kind 映射为共享高亮分类。
     * Maps a token kind to a shared highlighting category.
     */
    private fun categoryFor(kind: TokenKind): KkHighlightTokenCategory =
        when (kind) {
            TokenKinds.Val,
            TokenKinds.Modifier,
            -> KkHighlightTokenCategory.Keyword
            TokenKinds.Identifier -> KkHighlightTokenCategory.Identifier
            TokenKinds.Integer -> KkHighlightTokenCategory.Integer
            TokenKinds.String -> KkHighlightTokenCategory.String
            TokenKinds.Plus,
            TokenKinds.Minus,
            TokenKinds.Star,
            TokenKinds.Slash,
            TokenKinds.Equals,
            -> KkHighlightTokenCategory.Operator
            TokenKinds.LeftParen,
            TokenKinds.RightParen,
            TokenKinds.LeftBrace,
            TokenKinds.RightBrace,
            TokenKinds.LeftBracket,
            TokenKinds.RightBracket,
            TokenKinds.Semicolon,
            TokenKinds.Colon,
            TokenKinds.Comma,
            TokenKinds.Question,
            -> KkHighlightTokenCategory.Delimiter
            TokenKinds.Whitespace -> KkHighlightTokenCategory.Whitespace
            TokenKinds.Unknown -> KkHighlightTokenCategory.Unknown
            TokenKinds.EndOfFile -> KkHighlightTokenCategory.EndOfFile
            else -> KkHighlightTokenCategory.Unknown
        }
}
