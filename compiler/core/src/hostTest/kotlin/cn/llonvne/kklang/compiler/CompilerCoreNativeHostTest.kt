package cn.llonvne.kklang.compiler

import cn.llonvne.kklang.execution.ExecutionEngine
import cn.llonvne.kklang.execution.ExecutionResult
import cn.llonvne.kklang.execution.ExecutionValue
import cn.llonvne.kklang.frontend.SourceText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * 验证 compiler core 的 commonMain 代码可以被 Kotlin/Native host target 消费。
 * Verifies that compiler core commonMain code can be consumed by the Kotlin/Native host target.
 */
class CompilerCoreNativeHostTest {
    /**
     * 在 Native host test 中执行现有 compiler/evaluator 管线。
     * Executes the existing compiler/evaluator pipeline inside a Native host test.
     */
    @Test
    fun `compiler core executes on native host`() {
        val result = ExecutionEngine().execute(SourceText.of("sample.kk", "1 + 2 * 3"))

        assertIs<ExecutionResult.Success>(result)
        assertEquals(ExecutionValue.Int64(7), result.value)
    }
}
