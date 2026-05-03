package cn.llonvne.kklang.typechecking

import cn.llonvne.kklang.binding.BoundBinary
import cn.llonvne.kklang.binding.BoundExpression
import cn.llonvne.kklang.binding.BoundGrouped
import cn.llonvne.kklang.binding.BoundInteger
import cn.llonvne.kklang.binding.BoundMissing
import cn.llonvne.kklang.binding.BoundPrintCall
import cn.llonvne.kklang.binding.BoundPrefix
import cn.llonvne.kklang.binding.BoundProgram
import cn.llonvne.kklang.binding.BoundVariable
import cn.llonvne.kklang.binding.SeedBindingResolver
import cn.llonvne.kklang.frontend.SourceSpan
import cn.llonvne.kklang.frontend.SourceText
import cn.llonvne.kklang.frontend.lexing.Lexer
import cn.llonvne.kklang.frontend.lexing.Token
import cn.llonvne.kklang.frontend.lexing.TokenKind
import cn.llonvne.kklang.frontend.parsing.AstProgram
import cn.llonvne.kklang.frontend.parsing.BinaryExpression
import cn.llonvne.kklang.frontend.parsing.CallExpression
import cn.llonvne.kklang.frontend.parsing.Expression
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
import kotlin.test.assertSame
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
     * 验证字符串字面量会推导为 String 类型。
     * Verifies that string literals are inferred as the String type.
     */
    @Test
    fun `type checker infers string literals`() {
        val result = check("\"hello\"")

        assertFalse(result.hasErrors)
        val expression = assertIs<TypedString>(requireNotNull(result.expression))
        assertEquals(TypeRef.String, expression.type)
        assertEquals("hello", expression.syntax.text)
    }

    /**
     * 验证内建 print 调用接受当前值类型并返回 Unit。
     * Verifies that builtin print calls accept current value types and return Unit.
     */
    @Test
    fun `type checker infers builtin print calls as unit`() {
        val intPrint = check("print(1)")
        val stringPrint = check("print(\"hello\")")
        val unitPrint = check("print(print(\"hello\"))")

        assertFalse(intPrint.hasErrors)
        assertFalse(stringPrint.hasErrors)
        assertFalse(unitPrint.hasErrors)
        assertEquals(TypeRef.Unit, requireNotNull(intPrint.expression).type)
        assertEquals(TypeRef.Unit, requireNotNull(stringPrint.expression).type)
        assertEquals("print(print(string(hello)))", requireNotNull(unitPrint.expression).render())
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
        val yDeclaration = program.declarations.single { it.name == "y" }
        val expression = assertIs<TypedBinary>(program.expression)
        val variable = assertIs<TypedVariable>(expression.left)
        assertSame(yDeclaration.symbol, variable.symbol)
    }

    /**
     * 验证 program 便捷入口会先执行 binding 顺序检查。
     * Verifies that the program convenience entry point runs binding order checks first.
     */
    @Test
    fun `type checker program entrypoint runs binding order checks`() {
        val earlier = checkProgram("val x = 1; val y = x; y")
        val self = checkProgram("val x = x; 1")
        val later = checkProgram("val x = y; val y = 1; x")

        assertFalse(earlier.hasErrors)
        assertEquals(listOf("TYPE001"), self.diagnostics.map { it.code })
        assertEquals(listOf("TYPE001", "TYPE001"), later.diagnostics.map { it.code })
    }

    /**
     * 验证 program 便捷入口会先返回 binding 的重复声明 diagnostic。
     * Verifies that the program convenience entry point returns binding duplicate diagnostics first.
     */
    @Test
    fun `type checker program entrypoint returns duplicate binding diagnostics`() {
        val result = checkProgram("val x = 1; val x = 2; x")

        assertTrue(result.hasErrors)
        assertEquals(listOf("BIND001"), result.diagnostics.map { it.code })
    }

    /**
     * 验证单 expression 便捷入口在空 scope 下仍会报告 unresolved identifier。
     * Verifies that the single-expression convenience entry point still reports unresolved identifiers in an empty scope.
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
     * 验证已绑定 program 中 declaration 或最终 expression 的类型失败都会让 program 失败。
     * Verifies that type failures in declarations or the final expression fail an already bound program.
     */
    @Test
    fun `type checker reports bound program declaration and final expression failures`() {
        val declarationFailure = SeedTypeChecker().check(boundProgram("val x = ; 1"))
        val expressionFailure = SeedTypeChecker().check(boundProgram("val x = 1;"))

        assertTrue(declarationFailure.hasErrors)
        assertEquals(listOf("TYPE002"), declarationFailure.diagnostics.map { it.code })
        assertTrue(expressionFailure.hasErrors)
        assertEquals(listOf("TYPE002"), expressionFailure.diagnostics.map { it.code })
    }

    /**
     * 验证 malformed bound tree 中的内部失败路径会产生诊断并阻止 typed program。
     * Verifies that inner failure paths in malformed bound trees report diagnostics and block typed programs.
     */
    @Test
    fun `type checker reports malformed bound tree inner failures`() {
        val missing = BoundMissing(MissingExpression(SourceSpan("sample.kk", 0, 0)))
        val one = BoundInteger(assertIs<IntegerExpression>(parse("1")))
        val plus = Lexer().tokenize(SourceText.of("sample.kk", "+")).tokens.first()
        val minus = Lexer().tokenize(SourceText.of("sample.kk", "-")).tokens.first()
        val leftParen = Lexer().tokenize(SourceText.of("sample.kk", "(")).tokens.first()
        val rightParen = Lexer().tokenize(SourceText.of("sample.kk", ")")).tokens.first()
        val grouped = BoundGrouped(GroupedExpression(leftParen, missing.syntax, rightParen), missing)
        val prefix = BoundPrefix(PrefixExpression(minus, missing.syntax), missing)
        val print = BoundPrintCall(assertIs<CallExpression>(parse("print(1)")), missing)
        val leftFailure = BoundBinary(BinaryExpression(missing.syntax, plus, one.syntax), missing, one)
        val rightFailure = BoundBinary(BinaryExpression(one.syntax, plus, missing.syntax), one, missing)

        assertEquals(listOf("TYPE002"), checkBoundExpression(grouped).diagnostics.map { it.code })
        assertEquals(listOf("TYPE002"), checkBoundExpression(prefix).diagnostics.map { it.code })
        assertEquals(listOf("TYPE002"), checkBoundExpression(print).diagnostics.map { it.code })
        assertEquals(listOf("TYPE002"), checkBoundExpression(leftFailure).diagnostics.map { it.code })
        assertEquals(listOf("TYPE002"), checkBoundExpression(rightFailure).diagnostics.map { it.code })
    }

    /**
     * 验证已绑定变量的符号若没有对应 scope 类型仍会被拒绝。
     * Verifies that a bound variable is still rejected when its symbol has no matching scope type.
     */
    @Test
    fun `type checker reports bound variable without scoped type`() {
        val validProgram = boundProgram("val x = 1; x")
        val symbol = validProgram.symbols.single()
        val variable = BoundVariable(assertIs<IdentifierExpression>(parse("x")), symbol)
        val result = SeedTypeChecker().check(
            BoundProgram(
                syntax = AstProgram(variable.syntax),
                declarations = emptyList(),
                expression = variable,
                symbols = listOf(symbol),
            ),
        )

        assertTrue(result.hasErrors)
        assertEquals(listOf("TYPE001"), result.diagnostics.map { it.code })
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
     * 验证整数运算不接受字符串 operand。
     * Verifies that integer operations do not accept string operands.
     */
    @Test
    fun `type checker rejects string operands for integer operators`() {
        assertEquals(listOf("TYPE002"), check("-\"x\"").diagnostics.map { it.code })
        assertEquals(listOf("TYPE002"), check("1 + \"x\"").diagnostics.map { it.code })
        assertEquals(listOf("TYPE002"), check("\"x\" + 1").diagnostics.map { it.code })
        assertEquals(listOf("TYPE002"), check("print(1) + 1").diagnostics.map { it.code })
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
     * 解析并 binding 一个测试 program。
     * Parses and binds one test program.
     */
    private fun boundProgram(text: String): BoundProgram =
        requireNotNull(SeedBindingResolver().resolve(parseProgram(text)).program)

    /**
     * 把单个 bound expression 包装成测试 program 后执行类型检查。
     * Wraps one bound expression in a test program before type checking.
     */
    private fun checkBoundExpression(expression: BoundExpression): ProgramTypeCheckResult =
        SeedTypeChecker().check(BoundProgram(AstProgram(expression.syntax), emptyList(), expression, emptyList()))

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
        is TypedString -> "string(${syntax.text})"
        is TypedPrintCall -> "print(${argument.render()})"
        is TypedVariable -> "variable(${syntax.name})"
        is TypedGrouped -> "grouped(${inner.render()})"
        is TypedPrefix -> "prefix(${syntax.operator.lexeme}, ${operand.render()})"
        is TypedBinary -> "binary(${syntax.operator.lexeme}, ${left.render()}, ${right.render()})"
    }
