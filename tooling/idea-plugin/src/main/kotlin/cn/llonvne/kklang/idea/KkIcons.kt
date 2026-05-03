package cn.llonvne.kklang.idea

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * IDEA 插件使用的 kklang 图标集合。
 * kklang icon set used by the IDEA plugin.
 */
object KkIcons {
    /**
     * `.kk` 文件类型和 kklang 单文件运行配置共享的语言图标。
     * Shared language icon for `.kk` file types and kklang single-file run configurations.
     */
    val Language: Icon = IconLoader.getIcon("/icons/kklang.png", KkIcons::class.java)
}
