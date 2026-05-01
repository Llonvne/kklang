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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
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
     * 解析并类型检查一段测试源码。
     * Parses and type-checks one test source snippet.
     */
    private fun type(text: String): TypedExpression =
        requireNotNull(SeedTypeChecker().check(parse(text)).expression)

    /**
     * 使用默认 lexer/parser 解析一段测试源码。
     * Parses one test source snippet with the default lexer and parser.
     */
    private fun parse(text: String) =
        Parser(Lexer().tokenize(SourceText.of("sample.kk", text)).tokens)
            .parseExpressionDocument()
            .expression
}

/**
 * 将 Core IR 渲染为稳定的测试断言字符串。
 * Renders Core IR into a stable assertion string for tests.
 */
private fun IrExpression.render(): String =
    when (this) {
        is IrInt64 -> "int64($value)"
        is IrUnary -> "(${operator.text} ${operand.render()})"
        is IrBinary -> "(${operator.text} ${left.render()} ${right.render()})"
    }
