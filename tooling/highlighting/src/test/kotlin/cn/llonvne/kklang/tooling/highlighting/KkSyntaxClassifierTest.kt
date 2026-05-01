package cn.llonvne.kklang.tooling.highlighting

import cn.llonvne.kklang.frontend.SourceText
import cn.llonvne.kklang.frontend.lexing.LexerConfig
import cn.llonvne.kklang.frontend.lexing.LexerRule
import cn.llonvne.kklang.frontend.lexing.TokenKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 覆盖共享 `.kk` 语法分类器。
 * Covers the shared `.kk` syntax classifier.
 */
class KkSyntaxClassifierTest {
    /**
     * 验证默认分类覆盖编辑器可见 token，并默认省略 trivia 和 EOF。
     * Verifies that default classification covers editor-visible tokens and omits trivia plus EOF by default.
     */
    @Test
    fun `classifier maps visible tokens and omits trivia by default`() {
        val tokens = KkSyntaxClassifier().classify(SourceText.of("sample.kk", "val x = (1 + 2); x"))

        assertEquals(
            listOf(
                KkHighlightTokenCategory.Keyword,
                KkHighlightTokenCategory.Identifier,
                KkHighlightTokenCategory.Operator,
                KkHighlightTokenCategory.Delimiter,
                KkHighlightTokenCategory.Integer,
                KkHighlightTokenCategory.Operator,
                KkHighlightTokenCategory.Integer,
                KkHighlightTokenCategory.Delimiter,
                KkHighlightTokenCategory.Delimiter,
                KkHighlightTokenCategory.Identifier,
            ),
            tokens.map { it.category },
        )
        assertEquals(listOf("val", "x", "=", "(", "1", "+", "2", ")", ";", "x"), tokens.map { it.lexeme })
        assertEquals(KkHighlightToken(KkHighlightTokenCategory.Keyword, "val", 0, 3), tokens.first())
    }

    /**
     * 验证工具请求 trivia 时会保留 whitespace 和 EOF。
     * Verifies that tooling trivia requests preserve whitespace and EOF.
     */
    @Test
    fun `classifier can include trivia and eof`() {
        val tokens = KkSyntaxClassifier().classify(SourceText.of("sample.kk", "x \n y"), includeTrivia = true)

        assertEquals(
            listOf(
                KkHighlightTokenCategory.Identifier,
                KkHighlightTokenCategory.Whitespace,
                KkHighlightTokenCategory.Identifier,
                KkHighlightTokenCategory.EndOfFile,
            ),
            tokens.map { it.category },
        )
        assertEquals(listOf("x", " \n ", "y", ""), tokens.map { it.lexeme })
    }

    /**
     * 验证未知字符保留 unknown 分类。
     * Verifies that unknown characters retain the unknown category.
     */
    @Test
    fun `classifier preserves unknown tokens`() {
        val tokens = KkSyntaxClassifier().classify(SourceText.of("sample.kk", "@"))

        assertEquals(listOf(KkHighlightTokenCategory.Unknown), tokens.map { it.category })
        assertEquals(KkHighlightToken(KkHighlightTokenCategory.Unknown, "@", 0, 1), tokens.single())
    }

    /**
     * 验证所有当前 operator token 都映射为 operator 分类。
     * Verifies that every current operator token maps to the operator category.
     */
    @Test
    fun `classifier maps every operator token`() {
        val tokens = KkSyntaxClassifier().classify(SourceText.of("sample.kk", "+ - * / ="))

        assertEquals(
            listOf(
                KkHighlightTokenCategory.Operator,
                KkHighlightTokenCategory.Operator,
                KkHighlightTokenCategory.Operator,
                KkHighlightTokenCategory.Operator,
                KkHighlightTokenCategory.Operator,
            ),
            tokens.map { it.category },
        )
    }

    /**
     * 验证自定义未知 token kind 会落到 unknown 分类。
     * Verifies that a custom unknown token kind falls back to the unknown category.
     */
    @Test
    fun `classifier maps custom token kinds to unknown`() {
        val question = TokenKind("question")
        val config = LexerConfig.default().withRule(LexerRule.literal("question", question, "?"))
        val tokens = KkSyntaxClassifier(config).classify(SourceText.of("sample.kk", "?"))

        assertEquals(listOf(KkHighlightTokenCategory.Unknown), tokens.map { it.category })
    }

    /**
     * 验证即使底层配置发出 trivia，默认分类仍会过滤 whitespace。
     * Verifies that default classification filters whitespace even when the underlying config emits trivia.
     */
    @Test
    fun `classifier filters whitespace from trivia emitting config by default`() {
        val tokens = KkSyntaxClassifier(LexerConfig.default().withTrivia()).classify(SourceText.of("sample.kk", "x y"))

        assertEquals(
            listOf(KkHighlightTokenCategory.Identifier, KkHighlightTokenCategory.Identifier),
            tokens.map { it.category },
        )
    }

    /**
     * 验证分类数据拒绝非法 offset 区间。
     * Verifies that classification data rejects invalid offset ranges.
     */
    @Test
    fun `highlight token validates offsets`() {
        assertFailsWith<IllegalArgumentException> {
            KkHighlightToken(KkHighlightTokenCategory.Identifier, "x", -1, 1)
        }
        assertFailsWith<IllegalArgumentException> {
            KkHighlightToken(KkHighlightTokenCategory.Identifier, "x", 2, 1)
        }
    }
}
