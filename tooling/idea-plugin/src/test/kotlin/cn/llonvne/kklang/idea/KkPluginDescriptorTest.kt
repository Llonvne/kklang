package cn.llonvne.kklang.idea

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 覆盖 IDEA plugin descriptor 关键注册项。
 * Covers key IDEA plugin descriptor registrations.
 */
class KkPluginDescriptorTest {
    /**
     * 验证 plugin.xml 注册 `.kk` 文件类型和 syntax highlighter。
     * Verifies that plugin.xml registers the `.kk` file type and syntax highlighter.
     */
    @Test
    fun `plugin descriptor registers kk support`() {
        val descriptor = javaClass.classLoader.getResource("META-INF/plugin.xml")
        assertNotNull(descriptor)

        val text = descriptor.readText()
        assertTrue(text.contains("extensions=\"kk\""))
        assertTrue(text.contains("cn.llonvne.kklang.idea.KkFileType"))
        assertTrue(text.contains("cn.llonvne.kklang.idea.KkParserDefinition"))
        assertTrue(text.contains("cn.llonvne.kklang.idea.KkSyntaxHighlighterFactory"))
        assertTrue(text.contains("cn.llonvne.kklang.idea.KkDiagnosticExternalAnnotator"))
    }
}
