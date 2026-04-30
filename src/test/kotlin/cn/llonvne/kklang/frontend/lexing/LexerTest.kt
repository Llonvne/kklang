package cn.llonvne.kklang.frontend.lexing

import cn.llonvne.kklang.frontend.SourceSpan
import cn.llonvne.kklang.frontend.SourceText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LexerTest {
    @Test
    fun `default lexer tokenizes seed surface and omits trivia`() {
        val source = SourceText.of("sample.kk", "alpha + 42\n(beta) _A0")
        val result = Lexer().tokenize(source)

        assertFalse(result.hasErrors)
        assertEquals(
            listOf(
                TokenKinds.Identifier,
                TokenKinds.Plus,
                TokenKinds.Integer,
                TokenKinds.LeftParen,
                TokenKinds.Identifier,
                TokenKinds.RightParen,
                TokenKinds.Identifier,
                TokenKinds.EndOfFile,
            ),
            result.tokens.map { it.kind },
        )
        assertEquals(listOf("alpha", "+", "42", "(", "beta", ")", "_A0", ""), result.tokens.map { it.lexeme })
        assertEquals(SourceSpan("sample.kk", 0, 5), result.tokens.first().span)
        assertEquals(SourceSpan("sample.kk", 21, 21), result.tokens.last().span)
        assertEquals("identifier", TokenKinds.Identifier.toString())
    }

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

    @Test
    fun `identifier lexer covers ascii start and continuation branches`() {
        val source = SourceText.of("sample.kk", "_ A Z a z A0 z9 a@ [ ` {")
        val result = Lexer().tokenize(source)

        assertTrue(result.hasErrors)
        assertEquals(
            listOf("_", "A", "Z", "a", "z", "A0", "z9", "a", "@", "[", "`", "{", ""),
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
                TokenKinds.Unknown,
                TokenKinds.Unknown,
                TokenKinds.Unknown,
                TokenKinds.Unknown,
                TokenKinds.EndOfFile,
            ),
            result.tokens.map { it.kind },
        )
    }

    @Test
    fun `unknown characters are tokens and diagnostics`() {
        val source = SourceText.of("sample.kk", "a @")
        val result = Lexer().tokenize(source)

        assertTrue(result.hasErrors)
        assertEquals(TokenKinds.Unknown, result.tokens[1].kind)
        assertEquals("@", result.tokens[1].lexeme)
        assertEquals("LEX001", result.diagnostics.single().code)
    }

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
