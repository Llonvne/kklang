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

/**
 * C runtime handle 的 Kotlin/Native 所有者 wrapper。
 * Kotlin/Native owning wrapper around a C runtime handle.
 */
class KkRuntime private constructor(
    private var handle: CPointer<kk_runtime>?,
) {
    private val strings = mutableSetOf<KkString>()

    /**
     * 在当前 runtime 中创建一个由 C runtime 拥有的字符串 wrapper。
     * Creates a string wrapper owned by the C runtime inside this runtime.
     */
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

    /**
     * 销毁 runtime，并把已登记的字符串 wrapper 标记为关闭。
     * Destroys the runtime and marks all registered string wrappers as closed.
     */
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

    /**
     * 返回当前 C runtime handle；runtime 已关闭时抛出异常。
     * Returns the current C runtime handle and throws when the runtime is closed.
     */
    internal fun currentHandle(): CPointer<kk_runtime> =
        handle ?: throw IllegalStateException("runtime is closed")

    /**
     * 从 runtime 的已拥有字符串集合中移除一个 wrapper。
     * Removes one wrapper from the runtime-owned string set.
     */
    internal fun unregister(string: KkString) {
        strings -= string
    }

    /**
     * KkRuntime 的创建工厂。
     * Creation factory for KkRuntime.
     */
    companion object {
        /**
         * 创建一个新的 C runtime 并返回 Kotlin owner wrapper。
         * Creates a new C runtime and returns its Kotlin owner wrapper.
         */
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
