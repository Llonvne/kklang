package cn.llonvne.kklang.execution

import cn.llonvne.kklang.frontend.diagnostics.Diagnostic

sealed interface ExecutionResult {
    val hasErrors: Boolean

    data class Success(val value: ExecutionValue) : ExecutionResult {
        override val hasErrors: Boolean = false
    }

    data class Failure(val diagnostics: List<Diagnostic>) : ExecutionResult {
        override val hasErrors: Boolean
            get() = diagnostics.isNotEmpty()
    }
}

sealed interface ExecutionValue {
    data class Int64(val value: Long) : ExecutionValue
}

