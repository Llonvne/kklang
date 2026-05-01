package cn.llonvne.kklang.spec

import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 覆盖 type-system Kotlin DSL 规范和 Markdown 规范的一致性。
 * Covers consistency between the type-system Kotlin DSL spec and Markdown spec.
 */
class TypeSystemSpecTest {
    /**
     * 验证 type-system DSL 记录阶段和类型表面。
     * Verifies that the type-system DSL records the phase and type surface.
     */
    @Test
    fun `type system dsl spec records phase and type surface`() {
        assertEquals(listOf("type checking"), minimalTypeSystemSpec.phases)
        assertEquals(listOf("TypeRef.Int64"), minimalTypeSystemSpec.types)
    }

    /**
     * 验证 type-system DSL 记录 typed AST 节点。
     * Verifies that the type-system DSL records typed AST nodes.
     */
    @Test
    fun `type system dsl spec records typed ast nodes`() {
        assertEquals(
            listOf(
                "TypedProgram",
                "TypedValDeclaration",
                "TypedExpression",
                "TypedInteger",
                "TypedVariable",
                "TypedGrouped",
                "TypedPrefix",
                "TypedBinary",
            ),
            minimalTypeSystemSpec.typedNodes,
        )
    }

    /**
     * 验证 type-system DSL 记录当前支持的源码形式。
     * Verifies that the type-system DSL records the currently supported source forms.
     */
    @Test
    fun `type system dsl spec records supported forms`() {
        assertEquals(
            listOf(
                "val declaration",
                "identifier reference",
                "integer literal",
                "grouped expression",
                "unary plus",
                "unary minus",
                "binary plus",
                "binary minus",
                "binary multiply",
                "binary divide",
            ),
            minimalTypeSystemSpec.supportedForms,
        )
    }

    /**
     * 验证 type-system DSL 记录绑定规则。
     * Verifies that the type-system DSL records binding rules.
     */
    @Test
    fun `type system dsl spec records binding rules`() {
        assertEquals(
            listOf(
                "val declaration binds initializer type",
                "identifier reference uses bound val type",
                "TypedValDeclaration preserves BindingSymbol",
                "TypedVariable preserves BindingSymbol",
            ),
            minimalTypeSystemSpec.bindingRules,
        )
    }

    /**
     * 验证 Markdown type-system 规范包含 DSL 中的类型、typed 节点和 diagnostics。
     * Verifies that the Markdown type-system spec contains types, typed nodes, and diagnostics from the DSL.
     */
    @Test
    fun `markdown type system spec contains dsl surface`() {
        val markdown = Path("../../spec/type-system.md").readText()
        val expectedTerms = minimalTypeSystemSpec.types +
            minimalTypeSystemSpec.typedNodes +
            minimalTypeSystemSpec.bindingRules +
            minimalTypeSystemSpec.diagnostics.map { it.code }

        for (term in expectedTerms) {
            assertTrue(markdown.contains(term), "missing $term in Markdown type-system spec")
        }
    }
}
