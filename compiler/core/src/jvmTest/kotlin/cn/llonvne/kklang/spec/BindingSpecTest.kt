package cn.llonvne.kklang.spec

import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 覆盖 binding Kotlin DSL 规范和 Markdown 规范的一致性。
 * Covers consistency between the binding Kotlin DSL spec and Markdown spec.
 */
class BindingSpecTest {
    /**
     * 验证 binding DSL 记录 val 语法。
     * Verifies that the binding DSL records val syntax.
     */
    @Test
    fun `binding dsl spec records val syntax`() {
        assertEquals(
            listOf("val declaration", "val identifier = expression;", "function declaration", "builtin print call"),
            minimalBindingSpec.syntaxes,
        )
    }

    /**
     * 验证 binding DSL 记录 bound AST 节点。
     * Verifies that the binding DSL records bound AST nodes.
     */
    @Test
    fun `binding dsl spec records bound ast nodes`() {
        assertEquals(
            listOf(
                "BoundProgram",
                "BoundValDeclaration",
                "BoundFunctionDeclaration",
                "BoundFunctionParameter",
                "BoundFunctionCall",
                "BindingScope",
                "BindingSymbol",
                "BoundInteger",
                "BoundString",
                "BoundPrintCall",
                "BoundVariable",
                "BoundGrouped",
                "BoundPrefix",
                "BoundBinary",
                "BoundMissing",
            ),
            minimalBindingSpec.boundNodes,
        )
    }

    /**
     * 验证 binding DSL 记录不可变和作用域规则。
     * Verifies that the binding DSL records immutability and scope rules.
     */
    @Test
    fun `binding dsl spec records immutable scope rules`() {
        assertEquals(
            listOf(
                "binding resolver runs before type checking",
                "binding resolver emits BoundProgram",
                "binding resolver emits BoundExpression",
                "BoundVariable carries BindingSymbol",
                "BindingScope preserves declaration order",
                "BindingScope resolves only defined symbols",
                "val bindings are immutable",
                "initializer can reference earlier vals",
                "initializer cannot reference itself or later vals",
                "unresolved identifiers are rejected",
                "builtin print call",
                "same-scope duplicate val is rejected",
                "function declarations bind in source order",
                "duplicate function parameter is rejected",
                "assignment expression is not part of the grammar",
            ),
            minimalBindingSpec.scopeRules,
        )
    }

    /**
     * 验证 Markdown binding 规范包含 DSL 公开表面。
     * Verifies that the Markdown binding spec contains the DSL surface.
     */
    @Test
    fun `markdown binding spec contains dsl surface`() {
        val markdown = Path("../../spec/binding.md").readText()
        val expectedTerms = minimalBindingSpec.syntaxes +
            minimalBindingSpec.boundNodes +
            minimalBindingSpec.scopeRules +
            minimalBindingSpec.diagnostics.map { it.code }

        for (term in expectedTerms) {
            assertTrue(markdown.contains(term), "missing $term in Markdown binding spec")
        }
    }
}
