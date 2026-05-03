package cn.llonvne.kklang.idea

import cn.llonvne.kklang.frontend.SourceText
import cn.llonvne.kklang.tooling.highlighting.KkHighlightTokenCategory
import cn.llonvne.kklang.tooling.highlighting.KkSyntaxClassifier
import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType

/**
 * IDEA lexer adapter that converts shared `.kk` highlight tokens into IDEA token types.
 * 将共享 `.kk` 高亮 token 转换为 IDEA token type 的 IDEA lexer 适配器。
 */
class KkIdeaLexer(
    private val syntaxClassifier: KkSyntaxClassifier = KkSyntaxClassifier(),
) : LexerBase() {
    private var buffer: CharSequence = ""
    private var bufferEndOffset: Int = 0
    private var index: Int = 0
    private var tokens: List<IdeaToken> = emptyList()

    /**
     * 在 IDEA 指定的 buffer 区间上启动 lexer。
     * Starts the lexer over the buffer range provided by IDEA.
     */
    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        bufferEndOffset = endOffset
        index = 0
        val text = buffer.subSequence(startOffset, endOffset).toString()
        tokens = syntaxClassifier
            .classify(SourceText.of("idea-buffer.kk", text), includeTrivia = true)
            .filter { it.category != KkHighlightTokenCategory.EndOfFile }
            .map { token ->
                IdeaToken(
                    type = KkIdeaTokenTypeMapper.ideaTokenType(token.category),
                    startOffset = startOffset + token.startOffset,
                    endOffset = startOffset + token.endOffset,
                )
            }
    }

    /**
     * 第一版 lexer 无需额外状态。
     * The first lexer version does not need extra state.
     */
    override fun getState(): Int = 0

    /**
     * 返回当前 IDEA token type，结束时返回 null。
     * Returns the current IDEA token type, or null at the end.
     */
    override fun getTokenType(): IElementType? =
        tokens.getOrNull(index)?.type

    /**
     * 返回当前 token 的起始 offset。
     * Returns the current token start offset.
     */
    override fun getTokenStart(): Int =
        tokens.getOrNull(index)?.startOffset ?: bufferEndOffset

    /**
     * 返回当前 token 的结束 offset。
     * Returns the current token end offset.
     */
    override fun getTokenEnd(): Int =
        tokens.getOrNull(index)?.endOffset ?: bufferEndOffset

    /**
     * 前进到下一个 token。
     * Advances to the next token.
     */
    override fun advance() {
        index += 1
    }

    /**
     * 返回当前 buffer。
     * Returns the current buffer.
     */
    override fun getBufferSequence(): CharSequence = buffer

    /**
     * 返回当前 buffer 区间终点。
     * Returns the current buffer range end.
     */
    override fun getBufferEnd(): Int = bufferEndOffset

    /**
     * IDEA token type 与源码 offset 区间。
     * IDEA token type plus source offset range.
     */
    private data class IdeaToken(
        val type: IElementType,
        val startOffset: Int,
        val endOffset: Int,
    )
}

/**
 * 将共享高亮分类映射为 IDEA token type。
 * Maps shared highlight categories to IDEA token types.
 */
internal object KkIdeaTokenTypeMapper {
    /**
     * 将一个共享高亮分类映射为 IDEA token type。
     * Maps one shared highlight category to an IDEA token type.
     */
    fun ideaTokenType(category: KkHighlightTokenCategory): IElementType =
        when (category) {
            KkHighlightTokenCategory.Keyword -> KkTokenTypes.KEYWORD
            KkHighlightTokenCategory.Identifier -> KkTokenTypes.IDENTIFIER
            KkHighlightTokenCategory.Integer -> KkTokenTypes.INTEGER
            KkHighlightTokenCategory.String -> KkTokenTypes.STRING
            KkHighlightTokenCategory.Operator -> KkTokenTypes.OPERATOR
            KkHighlightTokenCategory.Delimiter -> KkTokenTypes.DELIMITER
            KkHighlightTokenCategory.Whitespace -> KkTokenTypes.WHITESPACE
            KkHighlightTokenCategory.Unknown -> KkTokenTypes.UNKNOWN
            KkHighlightTokenCategory.EndOfFile -> KkTokenTypes.UNKNOWN
        }
}
