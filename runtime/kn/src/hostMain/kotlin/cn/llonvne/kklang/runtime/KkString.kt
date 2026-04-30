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

class KkString internal constructor(
    private val runtime: KkRuntime,
    private var handle: CPointer<kk_string>?,
) {
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

    fun close() {
        val string = handle ?: return
        val status = kk_string_release(runtime.currentHandle(), string).toKkStatus()
        if (status != KkStatus.Ok) {
            throw KkRuntimeException(status)
        }

        handle = null
        runtime.unregister(this)
    }

    internal fun markRuntimeClosed() {
        handle = null
    }

    private fun currentHandle(): CPointer<kk_string> =
        handle ?: throw IllegalStateException("string is closed")
}
