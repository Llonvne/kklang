package cn.llonvne.kklang.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * 覆盖 Kotlin/Native runtime wrapper 的状态映射、生命周期和失败语义。
 * Covers status mapping, lifetime, and failure semantics of the Kotlin/Native runtime wrappers.
 */
class KkRuntimeTest {
    /**
     * 验证 C ABI status code 会映射为类型化 Kotlin status。
     * Verifies that C ABI status codes map to typed Kotlin statuses.
     */
    @Test
    fun `status codes map to typed statuses`() {
        assertEquals(KkStatus.Ok, KkStatus.fromCode(0))
        assertEquals(KkStatus.OutOfMemory, KkStatus.fromCode(1))
        assertEquals(KkStatus.InvalidArgument, KkStatus.fromCode(2))
        assertEquals(KkStatus.Unknown(99), KkStatus.fromCode(99))
    }

    /**
     * 验证 runtime 创建成功结果和重复 close 的幂等行为。
     * Verifies successful runtime creation and idempotent repeated close behavior.
     */
    @Test
    fun `runtime create returns typed success and close is idempotent`() {
        val result = KkRuntime.create()
        val runtime = result.getOrThrow()

        assertIs<KkResult.Success<KkRuntime>>(result)
        runtime.close()
        runtime.close()
        assertFailsWith<IllegalStateException> {
            runtime.string("after-close").getOrThrow()
        }
    }

    /**
     * 验证字符串 wrapper 保留 UTF-8 内容并在 close 后拒绝访问。
     * Verifies that the string wrapper preserves UTF-8 content and rejects access after close.
     */
    @Test
    fun `string wrapper preserves utf8 bytes and rejects use after close`() {
        val runtime = KkRuntime.create().getOrThrow()
        val string = runtime.string("hello lambda").getOrThrow()

        assertEquals("hello lambda", string.text)
        assertEquals("hello lambda".encodeToByteArray().size.toULong(), string.byteSize)
        string.close()
        string.close()
        assertFailsWith<IllegalStateException> { string.text }
        runtime.close()
    }

    /**
     * 验证 runtime close 会把仍归属它的字符串标记为关闭。
     * Verifies that runtime close marks still-owned strings as closed.
     */
    @Test
    fun `runtime close marks owned strings closed`() {
        val runtime = KkRuntime.create().getOrThrow()
        val string = runtime.string("owned").getOrThrow()

        runtime.close()

        assertFailsWith<IllegalStateException> { string.text }
        string.close()
    }

    /**
     * 验证 typed failure 的 getOrThrow 会抛出 runtime 异常。
     * Verifies that getOrThrow on a typed failure throws a runtime exception.
     */
    @Test
    fun `typed failure throws runtime exception`() {
        val failure = KkResult.Failure<KkRuntime>(KkStatus.InvalidArgument)

        val exception = assertFailsWith<KkRuntimeException> {
            failure.getOrThrow()
        }
        assertEquals(KkStatus.InvalidArgument, exception.status)
    }
}
