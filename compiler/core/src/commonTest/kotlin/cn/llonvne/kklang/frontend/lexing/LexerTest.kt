package cn.llonvne.kklang.frontend.lexing

import cn.llonvne.kklang.frontend.SourceSpan
import cn.llonvne.kklang.frontend.SourceText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 覆盖默认 lexer、扩展规则和错误诊断。
 * Covers the default lexer, extension rules, and error diagnostics.
 */
class LexerTest {
    /**
     * 验证默认 lexer 识别 seed token 并默认省略 trivia。
     * Verifies that the default lexer recognizes seed tokens and omits trivia by default.
     */
    @Test
    fun `default lexer tokenizes seed surface and omits trivia`() {
        val source = SourceText.of("sample.kk", "val alpha = 42;\n(beta) _A0 \"hi\"")
        val result = Lexer().tokenize(source)

        assertFalse(result.hasErrors)
        assertEquals(
            listOf(
                TokenKinds.Val,
                TokenKinds.Identifier,
                TokenKinds.Equals,
                TokenKinds.Integer,
                TokenKinds.Semicolon,
                TokenKinds.LeftParen,
                TokenKinds.Identifier,
                TokenKinds.RightParen,
                TokenKinds.Identifier,
                TokenKinds.String,
                TokenKinds.EndOfFile,
            ),
            result.tokens.map { it.kind },
        )
        assertEquals(listOf("val", "alpha", "=", "42", ";", "(", "beta", ")", "_A0", "\"hi\"", ""), result.tokens.map { it.lexeme })
        assertEquals(SourceSpan("sample.kk", 0, 3), result.tokens.first().span)
        assertEquals(SourceSpan("sample.kk", 31, 31), result.tokens.last().span)
        assertEquals("identifier", TokenKinds.Identifier.toString())
    }

    /**
     * 验证字符串字面量必须完整闭合，且第一版不接受换行和转义。
     * Verifies that string literals must be closed, and the first version does not accept newlines or escapes.
     */
    @Test
    fun `string lexer requires closed single line literals`() {
        val closed = Lexer().tokenize(SourceText.of("sample.kk", "\"hello\""))
        val unterminated = Lexer().tokenize(SourceText.of("sample.kk", "\"hello"))
        val multiline = Lexer().tokenize(SourceText.of("sample.kk", "\"a\nb\""))

        assertFalse(closed.hasErrors)
        assertEquals(listOf(TokenKinds.String, TokenKinds.EndOfFile), closed.tokens.map { it.kind })
        assertEquals(listOf(TokenKinds.Unknown, TokenKinds.Identifier, TokenKinds.EndOfFile), unterminated.tokens.map { it.kind })
        assertEquals(listOf(TokenKinds.Unknown, TokenKinds.Identifier, TokenKinds.Identifier, TokenKinds.Unknown, TokenKinds.EndOfFile), multiline.tokens.map { it.kind })
    }

    /**
     * 验证 val 关键字只匹配完整关键字，较长 identifier 仍保持 identifier。
     * Verifies that the val keyword matches only the full keyword while longer names remain identifiers.
     */
    @Test
    fun `val keyword does not consume longer identifiers`() {
        val result = Lexer().tokenize(SourceText.of("sample.kk", "val value"))

        assertFalse(result.hasErrors)
        assertEquals(
            listOf(TokenKinds.Val, TokenKinds.Identifier, TokenKinds.EndOfFile),
            result.tokens.map { it.kind },
        )
        assertEquals(listOf("val", "value", ""), result.tokens.map { it.lexeme })
    }

    /**
     * 验证工具模式可以发出 whitespace trivia。
     * Verifies that tooling mode can emit whitespace trivia.
     */
    @Test
    fun `lexer can emit trivia for tools`() {
        val source = SourceText.of("sample.kk", "a \n b")
        val result = Lexer(LexerConfig.default().withTrivia()).tokenize(source)

        assertFalse(result.hasErrors)
        assertEquals(
            listOf(
                TokenKinds.Identifier,
                TokenKinds.Whitespace,
                TokenKinds.Identifier,
                TokenKinds.EndOfFile,
            ),
            result.tokens.map { it.kind },
        )
        assertEquals(" \n ", result.tokens[1].lexeme)
    }

