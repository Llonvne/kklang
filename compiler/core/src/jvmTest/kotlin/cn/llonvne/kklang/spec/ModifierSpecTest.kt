package cn.llonvne.kklang.spec

import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 覆盖 modifier Kotlin DSL 规范和 Markdown 规范的一致性。
 * Covers consistency between the modifier Kotlin DSL spec and Markdown spec.
 */
class ModifierSpecTest {
    /**
     * 验证 modifier DSL 记录第一版元编程语法表面。
     * Verifies that the modifier DSL records the first metaprogramming syntax surface.
     */
    @Test
    fun `modifier dsl spec records syntax surface`() {
        assertEquals(
            listOf("modifier declaration", "fn modifier", "RawModifierApplication", "FunctionDeclaration"),
            minimalModifierSpec.syntaxes,
        )
    }

    /**
     * 验证 modifier DSL 记录 expansion 和函数语义规则。
     * Verifies that the modifier DSL records expansion and function semantic rules.
     */
    @Test
    fun `modifier dsl spec records expansion and function rules`() {
        assertEquals(
            listOf(
                "declarative modifier expansion",
                "modifier expansion runs before binding",
                "parameter type syntax is optional",
            ),
            minimalModifierSpec.expansionRules,
        )
        assertEquals(
            listOf(
                "top-level named function",
                "functions are not first-class values",
                "function declarations bind in source order",
                "recursion and forward reference are forbidden",
                "function return type is inferred from body",
            ),
            minimalModifierSpec.functionRules,
        )
    }

    /**
     * 验证 Markdown modifier 规范包含 DSL 公开表面。
     * Verifies that the Markdown modifier spec contains the DSL surface.
     */
    @Test
    fun `markdown modifier spec contains dsl surface`() {
        val markdown = Path("../../spec/modifier.md").readText()
        val expectedTerms = minimalModifierSpec.syntaxes +
            minimalModifierSpec.expansionRules +
            minimalModifierSpec.functionRules +
            minimalModifierSpec.diagnostics.map { it.code }

        for (term in expectedTerms) {
            assertTrue(markdown.contains(term), "missing $term in Markdown modifier spec")
        }
    }
}
