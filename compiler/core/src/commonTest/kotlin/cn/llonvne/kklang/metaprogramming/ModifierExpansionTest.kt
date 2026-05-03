package cn.llonvne.kklang.metaprogramming

import cn.llonvne.kklang.frontend.SourceText
import cn.llonvne.kklang.frontend.SourceSpan
import cn.llonvne.kklang.frontend.lexing.Lexer
import cn.llonvne.kklang.frontend.lexing.Token
import cn.llonvne.kklang.frontend.lexing.TokenKinds
import cn.llonvne.kklang.frontend.parsing.AstProgram
import cn.llonvne.kklang.frontend.parsing.FunctionDeclaration
import cn.llonvne.kklang.frontend.parsing.IntegerExpression
import cn.llonvne.kklang.frontend.parsing.Parser
import cn.llonvne.kklang.frontend.parsing.RawModifierApplication
import cn.llonvne.kklang.frontend.parsing.ValDeclaration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 覆盖第一版 modifier expansion 和 `fn` modifier。
 * Covers the first modifier expansion pass and the `fn` modifier.
 */
class ModifierExpansionTest {
    /**
     * 验证 canonical `fn` modifier application expansion 为结构化函数声明。
     * Verifies that the canonical `fn` modifier application expands into a structured function declaration.
     */
    @Test
    fun `modifier expansion turns fn application into function declaration`() {
        val result = expand("modifier fn { [modifiers] fn [identifier]([identifier:type?]) { [body] } } fn add(a: Int, b: Int) { val c = a + b; c } add(1, 2)")

        assertFalse(result.hasErrors)
        val function = assertIs<FunctionDeclaration>(requireNotNull(result.program).declarations.single())
        assertEquals("add", function.name)
        assertEquals(listOf("a:Int", "b:Int"), function.parameters.map { "${it.name}:${it.typeName}" })
        assertEquals(listOf("c"), function.body.declarations.map { it.name })
        assertEquals("fn", function.modifierName)
    }

    /**
     * 验证未定义的 raw modifier application 会产生 MOD001。
     * Verifies that an undefined raw modifier application produces MOD001.
     */
    @Test
    fun `modifier expansion rejects unknown raw modifier application`() {
        val result = expand("decorated thing { 1 } 0")

        assertTrue(result.hasErrors)
        assertEquals(listOf("MOD001"), result.diagnostics.map { it.code })
    }

    /**
     * 验证重复 modifier declaration 会产生 MOD002。
     * Verifies that duplicate modifier declarations produce MOD002.
     */
    @Test
    fun `modifier expansion rejects duplicate modifier declaration`() {
        val text = "modifier fn { [modifiers] fn [identifier]([identifier:type?]) { [body] } } " +
            "modifier fn { [modifiers] fn [identifier]([identifier:type?]) { [body] } } 0"
        val result = expand(text)

        assertTrue(result.hasErrors)
        assertEquals(listOf("MOD002"), result.diagnostics.map { it.code })
    }

    /**
     * 验证 malformed `fn` application 会产生 MOD003。
     * Verifies that malformed `fn` applications produce MOD003.
     */
    @Test
    fun `modifier expansion rejects malformed fn application`() {
        val result = expand("fn (a: Int) { a } 0")

        assertTrue(result.hasErrors)
        assertEquals(listOf("MOD003"), result.diagnostics.map { it.code })
    }

    /**
     * 验证 modifier declaration 名称和 pattern 必须匹配第一版 `fn` 定义。
     * Verifies that modifier declaration names and patterns must match the first `fn` definition.
     */
    @Test
    fun `modifier expansion rejects unknown modifier declarations and pattern mismatches`() {
        val unknown = expand("modifier other { [modifiers] fn [identifier]([identifier:type?]) { [body] } } 0")
        val mismatch = expand("modifier fn { [bad] } 0")

        assertTrue(unknown.hasErrors)
        assertEquals(listOf("MOD001"), unknown.diagnostics.map { it.code })
        assertTrue(mismatch.hasErrors)
        assertEquals(listOf("MOD003"), mismatch.diagnostics.map { it.code })
    }

    /**
     * 验证 expansion 会保留已经结构化的函数声明并继续展开同一 program。
     * Verifies that expansion preserves already structured function declarations while expanding the same program.
     */
    @Test
    fun `modifier expansion preserves structured function declarations`() {
        val firstExpansion = expand("fn one(a: Int) { a } one(1)")
        val firstProgram = requireNotNull(firstExpansion.program)
        val function = assertIs<FunctionDeclaration>(firstProgram.declarations.single())
        val secondExpansion = SeedModifierExpander().expand(
            AstProgram(expression = firstProgram.expression, declarations = listOf(function)),
        )

        assertFalse(secondExpansion.hasErrors)
        assertEquals(listOf(function), requireNotNull(secondExpansion.program).declarations)
    }

