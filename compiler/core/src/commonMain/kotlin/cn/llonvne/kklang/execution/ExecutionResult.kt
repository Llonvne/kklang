package cn.llonvne.kklang.execution

import cn.llonvne.kklang.frontend.diagnostics.Diagnostic

/**
 * 执行结果的封闭模型，成功时携带值和输出，失败时携带 diagnostics 和已产生输出。
 * Closed execution result model carrying a value and output on success, and diagnostics plus produced output on failure.
 */
sealed interface ExecutionResult {
    val hasErrors: Boolean

    /**
     * 执行成功结果，当前不会携带 diagnostics。
     * Successful execution result that currently carries no diagnostics.
     */
    data class Success(val value: ExecutionValue, val output: String = "") : ExecutionResult {
        override val hasErrors: Boolean = false
    }

    /**
     * 执行失败结果，保留触发失败的 diagnostics 和失败前输出。
     * Failed execution result preserving diagnostics and output produced before failure.
     */
    data class Failure(val diagnostics: List<Diagnostic>, val output: String = "") : ExecutionResult {
        override val hasErrors: Boolean
            get() = diagnostics.isNotEmpty()
    }
}

/**
 * 当前可观察执行值集合。
 * Current set of observable execution values.
 */
sealed interface ExecutionValue {
    /**
     * 64 位有符号整数执行值。
     * Signed 64-bit integer execution value.
     */
    data class Int64(val value: Long) : ExecutionValue

    /**
     * 字符串执行值。
     * String execution value.
     */
    data class String(val value: kotlin.String) : ExecutionValue

    /**
     * Unit 执行值，表示副作用表达式已完成。
     * Unit execution value representing completion of a side-effect expression.
     */
    data object Unit : ExecutionValue
}
