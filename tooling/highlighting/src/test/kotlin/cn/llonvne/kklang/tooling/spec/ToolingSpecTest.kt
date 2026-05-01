package cn.llonvne.kklang.tooling.spec

import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 覆盖 tooling Kotlin DSL 规范和 Markdown 规范的一致性。
 * Covers consistency between the tooling Kotlin DSL spec and Markdown spec.
 */
class ToolingSpecTest {
    /**
     * 验证 DSL 记录当前 `.kk` 文件扩展名。
     * Verifies that the DSL records the current `.kk` file extension.
     */
    @Test
    fun `kotlin dsl spec records kk file extension`() {
        assertEquals("kk", toolingLanguageSpec.fileExtension)
    }

    /**
     * 验证 DSL 记录共享高亮分类表面。
     * Verifies that the DSL records the shared highlighting category surface.
     */
    @Test
    fun `kotlin dsl spec records highlight categories`() {
        assertEquals(
            listOf("keyword", "identifier", "integer", "operator", "delimiter", "whitespace", "unknown", "eof"),
            toolingLanguageSpec.highlightCategories.map { it.category },
        )
        assertEquals(
            listOf("plus", "minus", "star", "slash", "equals"),
            toolingLanguageSpec.highlightCategories.single { it.category == "operator" }.tokenKinds,
        )
    }

    /**
     * 验证 Markdown tooling 规范包含 DSL 中的 feature 名称。
     * Verifies that the Markdown tooling spec contains feature names from the DSL.
     */
    @Test
    fun `markdown spec contains tooling features from dsl spec`() {
        val markdown = Path("../../spec/tooling.md").readText()

        assertTrue(markdown.contains(".kk"), "missing .kk extension in Markdown spec")
        for (feature in toolingLanguageSpec.lspFeatures + toolingLanguageSpec.ideaFeatures) {
            assertTrue(markdown.contains(feature), "missing $feature in Markdown spec")
        }
    }
}
