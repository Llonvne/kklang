package cn.llonvne.kklang.frontend.parsing

import cn.llonvne.kklang.frontend.SourceSpan
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

/**
 * 覆盖默认 Pratt parser 行为和 parser 扩展点。
 * Covers default Pratt parser behavior and parser extension points.
 */
class ParserTest {
    /**
     * 验证默认 parser 支持 identifier、integer、grouping、prefix 和优先级。
     * Verifies that the default parser supports identifiers, integers, grouping, prefix operators, and precedence.
     */
    @Test
    fun `parser handles identifiers integers grouping prefix and precedence`() {
        val result = parse("-(alpha + 2) * +3")

        assertFalse(result.hasErrors)
        assertEquals("(* (- (group (+ id(alpha) int(2)))) (+ int(3)))", result.expression.render())
    }

    /**
     * 验证 AstProgram 会暴露根 expression 的 span。
     * Verifies that AstProgram exposes the root expression span.
     */
    @Test
    fun `ast program exposes root expression span`() {
        val program = AstProgram(parse("1").expression)

        assertEquals(SourceSpan("sample.kk", 0, 1), program.span)
    }

    /**
     * 验证默认二元运算符是左结合。
     * Verifies that default binary operators are left-associative.
     */
    @Test
    fun `default binary operators are left associative`() {
        val result = parse("10 - 3 - 2")

        assertFalse(result.hasErrors)
        assertEquals("(- (- int(10) int(3)) int(2))", result.expression.render())
    }

    /**
     * 验证缺失 prefix expression 和多余 token 的 diagnostics。
     * Verifies diagnostics for a missing prefix expression and trailing tokens.
     */
    @Test
    fun `parser reports missing expression prefixes and trailing tokens`() {
        val result = parse("* 1")

        assertTrue(result.hasErrors)
        assertEquals("<missing>", result.expression.render())
        assertEquals(listOf("PARSE001", "PARSE002"), result.diagnostics.map { it.code })
    }

    /**
     * 验证缺失右括号会产生 PARSE003。
     * Verifies that a missing right parenthesis produces PARSE003.
     */
    @Test
    fun `parser reports missing right paren`() {
        val result = parse("(1 + 2")

        assertTrue(result.hasErrors)
        assertEquals("(group (+ int(1) int(2)))", result.expression.render())
        assertEquals(listOf("PARSE003"), result.diagnostics.map { it.code })
    }

    /**
     * 验证缺失右 operand 会产生 missing expression。
     * Verifies that a missing right operand produces a missing expression.
     */
    @Test
    fun `parser reports missing right operand`() {
        val result = parse("1 +")

        assertTrue(result.hasErrors)
        assertEquals("(+ int(1) <missing>)", result.expression.render())
        assertEquals(listOf("PARSE001"), result.diagnostics.map { it.code })
    }

    /**
     * 验证配置可以新增右结合 infix operator。
     * Verifies that configuration can add a right-associative infix operator.
     */
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

    /**
     * 验证配置可以新增 prefix operator。
     * Verifies that configuration can add a prefix operator.
     */
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

    /**
     * 验证 parser 构造、infix parselet 和配置 builder 的非法输入。
     * Verifies invalid inputs for parser construction, infix parselets, and the configuration builder.
     */
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

    /**
     * 使用指定 lexer/parser 配置解析测试源码。
     * Parses test source with the provided lexer and parser configuration.
     */
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

/**
 * 将 AST expression 渲染为稳定的测试断言字符串。
 * Renders an AST expression into a stable assertion string for tests.
 */
private fun Expression.render(): String =
    when (this) {
        is IdentifierExpression -> "id($name)"
        is IntegerExpression -> "int($digits)"
        is PrefixExpression -> "(${operator.lexeme} ${operand.render()})"
        is BinaryExpression -> "(${operator.lexeme} ${left.render()} ${right.render()})"
        is GroupedExpression -> "(group ${expression.render()})"
        is MissingExpression -> "<missing>"
    }
