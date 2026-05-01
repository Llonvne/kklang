package cn.llonvne.kklang.idea

import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * IDEA extension point factory for `.kk` syntax highlighting.
 * `.kk` syntax highlighting 的 IDEA extension point factory。
 */
class KkSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    /**
     * 返回 `.kk` syntax highlighter。
     * Returns the `.kk` syntax highlighter.
     */
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter =
        KkSyntaxHighlighter()
}
