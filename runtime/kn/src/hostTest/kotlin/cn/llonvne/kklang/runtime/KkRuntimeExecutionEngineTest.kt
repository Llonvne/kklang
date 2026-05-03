@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cn.llonvne.kklang.runtime

import cn.llonvne.kklang.frontend.SourceText
import cn.llonvne.kklang.runtime.c.KK_VALUE_INT64
import cn.llonvne.kklang.runtime.c.KK_VALUE_STRING
import cn.llonvne.kklang.runtime.c.KK_VALUE_UNIT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 覆盖 Kotlin/Native runtime backend 的 value materialization 和执行边界。
 * Covers value materialization and execution boundaries of the Kotlin/Native runtime backend.
 */
class KkRuntimeExecutionEngineTest {
    /**
     * 验证 Unit value 通过 C ABI materialize 并保留 tag。
     * Verifies that a Unit value is materialized through the C ABI and preserves its tag.
     */
    @Test
    fun `unit value materializes through c runtime tag`() {
        val value = KkValue.unit()

        assertIs<KkValue.Unit>(value)
        assertEquals(KK_VALUE_UNIT, value.tag)
    }

    /**
     * 验证 Int64 value 通过 C ABI materialize 并保留 tag/value。
     * Verifies that an Int64 value is materialized through the C ABI and preserves tag and value.
     */
    @Test
    fun `int64 value materializes through c runtime tag`() {
        val value = KkValue.int64(-42)

        assertIs<KkValue.Int64>(value)
        assertEquals(KK_VALUE_INT64, value.tag)
        assertEquals(-42, value.value)
    }

    /**
     * 验证 String value 通过 C ABI materialize 并保留 tag/value。
     * Verifies that a String value is materialized through the C ABI and preserves tag and value.
     */
    @Test
    fun `string value materializes through c runtime tag`() {
        val value = KkValue.string("hello")

        assertIs<KkValue.String>(value)
        assertEquals(KK_VALUE_STRING, value.tag)
        assertEquals("hello", value.value)
        value.close()
    }

    /**
     * 验证 runtime backend 成功执行整数表达式并返回 runtime-backed value。
     * Verifies that the runtime backend executes an integer expression and returns a runtime-backed value.
     */
    @Test
    fun `runtime backend executes integer expression`() {
        val result = KkRuntimeExecutionEngine().execute(SourceText.of("sample.kk", "1 + 2 * 3"))

        assertIs<KkRuntimeExecutionResult.Success>(result)
        assertFalse(result.hasErrors)
        assertEquals(KkValue.Int64(value = 7, tag = KK_VALUE_INT64), result.value)
    }

    /**
     * 验证 runtime backend 成功执行不可变 val declaration。
     * Verifies that the runtime backend executes immutable val declarations.
     */
    @Test
    fun `runtime backend executes immutable val declarations`() {
        val result = KkRuntimeExecutionEngine().execute(SourceText.of("sample.kk", "val x = 1; val y = x + 2; y * 3"))

        assertIs<KkRuntimeExecutionResult.Success>(result)
        assertFalse(result.hasErrors)
        assertEquals(KkValue.Int64(value = 9, tag = KK_VALUE_INT64), result.value)
    }

    /**
     * 验证 runtime backend 成功执行字符串表达式并返回 runtime-backed value。
     * Verifies that the runtime backend executes a string expression and returns a runtime-backed value.
     */
    @Test
    fun `runtime backend executes string expression`() {
        val result = KkRuntimeExecutionEngine().execute(SourceText.of("sample.kk", "\"hello\""))

        assertIs<KkRuntimeExecutionResult.Success>(result)
        assertFalse(result.hasErrors)
        val value = assertIs<KkValue.String>(result.value)
        assertEquals(KK_VALUE_STRING, value.tag)
        assertEquals("hello", value.value)
        value.close()
    }

    /**
     * 验证 runtime backend 成功执行内建 print 并 materialize Unit。
     * Verifies that the runtime backend executes builtin print and materializes Unit.
     */
    @Test
    fun `runtime backend executes builtin print`() {
        val result = KkRuntimeExecutionEngine().execute(SourceText.of("sample.kk", "print(\"hello\")"))

        assertIs<KkRuntimeExecutionResult.Success>(result)
        assertFalse(result.hasErrors)
        assertEquals("hello", result.output)
        assertEquals(KkValue.Unit(tag = KK_VALUE_UNIT), result.value)
    }

    /**
     * 验证编译失败会直接返回原始 diagnostics。
     * Verifies that compilation failures directly return original diagnostics.
     */
    @Test
    fun `runtime backend returns compiler diagnostics`() {
        assertFailureCodes("@", "LEX001")
        assertFailureCodes("name", "TYPE001")
        assertFailureCodes("val x = 1; val x = 2; x", "BIND001")
    }

    /**
     * 验证求值失败会直接返回原始 diagnostics。
     * Verifies that evaluation failures directly return original diagnostics.
     */
    @Test
    fun `runtime backend returns evaluator diagnostics`() {
        assertFailureCodes("1 / 0", "EXEC002")
        assertFailureCodes("9223372036854775807 + 1", "EXEC003")
    }

    /**
     * 执行源码并断言失败 diagnostic code。
     * Executes source and asserts failure diagnostic codes.
     */
    private fun assertFailureCodes(text: String, vararg codes: String) {
        val result = KkRuntimeExecutionEngine().execute(SourceText.of("sample.kk", text))

        assertIs<KkRuntimeExecutionResult.Failure>(result)
        assertTrue(result.hasErrors)
        assertEquals(codes.toList(), result.diagnostics.map { it.code })
    }
}
