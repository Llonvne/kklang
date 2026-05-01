package cn.llonvne.kklang.idea

import com.intellij.lang.annotation.AnnotationBuilder
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 覆盖 IDEA diagnostic annotator 对 compiler diagnostics 的映射。
 * Covers the IDEA diagnostic annotator mapping from compiler diagnostics.
 */
class KkDiagnosticExternalAnnotatorTest {
    /**
     * 验证合法源码不会产生 IDEA diagnostics。
     * Verifies that valid source produces no IDEA diagnostics.
     */
    @Test
    fun `diagnostics collector returns empty list for valid source`() {
        val diagnostics = KkIdeaDiagnostics.collect("sample.kk", "val x = 1; x")

        assertEquals(emptyList(), diagnostics)
    }

    /**
     * 验证 lexer diagnostic 映射为 IDEA diagnostic。
     * Verifies that lexer diagnostics map to IDEA diagnostics.
     */
    @Test
    fun `diagnostics collector maps lexer errors`() {
        val diagnostic = KkIdeaDiagnostics.collect("sample.kk", "@").single()

        assertEquals(KkIdeaDiagnostic("LEX001", "unknown character", 0, 1), diagnostic)
        assertEquals(TextRange(0, 1), diagnostic.textRange())
        assertEquals("LEX001: unknown character", diagnostic.annotationMessage())
    }

    /**
     * 验证 type checker diagnostic 映射为 IDEA diagnostic。
     * Verifies that type-checker diagnostics map to IDEA diagnostics.
     */
    @Test
    fun `diagnostics collector maps type checker errors`() {
        val diagnostic = KkIdeaDiagnostics.collect("sample.kk", "name").single()

        assertEquals("TYPE001", diagnostic.code)
        assertEquals(0, diagnostic.startOffset)
        assertEquals(4, diagnostic.endOffset)
    }

    /**
     * 验证 IDEA diagnostic 数据拒绝非法区间。
     * Verifies that IDEA diagnostic data rejects invalid ranges.
     */
    @Test
    fun `idea diagnostic validates ranges`() {
        assertFailsWith<IllegalArgumentException> {
            KkIdeaDiagnostic("X", "bad", -1, 1)
        }
        assertFailsWith<IllegalArgumentException> {
            KkIdeaDiagnostic("X", "bad", 2, 1)
        }
        assertFailsWith<IllegalArgumentException> {
            KkIdeaDiagnostic("", "bad", 0, 1)
        }
        assertFailsWith<IllegalArgumentException> {
            KkIdeaDiagnostic("X", "", 0, 1)
        }
        assertFailsWith<IllegalArgumentException> {
            KkIdeaDiagnosticSource("", "x")
        }
    }

    /**
     * 验证 external annotator 从 PsiFile 收集并应用 diagnostics。
     * Verifies that the external annotator collects from PsiFile and applies diagnostics.
     */
    @Test
    fun `external annotator collects and applies diagnostics`() {
        val annotator = KkDiagnosticExternalAnnotator()
        val file = psiFile("sample.kk", "@")
        val annotations = RecordingAnnotations()

        val collected = annotator.collectInformation(file)
        val collectedWithEditor = annotator.collectInformation(file, editor(), false)
        val diagnostics = annotator.doAnnotate(collectedWithEditor)
        annotator.apply(file, diagnostics, annotations.holder())

        assertEquals(KkIdeaDiagnosticSource("sample.kk", "@"), collected)
        assertEquals(listOf(RecordedAnnotation(TextRange(0, 1), "LEX001: unknown character")), annotations.values)
    }

    /**
     * 构造测试用 PsiFile proxy。
     * Builds a PsiFile proxy for tests.
     */
    private fun psiFile(name: String, text: String): PsiFile =
        Proxy.newProxyInstance(
            PsiFile::class.java.classLoader,
            arrayOf(PsiFile::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getName" -> name
                "getText" -> text
                "toString" -> "PsiFile($name)"
                else -> null
            }
        } as PsiFile

    /**
     * 构造测试用 Editor proxy。
     * Builds an Editor proxy for tests.
     */
    private fun editor(): Editor =
        Proxy.newProxyInstance(
            Editor::class.java.classLoader,
            arrayOf(Editor::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "toString" -> "Editor(sample)"
                else -> null
            }
        } as Editor

    /**
     * 记录测试 annotation。
     * Records a test annotation.
     */
    private data class RecordedAnnotation(val range: TextRange, val message: String)

    /**
     * 只实现本测试需要的 AnnotationHolder 方法。
     * Implements only the AnnotationHolder methods needed by this test.
     */
    private class RecordingAnnotations {
        val values = mutableListOf<RecordedAnnotation>()

        /**
         * 构造记录新版 annotation builder 调用的 holder。
         * Builds a holder that records modern annotation builder calls.
         */
        fun holder(): AnnotationHolder =
            Proxy.newProxyInstance(
                AnnotationHolder::class.java.classLoader,
                arrayOf(AnnotationHolder::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "newAnnotation" -> builder(args?.get(1) as String)
                    "isBatchMode" -> false
                    "toString" -> "RecordingAnnotationHolder"
                    else -> error("unused test holder method: ${method.name}")
                }
            } as AnnotationHolder

        /**
         * 构造记录 range 和 create 调用的 annotation builder。
         * Builds an annotation builder that records range and create calls.
         */
        private fun builder(message: String): AnnotationBuilder {
            var range: TextRange? = null
            lateinit var proxy: AnnotationBuilder
            proxy = Proxy.newProxyInstance(
                AnnotationBuilder::class.java.classLoader,
                arrayOf(AnnotationBuilder::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "range" -> {
                        range = args?.singleOrNull() as? TextRange ?: error("expected TextRange")
                        proxy
                    }
                    "create" -> {
                        values += RecordedAnnotation(range ?: error("range must be set before create"), message)
                        Unit
                    }
                    "toString" -> "RecordingAnnotationBuilder"
                    else -> proxy
                }
            } as AnnotationBuilder
            return proxy
        }
    }
}
