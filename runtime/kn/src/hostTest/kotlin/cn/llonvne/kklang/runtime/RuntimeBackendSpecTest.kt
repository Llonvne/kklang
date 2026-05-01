@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cn.llonvne.kklang.runtime

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.posix.SEEK_END
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.rewind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 覆盖 Native runtime backend DSL 规范及其与 Markdown 规范的同步。
 * Covers the Native runtime backend DSL spec and its synchronization with Markdown specs.
 */
class RuntimeBackendSpecTest {
    /**
     * 验证 backend DSL 记录 engine、value 和 materializer 表面。
     * Verifies that the backend DSL records engine, value, and materializer surfaces.
     */
    @Test
    fun `runtime backend dsl records engine value and materializer surface`() {
        assertEquals(
            listOf("KkRuntimeExecutionEngine", "KkRuntimeExecutionResult"),
            minimalRuntimeBackendSpec.engineTypes,
        )
        assertEquals(listOf("KkValue", "KkValue.Int64"), minimalRuntimeBackendSpec.valueTypes)
        assertEquals(listOf("kk_value_int64"), minimalRuntimeBackendSpec.materializers)
    }

    /**
     * 验证 backend DSL 记录 compiler/evaluator 两类失败来源。
     * Verifies that the backend DSL records compiler and evaluator failure sources.
     */
    @Test
    fun `runtime backend dsl records failure sources`() {
        assertEquals(
            listOf("compiler diagnostics", "evaluator diagnostics"),
            minimalRuntimeBackendSpec.failureSources,
        )
    }

    /**
     * 验证 Markdown 规范包含 backend DSL 的关键公开表面。
     * Verifies that Markdown specs contain the key public surface from the backend DSL.
     */
    @Test
    fun `markdown specs contain runtime backend dsl surface`() {
        val runtimeMarkdown = readHostText("../../spec/runtime.md")
        val executionMarkdown = readHostText("../../spec/execution.md")
        val expectedRuntimeTerms = minimalRuntimeBackendSpec.engineTypes +
            minimalRuntimeBackendSpec.valueTypes +
            minimalRuntimeBackendSpec.materializers

        for (term in expectedRuntimeTerms) {
            assertTrue(runtimeMarkdown.contains(term), "missing $term in Markdown runtime spec")
        }
        assertTrue(executionMarkdown.contains("KkRuntimeExecutionEngine"), "missing backend engine in execution spec")
        assertTrue(executionMarkdown.contains("KkValue.Int64"), "missing backend value in execution spec")
    }
}

/**
 * 使用 POSIX API 读取 host 测试工作目录相对路径下的文本文件。
 * Reads a text file under the host-test working directory using POSIX APIs.
 */
private fun readHostText(path: String): String {
    val file = fopen(path, "rb") ?: error("Cannot open $path")
    try {
        if (fseek(file, 0, SEEK_END) != 0) {
            error("Cannot seek $path")
        }
        val size = ftell(file)
        if (size < 0) {
            error("Cannot read size for $path")
        }
        rewind(file)
        if (size == 0L) {
            return ""
        }

        val bytes = ByteArray(size.toInt())
        val read = bytes.usePinned { pinned ->
            fread(pinned.addressOf(0), 1.convert(), bytes.size.convert(), file)
        }
        if (read.toLong() != size) {
            error("Expected $size bytes from $path, got $read")
        }
        return bytes.decodeToString()
    } finally {
        fclose(file)
    }
}
