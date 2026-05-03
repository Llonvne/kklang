package cn.llonvne.kklang.idea

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType

/**
 * IDEA syntax highlighter for `.kk` files.
 * `.kk` 文件的 IDEA syntax highlighter。
 */
class KkSyntaxHighlighter : SyntaxHighlighterBase() {
    /**
     * 返回复用共享分类器的 IDEA lexer。
     * Returns the IDEA lexer that reuses the shared classifier.
     */
    override fun getHighlightingLexer(): Lexer = KkIdeaLexer()

    /**
     * 将 IDEA token type 映射为编辑器颜色属性。
     * Maps IDEA token types to editor color attributes.
     */
    override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> =
        when (tokenType) {
            KkTokenTypes.KEYWORD -> pack(KEYWORD)
            KkTokenTypes.IDENTIFIER -> pack(IDENTIFIER)
            KkTokenTypes.INTEGER -> pack(INTEGER)
            KkTokenTypes.STRING -> pack(STRING)
            KkTokenTypes.OPERATOR -> pack(OPERATOR)
            KkTokenTypes.DELIMITER -> pack(DELIMITER)
            KkTokenTypes.UNKNOWN -> pack(UNKNOWN)
            else -> emptyArray()
        }

    /**
     * 高亮属性 key。
     * Highlighting attribute keys.
     */
    companion object {
        val KEYWORD: TextAttributesKey =
            TextAttributesKey.createTextAttributesKey("KK_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)
        val IDENTIFIER: TextAttributesKey =
            TextAttributesKey.createTextAttributesKey("KK_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER)
        val INTEGER: TextAttributesKey =
            TextAttributesKey.createTextAttributesKey("KK_INTEGER", DefaultLanguageHighlighterColors.NUMBER)
        val STRING: TextAttributesKey =
            TextAttributesKey.createTextAttributesKey("KK_STRING", DefaultLanguageHighlighterColors.STRING)
        val OPERATOR: TextAttributesKey =
            TextAttributesKey.createTextAttributesKey("KK_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN)
        val DELIMITER: TextAttributesKey =
            TextAttributesKey.createTextAttributesKey("KK_DELIMITER", DefaultLanguageHighlighterColors.PARENTHESES)
        val UNKNOWN: TextAttributesKey =
            TextAttributesKey.createTextAttributesKey("KK_UNKNOWN", HighlighterColors.BAD_CHARACTER)
    }
}
