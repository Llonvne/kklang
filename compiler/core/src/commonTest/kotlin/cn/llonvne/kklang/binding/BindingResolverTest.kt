package cn.llonvne.kklang.binding

import cn.llonvne.kklang.frontend.SourceSpan
import cn.llonvne.kklang.frontend.SourceText
import cn.llonvne.kklang.frontend.lexing.Lexer
import cn.llonvne.kklang.frontend.parsing.AstProgram
import cn.llonvne.kklang.frontend.parsing.MissingExpression
import cn.llonvne.kklang.frontend.parsing.Parser
import cn.llonvne.kklang.metaprogramming.SeedModifierExpander
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 覆盖 binding resolver 的符号构建、引用顺序规则和 diagnostics。
 * Covers binding-resolver symbol construction, reference-order rules, and diagnostics.
 */
class BindingResolverTest {
    /**
     * 验证 binding resolver 为不可变 val declarations 构建有序符号。
     * Verifies that the binding resolver builds ordered symbols for immutable val declarations.
     */
    @Test
    fun `binding resolver builds immutable val symbols`() {
        val syntax = parseProgram("val x = 1; val y = x + 2; y")
        val result = SeedBindingResolver().resolve(syntax)

        assertFalse(result.hasErrors)
        val program = requireNotNull(result.program)
        assertSame(syntax, program.syntax)
        assertSame(syntax.expression, program.expression.syntax)
        assertEquals(SourceSpan("sample.kk", 0, 27), program.span)
        assertEquals(listOf("x", "y"), program.declarations.map { it.name })
        assertEquals(listOf("x", "y"), program.symbols.map { it.name })
        assertSame(program.declarations[0].syntax, program.symbols[0].declaration)
        assertEquals("binary(+, variable(x -> x), int64)", program.declarations[1].initializer.render())
        assertEquals(SourceSpan("sample.kk", 19, 24), program.declarations[1].initializer.span)
        assertEquals("variable(y -> y)", program.expression.render())
        assertEquals(SourceSpan("sample.kk", 26, 27), program.expression.span)
    }

    /**
     * 验证 binding resolver 会遍历最终表达式中的 prefix、binary 和 grouped 引用。
     * Verifies that the binding resolver walks prefix, binary, and grouped references in the final expression.
     */
    @Test
    fun `binding resolver walks nested expression references`() {
        val result = resolve("val x = 1; -(x + (x))")

        assertFalse(result.hasErrors)
        val expression = requireNotNull(result.program).expression

        assertEquals(listOf("x"), result.program.symbols.map { it.name })
        assertEquals("prefix(-, grouped(binary(+, variable(x -> x), grouped(variable(x -> x)))))", expression.render())
    }

    /**
     * 验证 binding resolver 会保留字符串字面量为 bound string 节点。
     * Verifies that the binding resolver preserves string literals as bound string nodes.
     */
    @Test
    fun `binding resolver binds string literals`() {
        val result = resolve("val text = \"hello\"; text")

        assertFalse(result.hasErrors)
        val program = requireNotNull(result.program)
        assertEquals("string(hello)", program.declarations.single().initializer.render())
        assertEquals("variable(text -> text)", program.expression.render())
    }

    /**
     * 验证 binding resolver 将内建 print 调用绑定为专用节点。
     * Verifies that the binding resolver binds builtin print calls as a dedicated node.
     */
    @Test
    fun `binding resolver binds builtin print calls`() {
        val result = resolve("val text = \"hello\"; print(text)")

        assertFalse(result.hasErrors)
        val program = requireNotNull(result.program)
        assertEquals("print(variable(text -> text))", program.expression.render())
    }

    /**
     * 验证 binding resolver 绑定 modifier expansion 后的函数、参数和函数调用。
     * Verifies that the binding resolver binds functions, parameters, and function calls after modifier expansion.
     */
    @Test
    fun `binding resolver binds function declarations and calls`() {
        val result = resolveExpanded("fn add(a: Int, b: Int) { val c = a + b; c } add(1, 2)")

        assertFalse(result.hasErrors)
        val program = requireNotNull(result.program)
        val function = program.functions.single()
        assertEquals("add", function.name)
        assertEquals(listOf("a", "b"), function.parameters.map { it.name })
        assertEquals("binary(+, variable(a -> a), variable(b -> b))", function.body.declarations.single().initializer.render())
        assertEquals("call(add, int64, int64)", program.expression.render())
        assertEquals(listOf("add"), program.symbols.map { it.name })
    }