    /**
     * 验证 `fn` application 的所有必需结构缺失时都会产生 MOD003。
     * Verifies that every missing required structure in a `fn` application produces MOD003.
     */
    @Test
    fun `modifier expansion rejects malformed fn application structure`() {
        assertMalformedApplication("fn id { 1 } 0")
        assertMalformedApplication("fn id(: Int) { 1 } id(1)")
        assertMalformedApplication("fn id(a:) { a } id(1)")
        assertMalformedApplication("fn broken() { 1 + } broken()", "PARSE001")
        assertMalformedApplication("fn outer() { fn inner() { 1 } inner() } outer()")
    }

    /**
     * 验证手工构造的 raw application 也会受到同一结构校验。
     * Verifies that manually built raw applications receive the same structure validation.
     */
    @Test
    fun `modifier expansion validates raw application token invariants`() {
        assertFailsWith<IllegalArgumentException> {
            RawModifierApplication(nameToken = token(TokenKinds.Identifier, "fn", 0), tokens = emptyList())
        }
        assertMalformedRawApplication(listOf(token(TokenKinds.Modifier, "modifier", 0)))
        assertMalformedRawApplication(listOf(token(TokenKinds.Identifier, "fn", 0)))
        assertMalformedRawApplication(listOf(token(TokenKinds.Identifier, "wrong", 0)))
        assertMalformedRawApplication(
            listOf(
                token(TokenKinds.Identifier, "fn", 0),
                token(TokenKinds.Identifier, "id", 3),
                token(TokenKinds.LeftParen, "(", 5),
                token(TokenKinds.Identifier, "a", 6),
                token(TokenKinds.Colon, ":", 7),
                token(TokenKinds.Identifier, "Int", 9),
                token(TokenKinds.LeftBrace, "{", 13),
                token(TokenKinds.Integer, "1", 15),
                token(TokenKinds.RightBrace, "}", 17),
            ),
        )
        assertMalformedRawApplication(
            listOf(
                token(TokenKinds.Identifier, "fn", 0),
                token(TokenKinds.Identifier, "id", 3),
                token(TokenKinds.LeftParen, "(", 5),
                token(TokenKinds.RightParen, ")", 6),
                token(TokenKinds.Integer, "1", 8),
            ),
        )
        assertMalformedRawApplication(
            listOf(
                token(TokenKinds.Identifier, "fn", 0),
                token(TokenKinds.Identifier, "id", 3),
                token(TokenKinds.LeftParen, "(", 5),
                token(TokenKinds.RightParen, ")", 6),
                token(TokenKinds.LeftBrace, "{", 8),
                token(TokenKinds.Integer, "1", 10),
            ),
        )
        assertMalformedRawApplication(
            listOf(
                token(TokenKinds.Identifier, "fn", 0),
                token(TokenKinds.Identifier, "id", 3),
                token(TokenKinds.LeftParen, "(", 5),
                token(TokenKinds.RightParen, ")", 6),
                token(TokenKinds.LeftBrace, "{", 8),
                token(TokenKinds.Integer, "1", 10),
                token(TokenKinds.RightBrace, "}", 12),
                token(TokenKinds.Identifier, "extra", 14),
            ),
        )
    }

    /**
     * 使用默认 frontend 解析并执行 modifier expansion。
     * Parses with the default frontend and runs modifier expansion.
     */
    private fun expand(text: String): ModifierExpansionResult {
        val source = SourceText.of("sample.kk", text)
        val lexResult = Lexer().tokenize(source)
        val parseResult = Parser(lexResult.tokens).parseProgramDocument()
        return if (lexResult.hasErrors || parseResult.hasErrors) {
            ModifierExpansionResult(program = parseResult.program, diagnostics = lexResult.diagnostics + parseResult.diagnostics)
        } else {
            SeedModifierExpander().expand(parseResult.program)
        }
    }

    /**
     * 断言源码形式的 malformed application 产生指定 diagnostic。
     * Asserts that a source-form malformed application produces the specified diagnostic.
     */
    private fun assertMalformedApplication(text: String, code: String = "MOD003") {
        val result = expand(text)

        assertTrue(result.hasErrors)
        assertTrue(result.diagnostics.map { it.code }.contains(code))
    }

    /**
     * 断言手工 raw application token 序列被拒绝。
     * Asserts that a manually built raw application token sequence is rejected.
     */
    private fun assertMalformedRawApplication(tokens: List<Token>) {
        val raw = RawModifierApplication(nameToken = token(TokenKinds.Identifier, "fn", 0), tokens = tokens)
        val result = SeedModifierExpander().expand(AstProgram(expression = IntegerExpression(token(TokenKinds.Integer, "0", 20)), declarations = listOf(raw)))

        assertTrue(result.hasErrors)
        assertEquals(listOf("MOD003"), result.diagnostics.map { it.code })
    }

    /**
     * 创建测试用 token。
     * Creates a token for tests.
     */
    private fun token(kind: cn.llonvne.kklang.frontend.lexing.TokenKind, lexeme: String, start: Int): Token =
        Token(kind = kind, lexeme = lexeme, span = SourceSpan("sample.kk", start, start + lexeme.length))
}
