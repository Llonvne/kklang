package cn.llonvne.kklang.idea

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

/**
 * IDEA PSI 根元素类型，给 daemon annotator 提供 `.kk` 文件入口。
 * IDEA PSI root element type that gives daemon annotators an entry point for `.kk` files.
 */
object KkElementTypes {
    val FILE: IFileElementType = IFileElementType("KK_FILE", KkLanguage)
}

/**
 * `.kk` 文件的最小 PSI file。
 * Minimal PSI file for `.kk` files.
 */
class KkPsiFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, KkLanguage) {
    /**
     * 返回 `.kk` 文件类型。
     * Returns the `.kk` file type.
     */
    override fun getFileType(): KkFileType = KkFileType

    /**
     * 返回调试用文件描述。
     * Returns the debug file description.
     */
    override fun toString(): String = "kklang file"
}

/**
 * IDEA parser definition，只建立最小 PSI 壳，语言 diagnostics 仍由 compiler pipeline 负责。
 * IDEA parser definition that builds only a minimal PSI shell while language diagnostics remain owned by the compiler pipeline.
 */
class KkParserDefinition : ParserDefinition {
    /**
     * 创建复用共享分类器的 IDEA lexer。
     * Creates the IDEA lexer that reuses the shared classifier.
     */
    override fun createLexer(project: Project?): Lexer = KkIdeaLexer()

    /**
     * 创建消费所有 token 的最小 parser。
     * Creates the minimal parser that consumes every token.
     */
    override fun createParser(project: Project?): PsiParser = KkPsiParser()

    /**
     * 返回 `.kk` 文件根节点类型。
     * Returns the `.kk` file root node type.
     */
    override fun getFileNodeType(): IFileElementType = KkElementTypes.FILE

    /**
     * 返回 whitespace token 集合。
     * Returns the whitespace token set.
     */
    override fun getWhitespaceTokens(): TokenSet = TokenSet.create(KkTokenTypes.WHITESPACE)

    /**
     * 当前语言还没有 comment token。
     * The current language has no comment tokens yet.
     */
    override fun getCommentTokens(): TokenSet = TokenSet.EMPTY

    /**
     * 当前语言还没有 string literal token。
     * The current language has no string-literal tokens yet.
     */
    override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY

    /**
     * 为普通 AST node 创建 wrapper PSI element。
     * Creates a wrapper PSI element for a regular AST node.
     */
    override fun createElement(node: ASTNode): PsiElement = ASTWrapperPsiElement(node)

    /**
     * 创建 `.kk` PSI file。
     * Creates a `.kk` PSI file.
     */
    override fun createFile(viewProvider: FileViewProvider): PsiFile = KkPsiFile(viewProvider)
}

/**
 * 不定义语法语义的最小 IDEA parser，只负责让 PSI tree 覆盖整份文件。
 * Minimal IDEA parser that defines no syntax semantics and only makes the PSI tree cover the whole file.
 */
class KkPsiParser : PsiParser {
    /**
     * 消费所有 lexer token 并完成根节点。
     * Consumes every lexer token and completes the root node.
     */
    override fun parse(root: com.intellij.psi.tree.IElementType, builder: PsiBuilder): ASTNode {
        val marker = builder.mark()
        while (!builder.eof()) {
            builder.advanceLexer()
        }
        marker.done(root)
        return builder.treeBuilt
    }
}
