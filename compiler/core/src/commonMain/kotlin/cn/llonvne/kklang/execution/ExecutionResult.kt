package cn.llonvne.kklang.execution

import cn.llonvne.kklang.frontend.diagnostics.Diagnostic

/**
 * 执行结果的封闭模型，成功时携带值，失败时携带 diagnostics。
 * Closed execution result model carrying a value on success and diagnostics on failure.
 */
sealed interface ExecutionResult {
    val hasErrors: Boolean

    /**
     * 执行成功结果，当前不会携带 diagnostics。
     * Successful execution result that currently carries no diagnostics.
     */
    data class Success(val value: ExecutionValue) : ExecutionResult {
        override val hasErrors: Boolean = false
    }

    /**
     * 执行失败结果，保留触发失败的 diagnostics。
     * Failed execution result preserving the diagnostics that caused failure.
     */
    data class Failure(val diagnostics: List<Diagnostic>) : ExecutionResult {
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
}
