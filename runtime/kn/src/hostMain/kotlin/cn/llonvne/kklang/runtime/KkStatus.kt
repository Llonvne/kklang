@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cn.llonvne.kklang.runtime

import cn.llonvne.kklang.runtime.c.KK_ERR_INVALID_ARGUMENT
import cn.llonvne.kklang.runtime.c.KK_ERR_OOM
import cn.llonvne.kklang.runtime.c.KK_OK

/**
 * C runtime status code 的类型化 Kotlin 表示。
 * Typed Kotlin representation of C runtime status codes.
 */
sealed class KkStatus(val code: UInt) {
    /**
     * C runtime 操作成功。
     * C runtime operation succeeded.
     */
    data object Ok : KkStatus(KK_OK)

    /**
     * C runtime 内存分配失败。
     * C runtime memory allocation failed.
     */
    data object OutOfMemory : KkStatus(KK_ERR_OOM)

    /**
     * C runtime 收到非法参数。
     * C runtime received an invalid argument.
     */
    data object InvalidArgument : KkStatus(KK_ERR_INVALID_ARGUMENT)

    /**
     * 当前 Kotlin wrapper 尚未识别的 status code。
     * Status code not yet recognized by the current Kotlin wrapper.
     */
    data class Unknown(val rawCode: Int) : KkStatus(rawCode.toUInt())

    /**
     * KkStatus 的 status code 转换工厂。
     * Status-code conversion factory for KkStatus.
     */
    companion object {
        /**
         * 从有符号 C interop status code 创建类型化 status。
         * Creates a typed status from a signed C interop status code.
         */
        fun fromCode(code: Int): KkStatus =
            fromCode(code.toUInt())

        /**
         * 从无符号 ABI status code 创建类型化 status。
         * Creates a typed status from an unsigned ABI status code.
         */
        fun fromCode(code: UInt): KkStatus =
            when (code) {
                KK_OK -> Ok
                KK_ERR_OOM -> OutOfMemory
                KK_ERR_INVALID_ARGUMENT -> InvalidArgument
                else -> Unknown(code.toInt())
            }
    }
}

/**
 * 将 C interop 返回的 UInt status 转换为类型化 KkStatus。
 * Converts a UInt status returned by C interop into a typed KkStatus.
 */
internal fun UInt.toKkStatus(): KkStatus = KkStatus.fromCode(this)
