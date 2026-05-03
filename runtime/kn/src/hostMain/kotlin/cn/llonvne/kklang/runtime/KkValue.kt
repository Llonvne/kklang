@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cn.llonvne.kklang.runtime

import cn.llonvne.kklang.runtime.c.KK_VALUE_INT64
import cn.llonvne.kklang.runtime.c.KK_VALUE_STRING
import cn.llonvne.kklang.runtime.c.KK_VALUE_UNIT
import cn.llonvne.kklang.runtime.c.kk_value_int64
import cn.llonvne.kklang.runtime.c.kk_value_string
import cn.llonvne.kklang.runtime.c.kk_value_unit
import kotlinx.cinterop.useContents

/**
 * runtime-backed value 的类型安全 Kotlin/Native 表示。
 * Type-safe Kotlin/Native representation of a runtime-backed value.
 */
sealed interface KkValue {
    val tag: UInt

    /**
     * 由 C runtime `KK_VALUE_UNIT` materialize 出来的 Unit value。
     * Unit value materialized from the C runtime `KK_VALUE_UNIT`.
     */
    class Unit internal constructor(override val tag: UInt) : KkValue {
        /**
         * 按 tag 比较 Unit runtime value。
         * Compares Unit runtime values by tag.
         */
        override fun equals(other: Any?): Boolean =
            other is Unit && tag == other.tag

        /**
         * 返回 tag 的 hash。
         * Returns the hash of the tag.
         */
        override fun hashCode(): Int =
            tag.hashCode()

        /**
         * 返回稳定的调试字符串。
         * Returns a stable debug string.
         */
        override fun toString(): kotlin.String =
            "Unit(tag=$tag)"
    }

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
        override fun toString(): kotlin.String =
            "Int64(value=$value, tag=$tag)"
    }

    /**
     * 由 C runtime `KK_VALUE_STRING` materialize 出来的 String value。
     * String value materialized from the C runtime `KK_VALUE_STRING`.
     */
    class String internal constructor(
        val value: kotlin.String,
        private val runtime: KkRuntime,
        private val string: KkString,
        override val tag: UInt,
    ) : KkValue {
        /**
         * 关闭该 runtime-backed string value 拥有的 runtime 资源。
         * Closes runtime resources owned by this runtime-backed string value.
         */
        fun close() {
            string.close()
            runtime.close()
        }

        /**
         * 按 value 和 tag 比较 String runtime value。
         * Compares String runtime values by value and tag.
         */
        override fun equals(other: Any?): Boolean =
            other is String && value == other.value && tag == other.tag

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
        override fun toString(): kotlin.String =
            "String(value=$value, tag=$tag)"
    }

    /**
     * KkValue 的 C ABI materialization 工厂。
     * C ABI materialization factory for KkValue.
     */
    companion object {
        /**
         * 通过 `kk_value_unit` materialize 一个 Unit value。
         * Materializes a Unit value through `kk_value_unit`.
         */
        fun unit(): Unit =
            kk_value_unit().useContents {
                check(tag == KK_VALUE_UNIT) { "kk_value_unit returned unexpected tag: $tag" }
                Unit(tag = tag)
            }

        /**
         * 通过 `kk_value_int64` materialize 一个 Int64 value。
         * Materializes an Int64 value through `kk_value_int64`.
         */
        fun int64(value: Long): Int64 =
            kk_value_int64(value).useContents {
                check(tag == KK_VALUE_INT64) { "kk_value_int64 returned unexpected tag: $tag" }
                Int64(value = `as`.int64, tag = tag)
            }

        /**
         * 通过 `kk_string_new` 和 `kk_value_string` materialize 一个 String value。
         * Materializes a String value through `kk_string_new` and `kk_value_string`.
         */
        fun string(value: kotlin.String): String {
            val runtime = KkRuntime.create().getOrThrow()
            val string = runtime.string(value).getOrThrow()
            return kk_value_string(string.currentHandle()).useContents {
                check(tag == KK_VALUE_STRING) { "kk_value_string returned unexpected tag: $tag" }
                String(value = string.text, runtime = runtime, string = string, tag = tag)
            }
        }
    }
}
