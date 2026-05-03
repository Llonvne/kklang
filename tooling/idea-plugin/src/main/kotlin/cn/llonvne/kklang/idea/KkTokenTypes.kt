package cn.llonvne.kklang.idea

import com.intellij.psi.tree.IElementType

/**
 * IDEA lexer 暴露给 syntax highlighter 的 token types。
 * IDEA token types exposed by the lexer to the syntax highlighter.
 */
object KkTokenTypes {
    val KEYWORD: IElementType = IElementType("KK_KEYWORD", KkLanguage)
    val IDENTIFIER: IElementType = IElementType("KK_IDENTIFIER", KkLanguage)
    val INTEGER: IElementType = IElementType("KK_INTEGER", KkLanguage)
    val STRING: IElementType = IElementType("KK_STRING", KkLanguage)
    val OPERATOR: IElementType = IElementType("KK_OPERATOR", KkLanguage)
    val DELIMITER: IElementType = IElementType("KK_DELIMITER", KkLanguage)
    val WHITESPACE: IElementType = IElementType("KK_WHITESPACE", KkLanguage)
    val UNKNOWN: IElementType = IElementType("KK_UNKNOWN", KkLanguage)
}
