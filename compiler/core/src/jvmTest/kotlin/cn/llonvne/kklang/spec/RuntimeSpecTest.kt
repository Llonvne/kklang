package cn.llonvne.kklang.spec

import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 覆盖 runtime Kotlin DSL 规范和 Markdown 规范的一致性。
 * Covers consistency between the runtime Kotlin DSL spec and Markdown spec.
 */
class RuntimeSpecTest {
    /**
     * 验证 runtime DSL 记录 C status 表面。
     * Verifies that the runtime DSL records the C status surface.
     */
    @Test
    fun `runtime dsl spec records status surface`() {
        assertEquals(
            listOf("KK_OK", "KK_ERR_OOM", "KK_ERR_INVALID_ARGUMENT"),
            minimalRuntimeSpec.statuses.map { it.name },
        )
    }

    /**
     * 验证 runtime DSL 记录最小 C ABI 函数表面。
     * Verifies that the runtime DSL records the minimal C ABI function surface.
     */
    @Test
    fun `runtime dsl spec records minimal c abi surface`() {
        assertEquals(
            listOf(
                "kk_runtime_create",
                "kk_runtime_destroy",
                "kk_string_new",
                "kk_string_size",
                "kk_string_data",
                "kk_string_release",
            ),
            minimalRuntimeSpec.abiFunctions.map { it.name },
        )
    }

    /**
     * 验证 runtime DSL 记录第一批 value tag。
     * Verifies that the runtime DSL records the first value tags.
     */
    @Test
    fun `runtime dsl spec records first value tags`() {
        assertEquals(
            listOf(
                "KK_VALUE_UNIT",
                "KK_VALUE_BOOL",
                "KK_VALUE_INT64",
                "KK_VALUE_STRING",
                "KK_VALUE_OBJECT_REF",
            ),
            minimalRuntimeSpec.valueTags.map { it.name },
        )
    }

    /**
     * 验证 Markdown runtime 规范包含 DSL 中的 status、函数、value tag、wrapper 和调试规则。
     * Verifies that the Markdown runtime spec contains statuses, functions, value tags, wrappers, and debug rules from the DSL.
     */
    @Test
    fun `markdown runtime spec contains dsl status functions and value tags`() {
        val markdown = Path("../../spec/runtime.md").readText()
        val expectedTerms = minimalRuntimeSpec.statuses.map { it.name } +
            minimalRuntimeSpec.abiFunctions.map { it.name } +
            minimalRuntimeSpec.valueTags.map { it.name } +
            minimalRuntimeSpec.wrapperTypes.map { it.name } +
            minimalRuntimeSpec.debugRules

        for (term in expectedTerms) {
            assertTrue(markdown.contains(term), "missing $term in Markdown runtime spec")
        }
    }
}
