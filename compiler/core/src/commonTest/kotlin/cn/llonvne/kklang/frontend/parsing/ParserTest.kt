package cn.llonvne.kklang.frontend.parsing

import cn.llonvne.kklang.frontend.SourceText
import cn.llonvne.kklang.frontend.lexing.Lexer
import cn.llonvne.kklang.frontend.lexing.LexerConfig
import cn.llonvne.kklang.frontend.lexing.LexerRule
import cn.llonvne.kklang.frontend.lexing.TokenKind
import cn.llonvne.kklang.frontend.lexing.TokenKinds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ParserTest {
    @Test
    fun `parser handles identifiers integers grouping prefix and precedence`() {
        val result = parse("-(alpha + 2) * +3")

        assertFalse(result.hasErrors)
        assertEquals("(* (- (group (+ id(alpha) int(2)))) (+ int(3)))", result.expression.render())
    }

    @Test
    fun `default binary operators are left associative`() {
        val result = parse("10 - 3 - 2")

        assertFalse(result.hasErrors)
        assertEquals("(- (- int(10) int(3)) int(2))", result.expression.render())
    }

    @Test
    fun `parser reports missing expression prefixes and trailing tokens`() {
        val result = parse("* 1")

        assertTrue(result.hasErrors)
        assertEquals("<missing>", result.expression.render())
        assertEquals(listOf("PARSE001", "PARSE002"), result.diagnostics.map { it.code })
    }

    @Test
    fun `parser reports missing right paren`() {
        val result = parse("(1 + 2")

        assertTrue(result.hasErrors)
        assertEquals("(group (+ int(1) int(2)))", result.expression.render())
        assertEquals(listOf("PARSE003"), result.diagnostics.map { it.code })
    }

    @Test
    fun `parser reports missing right operand`() {
        val result = parse("1 +")

        assertTrue(result.hasErrors)
        assertEquals("(+ int(1) <missing>)", result.expression.render())
        assertEquals(listOf("PARSE001"), result.diagnostics.map { it.code })
    }

    @Test
    fun `parser configuration can add right associative operators`() {
        val caret = TokenKind("caret")
        val lexerConfig = LexerConfig.default().withRule(LexerRule.literal("caret", caret, "^"))
        val parserConfig = ParserConfig.default().withInfix(
            caret,
            InfixParselet.binary(precedence = 30, associativity = Associativity.Right),
        )

        val result = parse("a ^ b ^ c", lexerConfig, parserConfig)

        assertFalse(result.hasErrors)
        assertEquals("(^ id(a) (^ id(b) id(c)))", result.expression.render())
    }

    @Test
    fun `parser configuration can add prefix operators`() {
        val at = TokenKind("at")
        val lexerConfig = LexerConfig.default().withRule(LexerRule.literal("at", at, "@"))
        val parserConfig = ParserConfig.default().withPrefix(at) { parser, token ->
            PrefixExpression(operator = token, operand = parser.parseExpression(ParserConfig.PREFIX_PRECEDENCE))
        }

        val result = parse("@name", lexerConfig, parserConfig)

        assertFalse(result.hasErrors)
        assertEquals("(@ id(name))", result.expression.render())
    }

    @Test
    fun `parser validates token stream and parselet configuration`() {
        assertFailsWith<IllegalArgumentException> { Parser(emptyList()) }
        assertFailsWith<IllegalArgumentException> {
            val tokens = Lexer().tokenize(SourceText.of("sample.kk", "1")).tokens.dropLast(1)
            Parser(tokens)
        }
        assertFailsWith<IllegalArgumentException> {
            InfixParselet.binary(precedence = 0, associativity = Associativity.Left)
        }
        assertFailsWith<IllegalArgumentException> {
            ParserConfig.builder().build()
        }
    }

    private fun parse(
        text: String,
        lexerConfig: LexerConfig = LexerConfig.default(),
        parserConfig: ParserConfig = ParserConfig.default(),
    ): ParseResult {
        val source = SourceText.of("sample.kk", text)
        val lexResult = Lexer(lexerConfig).tokenize(source)
        val parseResult = Parser(lexResult.tokens, parserConfig).parseExpressionDocument()
        return parseResult.copy(diagnostics = lexResult.diagnostics + parseResult.diagnostics)
    }
}

private fun Expression.render(): String =
    when (this) {
        is IdentifierExpression -> "id($name)"
        is IntegerExpression -> "int($digits)"
        is PrefixExpression -> "(${operator.lexeme} ${operand.render()})"
        is BinaryExpression -> "(${operator.lexeme} ${left.render()} ${right.render()})"
        is GroupedExpression -> "(group ${expression.render()})"
        is MissingExpression -> "<missing>"
    }
