package cn.llonvne.kklang.idea

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

/**
 * IDEA 中的 `.kk` 文件类型。
 * IDEA file type for `.kk` files.
 */
object KkFileType : LanguageFileType(KkLanguage) {
    /**
     * 返回文件类型名称。
     * Returns the file type name.
     */
    override fun getName(): String = "kklang"

    /**
     * 返回文件类型描述。
     * Returns the file type description.
     */
    override fun getDescription(): String = "kklang source file"

    /**
     * 返回默认文件扩展名。
     * Returns the default file extension.
     */
    override fun getDefaultExtension(): String = "kk"

    /**
     * 第一版插件不提供自定义 icon。
     * The first plugin version does not provide a custom icon.
     */
    override fun getIcon(): Icon? = null
}