    /**
     * 验证 identifier 起始/后续字符的 ASCII 分支覆盖。
     * Verifies ASCII branch coverage for identifier start and continuation characters.
     */
    @Test
    fun `identifier lexer covers ascii start and continuation branches`() {
        val source = SourceText.of("sample.kk", "_ A Z a z A0 z9 a_ aA aZ aa az a0 a9 a@ [ ` {")
        val result = Lexer().tokenize(source)

        assertTrue(result.hasErrors)
        assertEquals(
            listOf("_", "A", "Z", "a", "z", "A0", "z9", "a_", "aA", "aZ", "aa", "az", "a0", "a9", "a", "@", "[", "`", "{", ""),
            result.tokens.map { it.lexeme },
        )
        assertEquals(
            listOf(
                TokenKinds.Identifier,
                TokenKinds.Identifier,
                TokenKinds.Identifier,
                TokenKinds.Identifier,
                TokenKinds.Identifier,
                TokenKinds.Identifier,
                TokenKinds.Identifier,
                TokenKinds.Identifier,
                TokenKinds.Identifier,
                TokenKinds.Identifier,
                TokenKinds.Identifier,
                TokenKinds.Identifier,
                TokenKinds.Identifier,
                TokenKinds.Identifier,
                TokenKinds.Identifier,
                TokenKinds.Unknown,
                TokenKinds.LeftBracket,
                TokenKinds.Unknown,
                TokenKinds.LeftBrace,
                TokenKinds.EndOfFile,
            ),
            result.tokens.map { it.kind },
        )
    }

    /**
     * 验证 modifier 和函数语法新增符号会被默认 lexer 识别。
     * Verifies that modifier and function-syntax punctuation are recognized by the default lexer.
     */
    @Test
    fun `default lexer tokenizes modifier function punctuation`() {
        val result = Lexer().tokenize(SourceText.of("sample.kk", "modifier fn { [identifier:type?], () }"))

        assertFalse(result.hasErrors)
        assertEquals(
            listOf(
                TokenKinds.Modifier,
                TokenKinds.Identifier,
                TokenKinds.LeftBrace,
                TokenKinds.LeftBracket,
                TokenKinds.Identifier,
                TokenKinds.Colon,
                TokenKinds.Identifier,
                TokenKinds.Question,
                TokenKinds.RightBracket,
                TokenKinds.Comma,
                TokenKinds.LeftParen,
                TokenKinds.RightParen,
                TokenKinds.RightBrace,
                TokenKinds.EndOfFile,
            ),
            result.tokens.map { it.kind },
        )
    }

    /**
     * 验证未知字符同时产生 unknown token 和 LEX001。
     * Verifies that unknown characters produce both unknown tokens and LEX001.
     */
    @Test
    fun `unknown characters are tokens and diagnostics`() {
        val source = SourceText.of("sample.kk", "a @")
        val result = Lexer().tokenize(source)

        assertTrue(result.hasErrors)
        assertEquals(TokenKinds.Unknown, result.tokens[1].kind)
        assertEquals("@", result.tokens[1].lexeme)
        assertEquals("LEX001", result.diagnostics.single().code)
    }

    /**
     * 验证自定义 lexer rule 可扩展且最长匹配胜出。
     * Verifies that custom lexer rules are extensible and longest match wins.
     */
    @Test
    fun `custom lexer rules are extensible and longest match wins`() {
        val arrow = TokenKind("arrow")
        val dash = TokenKind("dash")
        val config = LexerConfig.default()
            .withRule(LexerRule.literal("dash", dash, "-"))
            .withRule(LexerRule.literal("arrow", arrow, "->"))

        val result = Lexer(config).tokenize(SourceText.of("sample.kk", "a -> b"))

        assertFalse(result.hasErrors)
        assertEquals(listOf(TokenKinds.Identifier, arrow, TokenKinds.Identifier, TokenKinds.EndOfFile), result.tokens.map { it.kind })
    }

    /**
     * 验证长度相同时最早注册的 lexer rule 胜出。
     * Verifies that the earliest registered lexer rule wins ties.
     */
    @Test
    fun `earliest registered lexer rule wins ties`() {
        val first = TokenKind("first")
        val second = TokenKind("second")
        val config = LexerConfig.builder()
            .rule(LexerRule.literal("first", first, "?"))
            .rule(LexerRule.literal("second", second, "?"))
            .build()

        val result = Lexer(config).tokenize(SourceText.of("sample.kk", "?"))

        assertEquals(listOf(first, TokenKinds.EndOfFile), result.tokens.map { it.kind })
    }

    /**
     * 验证 lexer 扩展点拒绝非法配置和非法 offset。
     * Verifies that lexer extension points reject invalid configuration and invalid offsets.
     */
    @Test
    fun `lexer validates extension points`() {
        assertFailsWith<IllegalArgumentException> { TokenKind("") }
        assertFailsWith<IllegalArgumentException> { LexerRule.literal("bad", TokenKind("bad"), "") }
        assertFailsWith<IllegalArgumentException> { LexerRule.literal("", TokenKind("bad"), "x") }
        assertFailsWith<IllegalArgumentException> { TokenMatch(TokenKinds.Identifier, 0) }
        assertFailsWith<IllegalArgumentException> { LexerConfig.builder().build() }

        val source = SourceText.of("sample.kk", "x")
        val rule = LexerRule.literal("x", TokenKind("x"), "x")
        assertFailsWith<IllegalArgumentException> { rule.match(source, -1) }
        assertFailsWith<IllegalArgumentException> { rule.match(source, 1) }
    }
}
