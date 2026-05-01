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

class CoreIrLowererTest {
    @Test
    fun `lowerer lowers all supported seed expression forms`() {
        val result = lower("-(1 + 2) * +3")

        assertFalse(result.hasErrors)
        assertEquals(
            "(* (neg (+ int64(1) int64(2))) (pos int64(3)))",
            requireNotNull(result.ir).render(),
        )
    }

    @Test
    fun `lowerer reports unsupported identifiers`() {
        val result = lower("name")

        assertTrue(result.hasErrors)
        assertEquals("EXEC001", result.diagnostics.single().code)
        assertEquals(null, result.ir)
    }

    @Test
    fun `lowerer reports invalid integer literals`() {
        val expression = IntegerExpression(
            token = Lexer().tokenize(SourceText.of("sample.kk", "9223372036854775808")).tokens.first(),
        )

        val result = CoreIrLowerer().lower(expression)

        assertTrue(result.hasErrors)
        assertEquals("EXEC003", result.diagnostics.single().code)
    }

    @Test
    fun `lowerer reports missing expressions`() {
        val span = SourceSpan("sample.kk", 0, 0)
        val result = CoreIrLowerer().lower(MissingExpression(span))

        assertTrue(result.hasErrors)
        assertEquals("EXEC001", result.diagnostics.single().code)
    }

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

    @Test
    fun `lowerer result success exposes typed ir`() {
        val result = lower("1")

        assertIs<IrInt64>(result.ir)
        assertFalse(result.hasErrors)
    }

    private fun lower(text: String): IrLoweringResult =
        CoreIrLowerer().lower(parse(text))

    private fun parse(text: String) =
        Parser(Lexer().tokenize(SourceText.of("sample.kk", text)).tokens)
            .parseExpressionDocument()
            .expression
}

private fun IrExpression.render(): String =
    when (this) {
        is IrInt64 -> "int64($value)"
        is IrUnary -> "(${operator.text} ${operand.render()})"
        is IrBinary -> "(${operator.text} ${left.render()} ${right.render()})"
    }
