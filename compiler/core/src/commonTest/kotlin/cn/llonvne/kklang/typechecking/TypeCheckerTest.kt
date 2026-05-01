package cn.llonvne.kklang.typechecking

import cn.llonvne.kklang.frontend.SourceSpan
import cn.llonvne.kklang.frontend.SourceText
import cn.llonvne.kklang.frontend.lexing.Lexer
import cn.llonvne.kklang.frontend.lexing.Token
import cn.llonvne.kklang.frontend.lexing.TokenKind
import cn.llonvne.kklang.frontend.parsing.BinaryExpression
import cn.llonvne.kklang.frontend.parsing.Expression
import cn.llonvne.kklang.frontend.parsing.GroupedExpression
import cn.llonvne.kklang.frontend.parsing.MissingExpression
import cn.llonvne.kklang.frontend.parsing.Parser
import cn.llonvne.kklang.frontend.parsing.PrefixExpression
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 覆盖最小 type checker 的 Int64 推导、失败 diagnostics 和 typed AST 形状。
 * Covers minimal type-checker Int64 inference, failure diagnostics, and typed AST shapes.
 */
class TypeCheckerTest {
    /**
     * 验证所有当前支持的 seed expression 形式都会推导为 Int64 typed AST。
     * Verifies that every currently supported seed expression form is inferred as an Int64 typed AST.
     */
    @Test
    fun `type checker infers int64 for supported seed expressions`() {
        val result = check("-(1 + 2) * +3")

        assertFalse(result.hasErrors)
        assertEquals(TypeRef.Int64, requireNotNull(result.expression).type)
        assertEquals(SourceSpan("sample.kk", 0, 13), result.expression.syntax.span)
        assertEquals("binary(*, prefix(-, grouped(binary(+, int64, int64))), prefix(+, int64))", result.expression.render())
    }

    /**
     * 验证分组表达式的类型等于内部表达式类型。
     * Verifies that a grouped expression has the same type as its inner expression.
     */
    @Test
    fun `type checker preserves grouped inner type`() {
        val expression = requireNotNull(check("(1)").expression)

        assertIs<TypedGrouped>(expression)
        assertEquals(expression.inner.type, expression.type)
        assertEquals(TypeRef.Int64, expression.type)
    }

    /**
     * 验证 val declaration 会把名字绑定到 initializer 类型。
     * Verifies that a val declaration binds its name to the initializer type.
     */
    @Test
    fun `type checker binds immutable val declarations`() {
        val result = checkProgram("val x = 1; val y = x + 2; y * 3")

        assertFalse(result.hasErrors)
        val program = requireNotNull(result.program)
        assertEquals(TypeRef.Int64, program.type)
        assertEquals(listOf("x", "y"), program.declarations.map { it.name })
        assertEquals(TypeRef.Int64, program.declarations.single { it.name == "x" }.type)
        assertEquals("binary(*, variable(y), int64)", program.expression.render())
    }

    /**
     * 验证 val initializer 可以引用之前的 val，但不能引用自身或后续 val。
     * Verifies that a val initializer can reference earlier vals but cannot reference itself or later vals.
     */
    @Test
    fun `type checker enforces declaration order`() {
        val earlier = checkProgram("val x = 1; val y = x; y")
        val self = checkProgram("val x = x; 1")
        val later = checkProgram("val x = y; val y = 1; x")

        assertFalse(earlier.hasErrors)
        assertEquals(listOf("TYPE001"), self.diagnostics.map { it.code })
        assertEquals(listOf("TYPE001", "TYPE001"), later.diagnostics.map { it.code })
    }

    /**
     * 验证同一 program scope 重复声明 val 会失败。
     * Verifies that redeclaring a val in the same program scope fails.
     */
    @Test
    fun `type checker rejects duplicate immutable vals`() {
        val result = checkProgram("val x = 1; val x = 2; x")

        assertTrue(result.hasErrors)
        assertEquals(listOf("BIND001"), result.diagnostics.map { it.code })
    }

    /**
     * 验证标识符在类型检查阶段产生 unresolved identifier diagnostic。
     * Verifies that identifiers produce an unresolved-identifier diagnostic during type checking.
     */
    @Test
    fun `type checker reports unresolved identifiers`() {
        val result = check("name")

        assertTrue(result.hasErrors)
        assertEquals(null, result.expression)
        assertEquals(listOf("TYPE001"), result.diagnostics.map { it.code })
    }

