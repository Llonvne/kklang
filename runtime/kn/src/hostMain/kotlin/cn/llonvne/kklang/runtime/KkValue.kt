@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cn.llonvne.kklang.runtime

import cn.llonvne.kklang.runtime.c.KK_VALUE_INT64
import cn.llonvne.kklang.runtime.c.kk_value_int64
import kotlinx.cinterop.useContents

/**
 * runtime-backed value 的类型安全 Kotlin/Native 表示。
 * Type-safe Kotlin/Native representation of a runtime-backed value.
 */
sealed interface KkValue {
    val tag: UInt

    /**
     * 由 C runtime `KK_VALUE_INT64` materialize 出来的 Int64 value。
     * Int64 value materialized from the C runtime `KK_VALUE_INT64`.
     */
    class Int64 internal constructor(
        val value: Long,
        override val tag: UInt,
    ) : KkValue {
        /**
         * 按 value 和 tag 比较 Int64 runtime value。
         * Compares Int64 runtime values by value and tag.
         */
        override fun equals(other: Any?): Boolean =
            other is Int64 && value == other.value && tag == other.tag

        /**
         * 返回 value 和 tag 的组合 hash。
         * Returns the combined hash of value and tag.
         */
        override fun hashCode(): Int =
            31 * value.hashCode() + tag.hashCode()

        /**
         * 返回稳定的调试字符串。
         * Returns a stable debug string.
         */
        override fun toString(): String =
            "Int64(value=$value, tag=$tag)"
    }

    /**
     * KkValue 的 C ABI materialization 工厂。
     * C ABI materialization factory for KkValue.
     */
    companion object {
        /**
         * 通过 `kk_value_int64` materialize 一个 Int64 value。
         * Materializes an Int64 value through `kk_value_int64`.
         */
        fun int64(value: Long): Int64 =
            kk_value_int64(value).useContents {
                check(tag == KK_VALUE_INT64) { "kk_value_int64 returned unexpected tag: $tag" }
                Int64(value = `as`.int64, tag = tag)
            }
    }
}
