package cn.llonvne.kklang.idea

import cn.llonvne.kklang.compiler.CompilationInput
import cn.llonvne.kklang.compiler.CompilationResult
import cn.llonvne.kklang.compiler.CompilerPipeline
import cn.llonvne.kklang.frontend.SourceText
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

/**
 * IDEA diagnostic annotator 输入源码快照。
 * Source snapshot input for the IDEA diagnostic annotator.
 */
data class KkIdeaDiagnosticSource(
    val name: String,
    val text: String,
) {
    init {
        require(name.isNotBlank()) { "diagnostic source name must not be blank" }
    }
}

/**
 * 可映射到 IDEA annotation 的 compiler diagnostic 快照。
 * Compiler diagnostic snapshot that can be mapped to an IDEA annotation.
 */
data class KkIdeaDiagnostic(
    val code: String,
    val message: String,
    val startOffset: Int,
    val endOffset: Int,
) {
    init {
        require(code.isNotBlank()) { "diagnostic code must not be blank" }
        require(message.isNotBlank()) { "diagnostic message must not be blank" }
        require(startOffset >= 0) { "startOffset must be zero or greater" }
        require(endOffset >= startOffset) { "endOffset must be greater than or equal to startOffset" }
    }

    /**
     * 返回 IDEA 使用的源码区间。
     * Returns the source range used by IDEA.
     */
    fun textRange(): TextRange =
        TextRange(startOffset, endOffset)

    /**
     * 返回 IDEA annotation 消息。
     * Returns the IDEA annotation message.
     */
    fun annotationMessage(): String =
        "$code: $message"
}

/**
 * 通过 compiler pipeline 收集 IDEA diagnostics。
 * Collects IDEA diagnostics through the compiler pipeline.
 */
object KkIdeaDiagnostics {
    /**
     * 编译源码并返回 IDEA diagnostic 数据。
     * Compiles source and returns IDEA diagnostic data.
     */
    fun collect(
        sourceName: String,
        text: String,
        compilerPipeline: CompilerPipeline = CompilerPipeline(),
    ): List<KkIdeaDiagnostic> {
        val source = SourceText.of(sourceName, text)
        return when (val result = compilerPipeline.compile(CompilationInput(source))) {
            is CompilationResult.Success -> emptyList()
            is CompilationResult.Failure -> result.diagnostics.map { diagnostic ->
                KkIdeaDiagnostic(
                    code = diagnostic.code,
                    message = diagnostic.message,
                    startOffset = diagnostic.span.startOffset,
                    endOffset = diagnostic.span.endOffset,
                )
            }
        }
    }
}

/**
 * 将 `compiler:core` diagnostics 暴露为 IDEA editor annotations。
 * Exposes `compiler:core` diagnostics as IDEA editor annotations.
 */
class KkDiagnosticExternalAnnotator(
    private val compilerPipeline: CompilerPipeline = CompilerPipeline(),
) : ExternalAnnotator<KkIdeaDiagnosticSource, List<KkIdeaDiagnostic>>() {
    /**
     * 从 PsiFile 收集源码快照。
     * Collects a source snapshot from a PsiFile.
     */
    override fun collectInformation(file: PsiFile): KkIdeaDiagnosticSource =
        KkIdeaDiagnosticSource(name = file.name, text = file.text)

    /**
     * 从 editor 回调收集源码快照。
     * Collects a source snapshot from the editor callback.
     */
    override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): KkIdeaDiagnosticSource =
        collectInformation(file)

    /**
     * 在后台编译源码快照并返回 diagnostics。
     * Compiles the source snapshot in the background and returns diagnostics.
     */
    override fun doAnnotate(collectedInfo: KkIdeaDiagnosticSource): List<KkIdeaDiagnostic> =
        KkIdeaDiagnostics.collect(collectedInfo.name, collectedInfo.text, compilerPipeline)

    /**
     * 把 diagnostics 应用为 IDEA error annotations。
     * Applies diagnostics as IDEA error annotations.
     */
    override fun apply(file: PsiFile, annotationResult: List<KkIdeaDiagnostic>, holder: AnnotationHolder) {
        for (diagnostic in annotationResult) {
            holder
                .newAnnotation(HighlightSeverity.ERROR, diagnostic.annotationMessage())
                .tooltip(diagnostic.annotationMessage())
                .range(diagnostic.textRange())
                .create()
        }
    }
}