    /**
     * 验证所有当前 bound expression 节点的 span 都委托给原始 syntax。
     * Verifies that every current bound expression node delegates span to original syntax.
     */
    @Test
    fun `bound expression spans delegate to syntax spans`() {
        val program = requireNotNull(resolve("val x = 1; -(x + (x))").program)
        val integer = assertIs<BoundInteger>(program.declarations.single().initializer)
        val prefix = assertIs<BoundPrefix>(program.expression)
        val outerGrouped = assertIs<BoundGrouped>(prefix.operand)
        val binary = assertIs<BoundBinary>(outerGrouped.inner)
        val variable = assertIs<BoundVariable>(binary.left)
        val innerGrouped = assertIs<BoundGrouped>(binary.right)
        val string = assertIs<BoundString>(requireNotNull(resolve("\"hello\"").program).expression)
        val print = assertIs<BoundPrintCall>(requireNotNull(resolve("print(\"hello\")").program).expression)
        val call = assertIs<BoundFunctionCall>(requireNotNull(resolveExpanded("fn one() { 1 } one()").program).expression)
        val missing = BoundMissing(MissingExpression(SourceSpan("sample.kk", 0, 0)))
        val nodes: List<BoundExpression> = listOf(
            integer,
            string,
            print,
            call,
            prefix,
            outerGrouped,
            binary,
            variable,
            innerGrouped,
            missing,
        )

        for (node in nodes) {
            assertEquals(node.syntax.span, node.span)
        }
    }

    /**
     * 验证 self、later 和完全未绑定的 identifier 都被拒绝。
     * Verifies that self, later, and completely unbound identifiers are rejected.
     */
    @Test
    fun `binding resolver rejects unresolved identifier order`() {
        assertDiagnosticCodes("val x = x; 1", "TYPE001")
        assertDiagnosticCodes("val x = y; val y = 1; x", "TYPE001", "TYPE001")
        assertDiagnosticCodes("name", "TYPE001")
        assertDiagnosticCodes("-name", "TYPE001")
        assertDiagnosticCodes("(name)", "TYPE001")
        assertDiagnosticCodes("left + right", "TYPE001", "TYPE001")
        assertDiagnosticCodes("val x = 1; x + missing", "TYPE001")
        assertDiagnosticCodes("val x = 1; missing + x", "TYPE001")
        assertDiagnosticCodes("unknown(1)", "TYPE001")
        assertDiagnosticCodes("print(missing)", "TYPE001")
        assertExpandedDiagnosticCodes("fn self() { self() } self()", "TYPE001", "TYPE001")
        assertExpandedDiagnosticCodes("fn first() { later() } fn later() { 1 } first()", "TYPE001", "TYPE001")
    }

    /**
     * 验证重复 val declaration 会产生 BIND001 且不返回 BoundProgram。
     * Verifies that duplicate val declarations produce BIND001 and return no BoundProgram.
     */
    @Test
    fun `binding resolver rejects duplicate immutable vals`() {
        val result = resolve("val x = 1; val x = 2; x")

        assertTrue(result.hasErrors)
        assertNull(result.program)
        assertEquals(listOf("BIND001"), result.diagnostics.map { it.code })
    }

    /**
     * 验证重复函数和重复参数名都会产生 BIND001。
     * Verifies that duplicate functions and duplicate parameter names both produce BIND001.
     */
    @Test
    fun `binding resolver rejects duplicate functions and parameters`() {
        assertExpandedDiagnosticCodes("fn same() { 1 } fn same() { 2 } same()", "BIND001")
        assertExpandedDiagnosticCodes("fn bad(a: Int, a: Int) { a } 0", "BIND001")
    }

