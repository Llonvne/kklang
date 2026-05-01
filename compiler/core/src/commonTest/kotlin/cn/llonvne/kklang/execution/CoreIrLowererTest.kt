package cn.llonvne.kklang.execution

import cn.llonvne.kklang.frontend.SourceSpan
import cn.llonvne.kklang.frontend.SourceText
import cn.llonvne.kklang.frontend.lexing.Lexer
import cn.llonvne.kklang.frontend.lexing.Token
import cn.llonvne.kklang.frontend.lexing.TokenKind
import cn.llonvne.kklang.frontend.parsing.BinaryExpression
import cn.llonvne.kklang.frontend.parsing.GroupedExpression
import cn.llonvne.kklang.frontend.parsing.IdentifierExpression
import cn.llonvne.kklang.frontend.parsing.IntegerExpression
import cn.llonvne.kklang.frontend.parsing.MissingExpression
import cn.llonvne.kklang.frontend.parsing.Parser
import cn.llonvne.kklang.frontend.parsing.PrefixExpression
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 覆盖 CoreIrLowerer 支持的表达式、unsupported 分支和辅助渲染。
 * Covers CoreIrLowerer supported expressions, unsupported branches, and helper rendering.
 */
class CoreIrLowererTest {
    /**
     * 验证 seed 表达式语法中的支持形式都能降级为 Core IR。
     * Verifies that all supported seed expression forms lower to Core IR.
     */
    @Test
    fun `lowerer lowers all supported seed expression forms`() {
        val result = lower("-(1 + 2) * +3")

        assertFalse(result.hasErrors)
        assertEquals(
            "(* (neg (+ int64(1) int64(2))) (pos int64(3)))",
            requireNotNull(result.ir).render(),
        )
    }

    /**
     * 验证标识符在当前执行范围内被报告为 unsupported。
     * Verifies that identifiers are reported as unsupported in the current execution scope.
     */
    @Test
    fun `lowerer reports unsupported identifiers`() {
        val result = lower("name")

        assertTrue(result.hasErrors)
        assertEquals("EXEC001", result.diagnostics.single().code)
        assertEquals(null, result.ir)
    }

    /**
     * 验证超出 Int64 范围的整数字面量产生 EXEC003。
     * Verifies that integer literals outside Int64 range produce EXEC003.
     */
    @Test
    fun `lowerer reports invalid integer literals`() {
        val expression = IntegerExpression(
            token = Lexer().tokenize(SourceText.of("sample.kk", "9223372036854775808")).tokens.first(),
        )

        val result = CoreIrLowerer().lower(expression)

        assertTrue(result.hasErrors)
        assertEquals("EXEC003", result.diagnostics.single().code)
    }

    /**
     * 验证 MissingExpression 会被 lowering 报告为 unsupported。
     * Verifies that MissingExpression is reported as unsupported during lowering.
     */
    @Test
    fun `lowerer reports missing expressions`() {
        val span = SourceSpan("sample.kk", 0, 0)
        val result = CoreIrLowerer().lower(MissingExpression(span))

        assertTrue(result.hasErrors)
        assertEquals("EXEC001", result.diagnostics.single().code)
    }

    /**
     * 验证未知 prefix 和 binary operator 都产生 EXEC001。
     * Verifies that unknown prefix and binary operators both produce EXEC001.
     */
    @Test
    fun `lowerer reports unsupported prefix and binary operators`() {
        val left = parse("1")
        val right = parse("2")
        val unknownOperator = Token(TokenKind("unknown_operator"), "?", SourceSpan("sample.kk", 0, 1))
        val prefix = PrefixExpression(operator = unknownOperator, operand = left)
        val binary = BinaryExpression(left = left, operator = unknownOperator, right = right)

        assertEquals("EXEC001", CoreIrLowerer().lower(prefix).diagnostics.single().code)
        assertEquals("EXEC001", CoreIrLowerer().lower(binary).diagnostics.single().code)
    }

    /**
     * 验证二元表达式左右两侧的 lowering 失败都会传播。
     * Verifies that lowering failures from either side of a binary expression are propagated.
     */
    @Test
    fun `lowerer reports left and right binary lowering failures`() {
        val one = parse("1")
        val name = parse("name")
        val plus = Lexer().tokenize(SourceText.of("sample.kk", "+")).tokens.first()

        val leftFailure = CoreIrLowerer().lower(BinaryExpression(left = name, operator = plus, right = one))
        val rightFailure = CoreIrLowerer().lower(BinaryExpression(left = one, operator = plus, right = name))

        assertEquals("EXEC001", leftFailure.diagnostics.single().code)
        assertEquals("EXEC001", rightFailure.diagnostics.single().code)
    }

    /**
     * 验证分组表达式内部的 lowering 失败会向外传播。
     * Verifies that a lowering failure inside a grouped expression propagates outward.
     */
    @Test
    fun `lowerer reports nested lowering failures`() {
        val grouped = GroupedExpression(
            leftParen = Lexer().tokenize(SourceText.of("sample.kk", "(")).tokens.first(),
            expression = parse("name"),
            rightParen = Lexer().tokenize(SourceText.of("sample.kk", ")")).tokens.first(),
        )

        val result = CoreIrLowerer().lower(grouped)

        assertTrue(result.hasErrors)
        assertEquals("EXEC001", result.diagnostics.single().code)
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
     * 解析并 lowering 一段测试源码。
     * Parses and lowers one test source snippet.
     */
    private fun lower(text: String): IrLoweringResult =
        CoreIrLowerer().lower(parse(text))

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
