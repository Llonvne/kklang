package cn.llonvne.kklang.idea

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.lang.FileASTNode
import com.intellij.lang.Language
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.PsiBuilder
import com.intellij.mock.MockApplication
import com.intellij.mock.MockPsiManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.AbstractFileViewProvider
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.tree.TokenSet
import com.intellij.testFramework.LightVirtualFile
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 覆盖 `.kk` 最小 ParserDefinition 和 PSI 壳。
 * Covers the minimal `.kk` ParserDefinition and PSI shell.
 */
class KkParserDefinitionTest {
    /**
     * 验证 parser definition 暴露 language、lexer 和 token sets。
     * Verifies that the parser definition exposes language, lexer, and token sets.
     */
    @Test
    fun `parser definition exposes kk language pieces`() {
        val definition = KkParserDefinition()
        val lexer = definition.createLexer(null)
        val parser = definition.createParser(null)
        val element = definition.createElement(RecordingPsiBuilder(tokenCount = 0).astNode)

        assertTrue(lexer is KkIdeaLexer)
        assertTrue(parser is KkPsiParser)
        assertTrue(element is ASTWrapperPsiElement)
        assertSame(KkElementTypes.FILE, definition.fileNodeType)
        assertTrue(definition.whitespaceTokens.contains(KkTokenTypes.WHITESPACE))
        assertSame(TokenSet.EMPTY, definition.commentTokens)
        assertTrue(definition.stringLiteralElements.contains(KkTokenTypes.STRING))
    }

    /**
     * 验证 parser 消费全部 token 并完成根节点。
     * Verifies that the parser consumes all tokens and completes the root node.
     */
    @Test
    fun `psi parser consumes all tokens`() {
        val builder = RecordingPsiBuilder(tokenCount = 2)
        val ast = KkPsiParser().parse(KkElementTypes.FILE, builder.proxy())

        assertEquals(2, builder.advanceCount)
        assertSame(KkElementTypes.FILE, builder.doneElementType)
        assertSame(builder.astNode, ast)
    }

    /**
     * 验证 parser 对空文件也完成根节点。
     * Verifies that the parser also completes the root node for an empty file.
     */
    @Test
    fun `psi parser completes empty file`() {
        val builder = RecordingPsiBuilder(tokenCount = 0)

        KkPsiParser().parse(KkElementTypes.FILE, builder.proxy())

        assertEquals(0, builder.advanceCount)
        assertSame(KkElementTypes.FILE, builder.doneElementType)
    }

    /**
     * 验证 PSI file 暴露 `.kk` 文件类型和描述。
     * Verifies that the PSI file exposes the `.kk` file type and description.
     */
    @Test
    fun `psi file exposes kk file type`() {
        val application = MockApplication.setUp(Disposable {})
        val definition = KkParserDefinition()
        LanguageParserDefinitions.INSTANCE.addExplicitExtension(KkLanguage, definition)

        try {
            val file = definition.createFile(viewProvider()) as KkPsiFile

            assertSame(KkFileType, file.fileType)
            assertEquals("kklang file", file.toString())
        } finally {
            LanguageParserDefinitions.INSTANCE.removeExplicitExtension(KkLanguage, definition)
            application.dispose()
        }
    }

    /**
     * 构造测试用 FileViewProvider proxy。
     * Builds a FileViewProvider for tests.
     */
    private fun viewProvider(): FileViewProvider = TestFileViewProvider()

