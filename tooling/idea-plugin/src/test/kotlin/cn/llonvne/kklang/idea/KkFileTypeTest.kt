package cn.llonvne.kklang.idea

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * 覆盖 IDEA `.kk` 文件类型注册对象。
 * Covers the IDEA `.kk` file type registration object.
 */
class KkFileTypeTest {
    /**
     * 验证文件类型暴露 `kklang` 语言和 `.kk` 扩展名。
     * Verifies that the file type exposes the `kklang` language and `.kk` extension.
     */
    @Test
    fun `file type exposes kk extension`() {
        assertEquals("kklang", KkFileType.name)
        assertEquals("kklang source file", KkFileType.description)
        assertEquals("kk", KkFileType.defaultExtension)
        assertSame(KkLanguage, KkFileType.language)
        assertNull(KkFileType.icon)
    }
}
