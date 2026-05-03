package cn.llonvne.kklang.execution

import cn.llonvne.kklang.frontend.SourceSpan
import cn.llonvne.kklang.frontend.SourceText
import cn.llonvne.kklang.frontend.lexing.Lexer
import cn.llonvne.kklang.frontend.lexing.Token
import cn.llonvne.kklang.frontend.lexing.TokenKind
import cn.llonvne.kklang.frontend.parsing.BinaryExpression
import cn.llonvne.kklang.frontend.parsing.Parser
import cn.llonvne.kklang.frontend.parsing.PrefixExpression
import cn.llonvne.kklang.typechecking.SeedTypeChecker
import cn.llonvne.kklang.typechecking.TypeRef
import cn.llonvne.kklang.typechecking.TypedBinary
import cn.llonvne.kklang.typechecking.TypedExpression
import cn.llonvne.kklang.typechecking.TypedPrefix
import cn.llonvne.kklang.typechecking.TypedProgram
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 覆盖 CoreIrLowerer 支持的 typed AST、失败传播和辅助渲染。
 * Covers CoreIrLowerer supported typed AST, failure propagation, and helper rendering.
 */
class CoreIrLowererTest {
    /**
     * 验证 seed 表达式语法中的 typed 支持形式都能降级为 Core IR。
     * Verifies that all supported typed seed expression forms lower to Core IR.
     */
    @Test
    fun `lowerer lowers all supported typed seed expression forms`() {
        val result = lower("-(1 + 2) * +3")

        assertFalse(result.hasErrors)
        assertEquals(
            "(* (neg (+ int64(1) int64(2))) (pos int64(3)))",
            requireNotNull(result.ir).render(),
        )
    }

    /**
     * 验证 lowerer 会把 typed val declarations 和 variable references 降级为 Core IR。
     * Verifies that the lowerer lowers typed val declarations and variable references into Core IR.
     */
    @Test
    fun `lowerer lowers typed val declarations and variable references`() {
        val result = lowerProgram("val x = 1; val y = x + 2; y")

        assertFalse(result.hasErrors)
        val program = requireNotNull(result.program)
        assertEquals(listOf("x", "y"), program.declarations.map { it.name })
        assertEquals("var(y)", program.expression.render())
        assertEquals("(+ var(x) int64(2))", program.declarations[1].initializer.render())
    }

    /**
     * 验证字符串字面量会 lowering 为 Core IR 字符串节点。
     * Verifies that string literals lower into Core IR string nodes.
     */
    @Test
    fun `lowerer lowers string literals`() {
        val result = lowerProgram("val text = \"hello\"; text")

        assertFalse(result.hasErrors)
        val program = requireNotNull(result.program)
        assertEquals("string(hello)", program.declarations.single().initializer.render())
        assertEquals("var(text)", program.expression.render())
    }

    /**
     * 验证内建 print 调用会 lowering 为 Core IR print 节点。
     * Verifies that builtin print calls lower into Core IR print nodes.
     */
    @Test
    fun `lowerer lowers builtin print calls`() {
        val result = lowerProgram("val text = \"hello\"; print(text)")

        assertFalse(result.hasErrors)
        val program = requireNotNull(result.program)
        assertEquals("print(var(text))", program.expression.render())
        assertEquals(TypeRef.Unit, type("print(1)").type)
    }

    /**
     * 验证 print argument lowering 失败会向外传播。
     * Verifies that a print argument lowering failure propagates outward.
     */
    @Test
    fun `lowerer reports print argument lowering failure`() {
        val result = lower("print(9223372036854775808)")

        assertTrue(result.hasErrors)
        assertNull(result.ir)
        assertEquals("EXEC003", result.diagnostics.single().code)
    }

    /**
     * 验证 val declaration initializer 的 lowering 失败会让整个 program 失败。
     * Verifies that lowering failure in a val declaration initializer fails the whole program.
     */
    @Test
    fun `lowerer reports val declaration initializer failures`() {
        val result = lowerProgram("val x = 9223372036854775808; 1")

        assertTrue(result.hasErrors)
        assertNull(result.program)
        assertNull(result.ir)
        assertEquals("EXEC003", result.diagnostics.single().code)
    }

    /**
     * 验证超出 Int64 范围的 typed 整数字面量产生 EXEC003。
     * Verifies that typed integer literals outside Int64 range produce EXEC003.
     */
    @Test
    fun `lowerer reports invalid integer literals`() {
        val result = CoreIrLowerer().lower(type("9223372036854775808"))

        assertTrue(result.hasErrors)
        assertEquals("EXEC003", result.diagnostics.single().code)
    }

