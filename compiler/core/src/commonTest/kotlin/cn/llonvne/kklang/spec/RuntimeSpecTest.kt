package cn.llonvne.kklang.spec

import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuntimeSpecTest {
    @Test
    fun `runtime dsl spec records status surface`() {
        assertEquals(
            listOf("KK_OK", "KK_ERR_OOM", "KK_ERR_INVALID_ARGUMENT"),
            minimalRuntimeSpec.statuses.map { it.name },
        )
    }

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

    @Test
    fun `markdown runtime spec contains dsl status functions and value tags`() {
        val markdown = Path("../../spec/runtime.md").readText()
        val expectedTerms = minimalRuntimeSpec.statuses.map { it.name } +
            minimalRuntimeSpec.abiFunctions.map { it.name } +
            minimalRuntimeSpec.valueTags.map { it.name } +
            minimalRuntimeSpec.wrapperTypes.map { it.name }

        for (term in expectedTerms) {
            assertTrue(markdown.contains(term), "missing $term in Markdown runtime spec")
        }
    }
}
