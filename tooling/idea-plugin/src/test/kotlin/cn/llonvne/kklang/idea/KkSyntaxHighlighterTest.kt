package cn.llonvne.kklang.idea

import com.intellij.psi.tree.IElementType
import cn.llonvne.kklang.tooling.highlighting.KkHighlightTokenCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 覆盖 IDEA syntax highlighter 与共享高亮分类的映射。
 * Covers the mapping between the IDEA syntax highlighter and shared highlighting classification.
 */
class KkSyntaxHighlighterTest {
    /**
     * 验证 IDEA lexer 复用共享分类并输出 IDEA token types。
     * Verifies that the IDEA lexer reuses shared classification and emits IDEA token types.
     */
    @Test
    fun `idea lexer emits token types from shared classification`() {
        val lexer = KkIdeaLexer()
        lexer.start("val x = 1;\n@", 0, 12, 0)

        val types = mutableListOf<IElementType>()
        while (lexer.tokenType != null) {
            types += lexer.tokenType!!
            lexer.advance()
        }

        assertEquals(
            listOf(
                KkTokenTypes.KEYWORD,
                KkTokenTypes.WHITESPACE,
                KkTokenTypes.IDENTIFIER,
                KkTokenTypes.WHITESPACE,
                KkTokenTypes.OPERATOR,
                KkTokenTypes.WHITESPACE,
                KkTokenTypes.INTEGER,
                KkTokenTypes.DELIMITER,
                KkTokenTypes.WHITESPACE,
                KkTokenTypes.UNKNOWN,
            ),
            types,
        )
        assertEquals(12, lexer.bufferEnd)
        assertEquals("val x = 1;\n@", lexer.bufferSequence.toString())
        assertEquals(0, lexer.state)
        assertEquals(12, lexer.tokenStart)
        assertEquals(12, lexer.tokenEnd)
    }

    /**
     * 验证 IDEA lexer 支持非零起点的 buffer 区间。
     * Verifies that the IDEA lexer supports buffer ranges with non-zero starts.
     */
    @Test
    fun `idea lexer offsets are relative to original buffer`() {
        val lexer = KkIdeaLexer()
        lexer.start("##val", 2, 5, 0)

        assertEquals(KkTokenTypes.KEYWORD, lexer.tokenType)
        assertEquals(2, lexer.tokenStart)
        assertEquals(5, lexer.tokenEnd)
    }

    /**
     * 验证 syntax highlighter 为每种 token type 返回属性。
     * Verifies that the syntax highlighter returns attributes for each token type.
     */
    @Test
    fun `syntax highlighter maps every token type`() {
        val highlighter = KkSyntaxHighlighter()

        assertTrue(highlighter.highlightingLexer is KkIdeaLexer)
        assertTrue(highlighter.getTokenHighlights(KkTokenTypes.KEYWORD).isNotEmpty())
        assertTrue(highlighter.getTokenHighlights(KkTokenTypes.IDENTIFIER).isNotEmpty())
        assertTrue(highlighter.getTokenHighlights(KkTokenTypes.INTEGER).isNotEmpty())
        assertTrue(highlighter.getTokenHighlights(KkTokenTypes.OPERATOR).isNotEmpty())
        assertTrue(highlighter.getTokenHighlights(KkTokenTypes.DELIMITER).isNotEmpty())
        assertTrue(highlighter.getTokenHighlights(KkTokenTypes.UNKNOWN).isNotEmpty())
        assertTrue(highlighter.getTokenHighlights(KkTokenTypes.WHITESPACE).isEmpty())
        assertTrue(highlighter.getTokenHighlights(null).isEmpty())
        assertTrue(highlighter.getTokenHighlights(IElementType("FOREIGN", KkLanguage)).isEmpty())
        assertSame(KkSyntaxHighlighter.KEYWORD, highlighter.getTokenHighlights(KkTokenTypes.KEYWORD).single())
        assertSame(KkSyntaxHighlighter.IDENTIFIER, highlighter.getTokenHighlights(KkTokenTypes.IDENTIFIER).single())
        assertSame(KkSyntaxHighlighter.INTEGER, highlighter.getTokenHighlights(KkTokenTypes.INTEGER).single())
        assertSame(KkSyntaxHighlighter.OPERATOR, highlighter.getTokenHighlights(KkTokenTypes.OPERATOR).single())
        assertSame(KkSyntaxHighlighter.DELIMITER, highlighter.getTokenHighlights(KkTokenTypes.DELIMITER).single())
        assertSame(KkSyntaxHighlighter.UNKNOWN, highlighter.getTokenHighlights(KkTokenTypes.UNKNOWN).single())
    }

    /**
     * 验证 IDEA token type mapper 覆盖所有共享分类。
     * Verifies that the IDEA token type mapper covers every shared category.
     */
    @Test
    fun `idea token type mapper covers every category`() {
        assertEquals(KkTokenTypes.KEYWORD, KkIdeaTokenTypeMapper.ideaTokenType(KkHighlightTokenCategory.Keyword))
        assertEquals(KkTokenTypes.IDENTIFIER, KkIdeaTokenTypeMapper.ideaTokenType(KkHighlightTokenCategory.Identifier))
        assertEquals(KkTokenTypes.INTEGER, KkIdeaTokenTypeMapper.ideaTokenType(KkHighlightTokenCategory.Integer))
        assertEquals(KkTokenTypes.OPERATOR, KkIdeaTokenTypeMapper.ideaTokenType(KkHighlightTokenCategory.Operator))
        assertEquals(KkTokenTypes.DELIMITER, KkIdeaTokenTypeMapper.ideaTokenType(KkHighlightTokenCategory.Delimiter))
        assertEquals(KkTokenTypes.WHITESPACE, KkIdeaTokenTypeMapper.ideaTokenType(KkHighlightTokenCategory.Whitespace))
        assertEquals(KkTokenTypes.UNKNOWN, KkIdeaTokenTypeMapper.ideaTokenType(KkHighlightTokenCategory.Unknown))
        assertEquals(KkTokenTypes.UNKNOWN, KkIdeaTokenTypeMapper.ideaTokenType(KkHighlightTokenCategory.EndOfFile))
    }

    /**
     * 验证 factory 返回 kklang syntax highlighter。
     * Verifies that the factory returns the kklang syntax highlighter.
     */
    @Test
    fun `factory returns syntax highlighter`() {
        assertTrue(KkSyntaxHighlighterFactory().getSyntaxHighlighter(null, null) is KkSyntaxHighlighter)
    }
}