    /**
     * 验证 malformed typed prefix 和 binary operator 都产生 EXEC001 防御诊断。
     * Verifies that malformed typed prefix and binary operators both produce defensive EXEC001 diagnostics.
     */
    @Test
    fun `lowerer reports malformed typed prefix and binary operators`() {
        val one = type("1")
        val two = type("2")
        val unknownOperator = Token(TokenKind("unknown_operator"), "?", SourceSpan("sample.kk", 0, 1))
        val prefixSyntax = PrefixExpression(operator = unknownOperator, operand = parse("1"))
        val binarySyntax = BinaryExpression(left = parse("1"), operator = unknownOperator, right = parse("2"))
        val prefix = TypedPrefix(syntax = prefixSyntax, operand = one, type = TypeRef.Int64)
        val binary = TypedBinary(syntax = binarySyntax, left = one, right = two, type = TypeRef.Int64)

        assertEquals("EXEC001", CoreIrLowerer().lower(prefix).diagnostics.single().code)
        assertEquals("EXEC001", CoreIrLowerer().lower(binary).diagnostics.single().code)
    }

    /**
     * 验证二元 typed 表达式左右两侧的 lowering 失败都会传播。
     * Verifies that lowering failures from either side of a typed binary expression are propagated.
     */
    @Test
    fun `lowerer reports left and right binary lowering failures`() {
        val plus = Lexer().tokenize(SourceText.of("sample.kk", "+")).tokens.first()
        val leftFailure = TypedBinary(
            syntax = BinaryExpression(left = parse("9223372036854775808"), operator = plus, right = parse("1")),
            left = type("9223372036854775808"),
            right = type("1"),
            type = TypeRef.Int64,
        )
        val rightFailure = TypedBinary(
            syntax = BinaryExpression(left = parse("1"), operator = plus, right = parse("9223372036854775808")),
            left = type("1"),
            right = type("9223372036854775808"),
            type = TypeRef.Int64,
        )

        assertEquals("EXEC003", CoreIrLowerer().lower(leftFailure).diagnostics.single().code)
        assertEquals("EXEC003", CoreIrLowerer().lower(rightFailure).diagnostics.single().code)
    }

    /**
     * 验证 prefix operand 的 lowering 失败会向外传播。
     * Verifies that a lowering failure from a typed prefix operand propagates outward.
     */
    @Test
    fun `lowerer reports prefix operand lowering failure`() {
        val result = lower("+9223372036854775808")

        assertTrue(result.hasErrors)
        assertEquals("EXEC003", result.diagnostics.single().code)
    }

    /**
     * 验证成功 lowering 会暴露具体 Core IR 类型。
     * Verifies that successful lowering exposes the concrete Core IR type.
     */
    @Test
    fun `lowerer result success exposes typed ir`() {
        val result = lower("1")

        assertIs<IrInt64>(result.ir)
        assertFalse(result.hasErrors)
    }

    /**
     * 解析、类型检查并 lowering 一段测试源码。
     * Parses, type-checks, and lowers one test source snippet.
     */
    private fun lower(text: String): IrLoweringResult =
        CoreIrLowerer().lower(type(text))

    /**
     * 解析、类型检查并 lowering 一个测试 program。
     * Parses, type-checks, and lowers one test program.
     */
    private fun lowerProgram(text: String): IrLoweringResult =
        CoreIrLowerer().lower(typeProgram(text))

    /**
     * 解析并类型检查一段测试源码。
     * Parses and type-checks one test source snippet.
     */
    private fun type(text: String): TypedExpression =
        requireNotNull(SeedTypeChecker().check(parse(text)).expression)

    /**
     * 解析并类型检查一个测试 program。
     * Parses and type-checks one test program.
     */
    private fun typeProgram(text: String): TypedProgram =
        requireNotNull(SeedTypeChecker().check(parseProgram(text)).program)

    /**
     * 使用默认 lexer/parser 解析一段测试源码。
     * Parses one test source snippet with the default lexer and parser.
     */
    private fun parse(text: String) =
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
 * 将 Core IR 渲染为稳定的测试断言字符串。
 * Renders Core IR into a stable assertion string for tests.
 */
private fun IrExpression.render(): String =
    when (this) {
        is IrInt64 -> "int64($value)"
        is IrString -> "string($value)"
        is IrPrint -> "print(${argument.render()})"
        is IrVariable -> "var($name)"
        is IrUnary -> "(${operator.text} ${operand.render()})"
        is IrBinary -> "(${operator.text} ${left.render()} ${right.render()})"
    }