    /**
     * 验证未展开的 modifier 节点不能进入 binding 阶段。
     * Verifies that unexpanded modifier nodes cannot enter the binding phase.
     */
    @Test
    fun `binding resolver rejects unexpanded modifier nodes`() {
        val modifierResult = resolve("modifier fn { [modifiers] fn [identifier]([identifier:type?]) { [body] } } 0")
        val rawResult = resolve("decorated thing { 1 } 0")

        assertTrue(modifierResult.hasErrors)
        assertEquals(listOf("BIND001"), modifierResult.diagnostics.map { it.code })
        assertTrue(rawResult.hasErrors)
        assertEquals(listOf("BIND001"), rawResult.diagnostics.map { it.code })
    }

    /**
     * 验证函数体内局部 val initializer 失败会阻止函数绑定。
     * Verifies that a failing local val initializer inside a function body blocks function binding.
     */
    @Test
    fun `binding resolver reports function local declaration failures`() {
        assertExpandedDiagnosticCodes("fn bad(a: Int) { val x = missing; 1 } 0", "TYPE001")
    }

    /**
     * 验证 parser 恢复产生的 missing expression 由后续类型检查处理。
     * Verifies that missing expressions produced by parser recovery are left to later type checking.
     */
    @Test
    fun `binding resolver leaves missing expressions for type checking`() {
        val result = SeedBindingResolver().resolve(
            AstProgram(MissingExpression(SourceSpan("sample.kk", 0, 0))),
        )

        assertFalse(result.hasErrors)
        val expression = assertIs<BoundMissing>(requireNotNull(result.program).expression)
        assertEquals(SourceSpan("sample.kk", 0, 0), result.program.span)
        assertEquals(SourceSpan("sample.kk", 0, 0), expression.span)
    }

    /**
     * 解析并 binding 一个测试 program。
     * Parses and binds one test program.
     */
    private fun resolve(text: String): BindingResult =
        SeedBindingResolver().resolve(parseProgram(text))

    /**
     * 先执行 modifier expansion，再 binding 一个测试 program。
     * Runs modifier expansion first, then binds one test program.
     */
    private fun resolveExpanded(text: String): BindingResult =
        SeedBindingResolver().resolve(expandProgram(text))

    /**
     * 断言 binding 失败并产生指定 diagnostics。
     * Asserts that binding fails with the specified diagnostics.
     */
    private fun assertDiagnosticCodes(text: String, vararg codes: String) {
        val result = resolve(text)

        assertTrue(result.hasErrors)
        assertNull(result.program)
        assertEquals(codes.toList(), result.diagnostics.map { it.code })
    }

    /**
     * 断言 expansion 后的 binding 失败并产生指定 diagnostics。
     * Asserts that binding after expansion fails with the specified diagnostics.
     */
    private fun assertExpandedDiagnosticCodes(text: String, vararg codes: String) {
        val result = resolveExpanded(text)

        assertTrue(result.hasErrors)
        assertNull(result.program)
        assertEquals(codes.toList(), result.diagnostics.map { it.code })
    }

    /**
     * 使用默认 lexer/parser 解析一个 program。
     * Parses one program with the default lexer and parser.
     */
    private fun parseProgram(text: String): AstProgram =
        Parser(Lexer().tokenize(SourceText.of("sample.kk", text)).tokens)
            .parseProgramDocument()
            .program

    /**
     * 使用默认 parser 和 modifier expander 构造 expanded AST program。
     * Builds an expanded AST program with the default parser and modifier expander.
     */
    private fun expandProgram(text: String): AstProgram =
        requireNotNull(SeedModifierExpander().expand(parseProgram(text)).program)
}

/**
 * 将 bound AST 渲染为稳定的测试断言字符串。
 * Renders bound AST into a stable assertion string for tests.
 */
private fun BoundExpression.render(): String =
    when (this) {
        is BoundInteger -> "int64"
        is BoundString -> "string(${syntax.text})"
        is BoundPrintCall -> "print(${argument.render()})"
        is BoundFunctionCall -> "call(${symbol.name}, ${arguments.joinToString { it.render() }})"
        is BoundVariable -> "variable(${syntax.name} -> ${symbol.name})"
        is BoundGrouped -> "grouped(${inner.render()})"
        is BoundPrefix -> "prefix(${syntax.operator.lexeme}, ${operand.render()})"
        is BoundBinary -> "binary(${syntax.operator.lexeme}, ${left.render()}, ${right.render()})"
        is BoundMissing -> "<missing>"
    }
