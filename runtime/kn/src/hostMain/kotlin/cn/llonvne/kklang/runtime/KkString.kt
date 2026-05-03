@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cn.llonvne.kklang.runtime

import cn.llonvne.kklang.runtime.c.kk_string_data
import cn.llonvne.kklang.runtime.c.kk_string_release
import cn.llonvne.kklang.runtime.c.kk_string_size
import cnames.structs.kk_string
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.posix.size_tVar

/**
 * C runtime 字符串 handle 的 Kotlin/Native wrapper。
 * Kotlin/Native wrapper around a C runtime string handle.
 */
class KkString internal constructor(
    private val runtime: KkRuntime,
    private var handle: CPointer<kk_string>?,
) {
    /**
     * 返回 C runtime 字符串的 UTF-8 字节长度。
     * Returns the UTF-8 byte length of the C runtime string.
     */
    val byteSize: ULong
        get() {
            val string = currentHandle()
            return memScoped {
                val out = alloc<size_tVar>()
                val status = kk_string_size(string, out.ptr).toKkStatus()
                if (status != KkStatus.Ok) {
                    throw KkRuntimeException(status)
                }
                out.value
            }
        }

    /**
     * 返回 C runtime 字符串的 UTF-8 文本内容。
     * Returns the UTF-8 text content of the C runtime string.
     */
    val text: String
        get() {
            val string = currentHandle()
            return memScoped {
                val out = alloc<CPointerVar<ByteVar>>()
                val status = kk_string_data(string, out.ptr).toKkStatus()
                if (status != KkStatus.Ok) {
                    throw KkRuntimeException(status)
                }
                requireNotNull(out.value).toKString()
            }
        }

    /**
     * 释放当前字符串；重复关闭会被视为无操作。
     * Releases the current string; repeated close calls are treated as no-ops.
     */
    fun close() {
        val string = handle ?: return
        val status = kk_string_release(runtime.currentHandle(), string).toKkStatus()
        if (status != KkStatus.Ok) {
            throw KkRuntimeException(status)
        }

        handle = null
        runtime.unregister(this)
    }

    /**
     * 在 runtime 已销毁后标记字符串 handle 不再可用。
     * Marks the string handle as unavailable after its runtime has been destroyed.
     */
    internal fun markRuntimeClosed() {
        handle = null
    }

    /**
     * 返回当前 C string handle；字符串已关闭时抛出异常。
     * Returns the current C string handle and throws when the string is closed.
     */
    internal fun currentHandle(): CPointer<kk_string> =
        handle ?: throw IllegalStateException("string is closed")
}
