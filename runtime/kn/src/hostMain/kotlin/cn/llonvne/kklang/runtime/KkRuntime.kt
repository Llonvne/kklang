@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cn.llonvne.kklang.runtime

import cn.llonvne.kklang.runtime.c.kk_runtime_create
import cn.llonvne.kklang.runtime.c.kk_runtime_destroy
import cn.llonvne.kklang.runtime.c.kk_string_new
import cnames.structs.kk_runtime
import cnames.structs.kk_string
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value

class KkRuntime private constructor(
    private var handle: CPointer<kk_runtime>?,
) {
    private val strings = mutableSetOf<KkString>()

    fun string(text: String): KkResult<KkString> {
        val runtime = currentHandle()
        return memScoped {
            val out = alloc<CPointerVar<kk_string>>()
            val status = kk_string_new(runtime, text, out.ptr).toKkStatus()
            if (status == KkStatus.Ok) {
                val string = KkString(this@KkRuntime, requireNotNull(out.value))
                strings += string
                KkResult.Success(string)
            } else {
                KkResult.Failure(status)
            }
        }
    }

    fun close() {
        val runtime = handle ?: return
        val status = kk_runtime_destroy(runtime).toKkStatus()
        if (status != KkStatus.Ok) {
            throw KkRuntimeException(status)
        }

        handle = null
        for (string in strings.toList()) {
            string.markRuntimeClosed()
        }
        strings.clear()
    }

    internal fun currentHandle(): CPointer<kk_runtime> =
        handle ?: throw IllegalStateException("runtime is closed")

    internal fun unregister(string: KkString) {
        strings -= string
    }

    companion object {
        fun create(): KkResult<KkRuntime> =
            memScoped {
                val out = alloc<CPointerVar<kk_runtime>>()
                val status = kk_runtime_create(out.ptr).toKkStatus()
                if (status == KkStatus.Ok) {
                    KkResult.Success(KkRuntime(requireNotNull(out.value)))
                } else {
                    KkResult.Failure(status)
                }
            }
    }
}
