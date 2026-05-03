package cn.llonvne.kklang.frontend.parsing

import cn.llonvne.kklang.frontend.SourceSpan
import cn.llonvne.kklang.frontend.SourceText
import cn.llonvne.kklang.frontend.lexing.Lexer
import cn.llonvne.kklang.frontend.lexing.LexerConfig
import cn.llonvne.kklang.frontend.lexing.LexerRule
import cn.llonvne.kklang.frontend.lexing.TokenKind
import cn.llonvne.kklang.frontend.lexing.TokenKinds
import cn.llonvne.kklang.metaprogramming.SeedModifierExpander
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
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
     * 验证 parser 把字符串 token 解析为字符串字面量表达式。
     * Verifies that the parser parses string tokens as string-literal expressions.
     */
    @Test
    fun `parser handles string literals`() {
        val result = parse("\"hello\"")

        assertFalse(result.hasErrors)
        assertEquals("string(hello)", result.expression.render())
    }

    /**
     * 验证 parser 支持内建调用表达式并保持调用优先级高于整数运算。
     * Verifies that the parser supports builtin call expressions and keeps calls above integer operators.
     */
    @Test
    fun `parser handles builtin call expressions`() {
        val result = parse("-print(1 + 2) * 3")

        assertFalse(result.hasErrors)
        assertEquals("(* (- call(print, (+ int(1) int(2)))) int(3))", result.expression.render())
    }

    /**
     * 验证 parser 支持空参数和多参数调用表达式。
     * Verifies that the parser supports empty-argument and multi-argument call expressions.
     */
    @Test
    fun `parser handles empty and multiple call arguments`() {
        val empty = parse("answer()")
        val multiple = parse("add(1, 2 + 3)")

        assertFalse(empty.hasErrors)
        assertFalse(multiple.hasErrors)
        assertEquals("call(answer)", empty.expression.render())
        assertEquals("call(add, int(1), (+ int(2) int(3)))", multiple.expression.render())
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
     * 验证包含 val declaration 的 AstProgram span 覆盖 declaration 到最终 expression。
     * Verifies that AstProgram with val declarations spans from declaration to final expression.
     */
    @Test
    fun `ast program with val declarations exposes covered span`() {
        val program = parseProgram("val x = 1; x").program

        assertEquals(SourceSpan("sample.kk", 0, 12), program.span)
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
     * 验证 program parser 支持 val declarations 和最终 expression。
     * Verifies that the program parser supports val declarations and a final expression.
     */
    @Test
    fun `program parser handles val declarations and final expression`() {
        val result = parseProgram("val x = 1; val y = x + 2; y * 3")

        assertFalse(result.hasErrors)
        assertEquals("program(val x = int(1); val y = (+ id(x) int(2)); (* id(y) int(3)))", result.program.render())
    }

    /**
     * 验证 program parser 把 modifier declaration 和 identifier block declaration 保留为 raw 声明。
     * Verifies that the program parser preserves modifier declarations and identifier block declarations as raw declarations.
     */
    @Test
    fun `program parser preserves modifier declarations and raw modifier applications`() {
        val text = "modifier fn { [modifiers] fn [identifier]([identifier:type?]) { [body] } } fn add(a: Int) { a } add(1)"
        val result = parseProgram(text)

        assertFalse(result.hasErrors)
        assertEquals(
            "program(modifier fn; raw fn; call(add, int(1)))",
            result.program.render(),
        )
    }

    /**
     * 验证 modifier 和 raw declaration 的 span 覆盖完整源码结构。
     * Verifies that modifier and raw declaration spans cover the complete source structures.
     */
    @Test
    fun `program parser exposes modifier declaration spans`() {
        val result = parseProgram("modifier fn { { nested } } fn id() { 1 } id()")

        assertFalse(result.hasErrors)
        val modifier = assertIs<ModifierDeclaration>(result.program.declarations[0])
        val raw = assertIs<RawModifierApplication>(result.program.declarations[1])
        assertEquals(SourceSpan("sample.kk", 0, 26), modifier.span)
        assertEquals(SourceSpan("sample.kk", 27, 40), raw.span)
    }

    /**
     * 验证缺失 modifier 右大括号会产生 PARSE003，并验证括号内 block 不会被误判为 raw declaration。
     * Verifies that a missing modifier right brace produces PARSE003 and that a block inside parentheses is not mistaken for a raw declaration.
     */
    @Test
    fun `program parser reports malformed modifier declarations and ignores parenthesized blocks`() {
        val missingBrace = parseProgram("modifier fn { [body] 0")
        val parenthesizedBlock = parseProgram("name({ 1 })")
        val semicolonLookahead = parseProgram("name;")
        val unmatchedRightParenLookahead = parseProgram("name ) { 1 } 0")
        val unmatchedRightBraceRaw = parseProgram("decorated } { 1 } 0")
        val missingRawBrace = parseProgram("fn id() { 1")

        assertTrue(missingBrace.hasErrors)
        assertEquals(listOf("PARSE003", "PARSE001"), missingBrace.diagnostics.map { it.code })
        assertTrue(parenthesizedBlock.hasErrors)
        assertEquals(emptyList(), parenthesizedBlock.program.declarations)
        assertTrue(parenthesizedBlock.diagnostics.map { it.code }.contains("PARSE001"))
        assertEquals(listOf("PARSE002"), semicolonLookahead.diagnostics.map { it.code })
        assertFalse(unmatchedRightParenLookahead.hasErrors)
        assertEquals("raw name;", unmatchedRightParenLookahead.program.declarations.single().render())
        assertTrue(unmatchedRightBraceRaw.hasErrors)
        assertEquals("raw decorated;", unmatchedRightBraceRaw.program.declarations.single().render())
        assertTrue(missingRawBrace.hasErrors)
        assertEquals("raw fn;", missingRawBrace.program.declarations.single().render())
    }

    /**
     * 验证函数参数、函数体和单参数 call 的兼容属性。
     * Verifies compatibility properties for function parameters, function bodies, and single-argument calls.
     */
    @Test
    fun `ast compatibility properties expose spans and single call argument`() {
        val call = assertIs<CallExpression>(parse("print(1)").expression)
        val compatibilityCall = CallExpression(
            callee = call.callee,
            leftParen = call.leftParen,
            argument = call.arguments.single(),
            rightParen = call.rightParen,
        )
        val function = assertIs<FunctionDeclaration>(
            requireNotNull(parseExpandedFunction("fn id(a: Int) { a } id(1)")),
        )
        val untypedFunction = assertIs<FunctionDeclaration>(
            requireNotNull(parseExpandedFunction("fn id(a) { a } id(1)")),
        )
        val parameter = function.parameters.single()
        val untypedParameter = untypedFunction.parameters.single()

        assertEquals(SourceSpan("sample.kk", 6, 7), call.argument.span)
        assertEquals(call.arguments.single(), call.argument)
        assertEquals(call.argument, compatibilityCall.argument)
        assertEquals(SourceSpan("sample.kk", 6, 12), parameter.span)
        assertEquals(SourceSpan("sample.kk", 6, 7), untypedParameter.span)
        assertEquals(SourceSpan("sample.kk", 14, 19), function.body.span)
    }

    /**
     * 验证 val declaration 缺失必需 token 时产生 PARSE003。
     * Verifies that missing required tokens in val declarations produce PARSE003.
     */
    @Test
    fun `program parser reports missing val declaration separators`() {
        val result = parseProgram("val x = 1 x")

        assertTrue(result.hasErrors)
        assertEquals(listOf("PARSE003"), result.diagnostics.map { it.code })
        assertEquals("program(val x = int(1); id(x))", result.program.render())
    }

    /**
     * 验证 expression 位置的赋值写法不是重新赋值语法。
     * Verifies that assignment-like text in expression position is not reassignment syntax.
     */
    @Test
    fun `program parser treats assignment like expression as trailing tokens`() {
        val result = parseProgram("val x = 1; x = 2")

        assertTrue(result.hasErrors)
        assertEquals(listOf("PARSE002", "PARSE002"), result.diagnostics.map { it.code })
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
     * 验证 call expression 缺失右括号会产生 PARSE003。
     * Verifies that a call expression missing the right parenthesis produces PARSE003.
     */
    @Test
    fun `parser reports missing call right paren`() {
        val result = parse("print(1 + 2")

        assertTrue(result.hasErrors)
        assertEquals("call(print, (+ int(1) int(2)))", result.expression.render())
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
     * 验证高于 call 的自定义 infix binding power 会阻止右 operand 中的 call 后缀解析。
     * Verifies that custom infix binding power above calls prevents call postfix parsing in the right operand.
     */
    @Test
    fun `parser keeps call postfix below higher custom binding power`() {
        val caret = TokenKind("caret")
        val lexerConfig = LexerConfig.default().withRule(LexerRule.literal("caret", caret, "^"))
        val parserConfig = ParserConfig.default().withInfix(
            caret,
            InfixParselet.binary(precedence = 50, associativity = Associativity.Left),
        )

        val result = parse("a ^ b(1)", lexerConfig, parserConfig)

        assertTrue(result.hasErrors)
        assertEquals("(^ id(a) id(b))", result.expression.render())
        assertEquals(listOf("PARSE002", "PARSE002", "PARSE002"), result.diagnostics.map { it.code })
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

    /**
     * 使用指定 lexer/parser 配置解析一个 program。
     * Parses one program with the provided lexer and parser configuration.
     */
    private fun parseProgram(
        text: String,
        lexerConfig: LexerConfig = LexerConfig.default(),
        parserConfig: ParserConfig = ParserConfig.default(),
    ): ProgramParseResult {
        val source = SourceText.of("sample.kk", text)
        val lexResult = Lexer(lexerConfig).tokenize(source)
        val parseResult = Parser(lexResult.tokens, parserConfig).parseProgramDocument()
        return parseResult.copy(diagnostics = lexResult.diagnostics + parseResult.diagnostics)
    }

    /**
     * 解析并展开源码中的单个函数声明。
     * Parses and expands the single function declaration in source text.
     */
    private fun parseExpandedFunction(text: String): FunctionDeclaration? {
        val program = parseProgram(text).program
        return SeedModifierExpander().expand(program).program?.declarations?.singleOrNull() as? FunctionDeclaration
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
        is StringExpression -> "string($text)"
        is CallExpression -> {
            val argumentsText = arguments.joinToString(prefix = "", separator = ", ") { it.render() }
            if (arguments.isEmpty()) "call(${callee.name})" else "call(${callee.name}, $argumentsText)"
        }
        is PrefixExpression -> "(${operator.lexeme} ${operand.render()})"
        is BinaryExpression -> "(${operator.lexeme} ${left.render()} ${right.render()})"
        is GroupedExpression -> "(group ${expression.render()})"
        is MissingExpression -> "<missing>"
    }

/**
 * 将 AST program 渲染为稳定的测试断言字符串。
 * Renders an AST program into a stable assertion string for tests.
 */
private fun AstProgram.render(): String {
    val declarationsText = declarations.joinToString(separator = " ") { it.render() }
    return if (declarations.isEmpty()) {
        "program(${expression.render()})"
    } else {
        "program($declarationsText ${expression.render()})"
    }
}

/**
 * 将 declaration 渲染为稳定的测试断言字符串。
 * Renders a declaration into a stable assertion string for tests.
 */
private fun Declaration.render(): String =
    when (this) {
        is ValDeclaration -> "val $name = ${initializer.render()};"
        is ModifierDeclaration -> "modifier $name;"
        is RawModifierApplication -> "raw $name;"
        is FunctionDeclaration -> "fn $name;"
    }
