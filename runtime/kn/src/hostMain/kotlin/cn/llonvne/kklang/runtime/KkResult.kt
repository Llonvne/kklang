package cn.llonvne.kklang.runtime

sealed interface KkResult<out T> {
    fun getOrThrow(): T

    data class Success<out T>(val value: T) : KkResult<T> {
        override fun getOrThrow(): T = value
    }

    data class Failure<out T>(val status: KkStatus) : KkResult<T> {
        override fun getOrThrow(): T {
            throw KkRuntimeException(status)
        }
    }
}

class KkRuntimeException(val status: KkStatus) : RuntimeException("kklang runtime failed: $status")