    /**
     * 验证 missing expression 被类型检查阶段报告为不支持。
     * Verifies that missing expressions are reported as unsupported by type checking.
     */
    @Test
    fun `type checker reports missing expressions`() {
        val result = SeedTypeChecker().check(MissingExpression(SourceSpan("sample.kk", 0, 0)))

        assertTrue(result.hasErrors)
        assertEquals(listOf("TYPE002"), result.diagnostics.map { it.code })
    }

    /**
     * 验证未知 prefix 和 binary operator 都产生 TYPE002。
     * Verifies that unknown prefix and binary operators both produce TYPE002.
     */
    @Test
    fun `type checker reports unsupported prefix and binary operators`() {
        val one = parse("1")
        val two = parse("2")
        val unknownOperator = Token(TokenKind("unknown_operator"), "?", SourceSpan("sample.kk", 0, 1))
        val prefix = PrefixExpression(operator = unknownOperator, operand = one)
        val binary = BinaryExpression(left = one, operator = unknownOperator, right = two)

        assertEquals("TYPE002", SeedTypeChecker().check(prefix).diagnostics.single().code)
        assertEquals("TYPE002", SeedTypeChecker().check(binary).diagnostics.single().code)
    }

    /**
     * 验证二元表达式左右两侧的类型检查失败都会传播。
     * Verifies that type-checking failures from either side of a binary expression are propagated.
     */
    @Test
    fun `type checker reports left and right binary failures`() {
        val one = parse("1")
        val name = parse("name")
        val plus = Lexer().tokenize(SourceText.of("sample.kk", "+")).tokens.first()

        val leftFailure = SeedTypeChecker().check(BinaryExpression(left = name, operator = plus, right = one))
        val rightFailure = SeedTypeChecker().check(BinaryExpression(left = one, operator = plus, right = name))

        assertEquals(listOf("TYPE001"), leftFailure.diagnostics.map { it.code })
        assertEquals(listOf("TYPE001"), rightFailure.diagnostics.map { it.code })
    }

    /**
     * 验证 prefix operand 的类型检查失败会向外传播。
     * Verifies that type-checking failure from a prefix operand propagates outward.
     */
    @Test
    fun `type checker reports prefix operand failure`() {
        val minus = Lexer().tokenize(SourceText.of("sample.kk", "-")).tokens.first()
        val result = SeedTypeChecker().check(PrefixExpression(operator = minus, operand = parse("name")))

        assertEquals(listOf("TYPE001"), result.diagnostics.map { it.code })
        assertEquals(null, result.expression)
    }

    /**
     * 验证分组内部的类型检查失败会向外传播。
     * Verifies that a type-checking failure inside a grouped expression propagates outward.
     */
    @Test
    fun `type checker reports grouped inner failure`() {
        val grouped = GroupedExpression(
            leftParen = Lexer().tokenize(SourceText.of("sample.kk", "(")).tokens.first(),
            expression = parse("name"),
            rightParen = Lexer().tokenize(SourceText.of("sample.kk", ")")).tokens.first(),
        )

        val result = SeedTypeChecker().check(grouped)

        assertTrue(result.hasErrors)
        assertEquals(listOf("TYPE001"), result.diagnostics.map { it.code })
    }

    /**
     * 解析并类型检查一段测试源码。
     * Parses and type-checks one test source snippet.
     */
    private fun check(text: String): TypeCheckResult =
        SeedTypeChecker().check(parse(text))

    /**
     * 解析并类型检查一个测试 program。
     * Parses and type-checks one test program.
     */
    private fun checkProgram(text: String): ProgramTypeCheckResult =
        SeedTypeChecker().check(parseProgram(text))

    /**
     * 使用默认 lexer/parser 解析一段测试源码。
     * Parses one test source snippet with the default lexer and parser.
     */
    private fun parse(text: String): Expression =
        Parser(Lexer().tokenize(SourceText.of("sample.kk", text)).tokens)
            .parseExpressionDocument()
            .expression

    /**
     * 使用默认 lexer/parser 解析一个测试 program。
     * Parses one test program with the default lexer and parser.
     */
    private fun parseProgram(text: String) =
        Parser(Lexer().tokenize(SourceText.of("sample.kk", text)).tokens)
            .parseProgramDocument()
            .program
}

/**
 * 将 typed AST 渲染为稳定的测试断言字符串。
 * Renders typed AST into a stable assertion string for tests.
 */
private fun TypedExpression.render(): String =
    when (this) {
        is TypedInteger -> "int64"
        is TypedVariable -> "variable(${syntax.name})"
        is TypedGrouped -> "grouped(${inner.render()})"
        is TypedPrefix -> "prefix(${syntax.operator.lexeme}, ${operand.render()})"
        is TypedBinary -> "binary(${syntax.operator.lexeme}, ${left.render()}, ${right.render()})"
    }
