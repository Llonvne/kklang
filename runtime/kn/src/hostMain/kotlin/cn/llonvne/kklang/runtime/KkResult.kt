package cn.llonvne.kklang.runtime

/**
 * Kotlin/Native runtime 调用的封闭结果模型。
 * Closed result model for Kotlin/Native runtime calls.
 */
sealed interface KkResult<out T> {
    /**
     * 成功时返回值，失败时抛出 KkRuntimeException。
     * Returns the value on success and throws KkRuntimeException on failure.
     */
    fun getOrThrow(): T

    /**
     * runtime 调用成功并携带返回值。
     * Runtime call succeeded and carries its value.
     */
    data class Success<out T>(val value: T) : KkResult<T> {
        /**
         * 直接返回成功值。
         * Returns the successful value directly.
         */
        override fun getOrThrow(): T = value
    }

    /**
     * runtime 调用失败并携带 C ABI status。
     * Runtime call failed and carries the C ABI status.
     */
    data class Failure<out T>(val status: KkStatus) : KkResult<T> {
        /**
         * 将失败 status 转换为异常。
         * Converts the failure status into an exception.
         */
        override fun getOrThrow(): T {
            throw KkRuntimeException(status)
        }
    }
}

/**
 * Kotlin wrapper 在 C runtime 返回失败 status 时抛出的异常。
 * Exception thrown by Kotlin wrappers when the C runtime returns a failing status.
 */
class KkRuntimeException(val status: KkStatus) : RuntimeException("kklang runtime failed: $status")
