package cn.llonvne.kklang.binding

import cn.llonvne.kklang.frontend.SourceText
import cn.llonvne.kklang.frontend.lexing.Lexer
import cn.llonvne.kklang.frontend.parsing.Parser
import cn.llonvne.kklang.frontend.parsing.ValDeclaration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 覆盖 binding 作用域符号表的定义顺序、重复定义和解析规则。
 * Covers binding-scope symbol-table definition order, duplicate definition, and resolution rules.
 */
class BindingScopeTest {
    /**
     * 验证 BindingScope 按 declaration 顺序保存成功定义的符号。
     * Verifies that BindingScope preserves successfully defined symbols in declaration order.
     */
    @Test
    fun `binding scope preserves declaration order`() {
        val scope = BindingScope.empty()
        val declarations = declarations("val x = 1; val y = 2; 0")
        val x = BindingSymbol("x", declarations[0])
        val y = BindingSymbol("y", declarations[1])

        assertTrue(scope.define(x))
        assertTrue(scope.define(y))

        assertEquals(listOf("x", "y"), scope.symbols.map { it.name })
        assertSame(x, scope.resolve("x"))
        assertSame(y, scope.resolve("y"))
    }

    /**
     * 验证 BindingScope 拒绝同一作用域重复定义且不会替换已有符号。
     * Verifies that BindingScope rejects same-scope duplicates without replacing the existing symbol.
     */
    @Test
    fun `binding scope rejects duplicates without replacing existing symbol`() {
        val scope = BindingScope.empty()
        val declarations = declarations("val x = 1; val x = 2; 0")
        val first = BindingSymbol("x", declarations[0])
        val second = BindingSymbol("x", declarations[1])

        assertTrue(scope.define(first))
        assertFalse(scope.define(second))

        assertEquals(listOf("x"), scope.symbols.map { it.name })
        assertSame(first, scope.resolve("x"))
    }

    /**
     * 验证 BindingScope 只解析已经定义的符号。
     * Verifies that BindingScope resolves only already-defined symbols.
     */
    @Test
    fun `binding scope resolves only defined symbols`() {
        val scope = BindingScope.empty()
        val declaration = declarations("val x = 1; 0").single()
        val symbol = BindingSymbol("x", declaration)

        assertNull(scope.resolve("x"))
        assertTrue(scope.define(symbol))
        assertSame(symbol, scope.resolve("x"))
        assertNull(scope.resolve("missing"))
    }

    /**
     * 解析测试源码中的 val declarations。
     * Parses val declarations from a test source snippet.
     */
    private fun declarations(text: String): List<ValDeclaration> =
        Parser(Lexer().tokenize(SourceText.of("sample.kk", text)).tokens)
            .parseProgramDocument()
            .program
            .declarations
}