    /**
     * 满足 PsiFileBase 构造约束的最小测试 FileViewProvider。
     * Minimal test FileViewProvider that satisfies PsiFileBase construction constraints.
     */
    private class TestFileViewProvider :
        AbstractFileViewProvider(
            MockPsiManager(project()),
            LightVirtualFile("main.kk", KkFileType, ""),
            false,
        ) {
        /**
         * 返回 `.kk` 基础语言。
         * Returns the `.kk` base language.
         */
        override fun getBaseLanguage(): Language = KkLanguage

        /**
         * 返回当前文件包含的语言集合。
         * Returns the language set contained by the current file.
         */
        override fun getLanguages(): Set<Language> = setOf(KkLanguage)

        /**
         * 测试夹具不缓存 PSI。
         * The test fixture does not cache PSI.
         */
        override fun getPsiInner(target: Language): PsiFile? = null

        /**
         * 测试夹具不提供 cached PSI。
         * The test fixture exposes no cached PSI.
         */
        override fun getCachedPsi(target: Language): PsiFile? = null

        /**
         * 测试夹具不暴露 PSI file 列表。
         * The test fixture exposes no PSI file list.
         */
        override fun getAllFiles(): List<PsiFile> = emptyList()

        /**
         * 测试夹具没有 cached PSI 文件。
         * The test fixture has no cached PSI files.
         */
        override fun getCachedPsiFiles(): List<PsiFile> = emptyList()

        /**
         * 测试夹具没有已知 AST 根。
         * The test fixture has no known AST roots.
         */
        override fun getKnownTreeRoots(): List<FileASTNode> = emptyList()

        /**
         * 创建同样最小的 copy provider。
         * Creates the same minimal copy provider.
         */
        override fun createCopy(copy: VirtualFile): FileViewProvider = TestFileViewProvider()

        /**
         * 测试夹具不支持按 offset 查找 PSI element。
         * The test fixture does not support offset-based PSI element lookup.
         */
        override fun findElementAt(offset: Int): PsiElement? = null

        /**
         * 测试夹具不支持按 offset 和语言类查找 PSI element。
         * The test fixture does not support offset and language-class based PSI element lookup.
         */
        override fun findElementAt(offset: Int, lang: Class<out Language>): PsiElement? = null

        /**
         * 测试夹具不支持按 offset 查找引用。
         * The test fixture does not support offset-based reference lookup.
         */
        override fun findReferenceAt(offset: Int): PsiReference? = null
    }

    private companion object {
        /**
         * 构造测试用 Project proxy。
         * Builds a Project proxy for tests.
         */
        fun project(): Project =
            Proxy.newProxyInstance(
                Project::class.java.classLoader,
                arrayOf(Project::class.java),
            ) { _, method, _ ->
                when (method.name) {
                    "getName" -> "kklang-test"
                    "isOpen" -> true
                    "isInitialized" -> true
                    "isDefault" -> false
                    "isDisposed" -> false
                    "getBasePath" -> null
                    "getBaseDir" -> null
                    "getProjectFile" -> null
                    "getProjectFilePath" -> null
                    "getWorkspaceFile" -> null
                    "getLocationHash" -> "kklang-test"
                    "save" -> Unit
                    "toString" -> "KkProject"
                    else -> null
                }
            } as Project
    }

    /**
     * 记录 parser 对 PsiBuilder 的调用。
     * Records parser calls to PsiBuilder.
     */
    private class RecordingPsiBuilder(private val tokenCount: Int) {
        var advanceCount = 0
        var doneElementType: com.intellij.psi.tree.IElementType? = null
        val astNode: ASTNode = astNode()

        /**
         * 构造测试用 PsiBuilder proxy。
         * Builds a PsiBuilder proxy for tests.
         */
        fun proxy(): PsiBuilder =
            Proxy.newProxyInstance(
                PsiBuilder::class.java.classLoader,
                arrayOf(PsiBuilder::class.java),
            ) { _, method, _ ->
                when (method.name) {
                    "mark" -> marker()
                    "eof" -> advanceCount >= tokenCount
                    "advanceLexer" -> {
                        advanceCount += 1
                        Unit
                    }
                    "getTreeBuilt" -> astNode
                    "toString" -> "RecordingPsiBuilder"
                    else -> null
                }
            } as PsiBuilder

        /**
         * 构造测试用 marker proxy。
         * Builds a marker proxy for tests.
         */
        private fun marker(): PsiBuilder.Marker =
            Proxy.newProxyInstance(
                PsiBuilder.Marker::class.java.classLoader,
                arrayOf(PsiBuilder.Marker::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "done" -> {
                        doneElementType = args?.singleOrNull() as com.intellij.psi.tree.IElementType
                        Unit
                    }
                    "toString" -> "RecordingMarker"
                    else -> null
                }
            } as PsiBuilder.Marker

        /**
         * 构造测试用 ASTNode proxy。
         * Builds an ASTNode proxy for tests.
         */
        private fun astNode(): ASTNode =
            Proxy.newProxyInstance(
                ASTNode::class.java.classLoader,
                arrayOf(ASTNode::class.java),
            ) { _, method, _ ->
                when (method.name) {
                    "toString" -> "RecordingAstNode"
                    else -> null
                }
            } as ASTNode
    }
}
