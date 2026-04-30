@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cn.llonvne.kklang.runtime

import cn.llonvne.kklang.runtime.c.KK_ERR_INVALID_ARGUMENT
import cn.llonvne.kklang.runtime.c.KK_ERR_OOM
import cn.llonvne.kklang.runtime.c.KK_OK

sealed class KkStatus(val code: UInt) {
    data object Ok : KkStatus(KK_OK)
    data object OutOfMemory : KkStatus(KK_ERR_OOM)
    data object InvalidArgument : KkStatus(KK_ERR_INVALID_ARGUMENT)
    data class Unknown(val rawCode: Int) : KkStatus(rawCode.toUInt())

    companion object {
        fun fromCode(code: Int): KkStatus =
            fromCode(code.toUInt())

        fun fromCode(code: UInt): KkStatus =
            when (code) {
                KK_OK -> Ok
                KK_ERR_OOM -> OutOfMemory
                KK_ERR_INVALID_ARGUMENT -> InvalidArgument
                else -> Unknown(code.toInt())
            }
    }
}

internal fun UInt.toKkStatus(): KkStatus = KkStatus.fromCode(this)
