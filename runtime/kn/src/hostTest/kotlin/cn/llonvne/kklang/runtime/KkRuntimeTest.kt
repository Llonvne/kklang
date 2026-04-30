package cn.llonvne.kklang.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class KkRuntimeTest {
    @Test
    fun `status codes map to typed statuses`() {
        assertEquals(KkStatus.Ok, KkStatus.fromCode(0))
        assertEquals(KkStatus.OutOfMemory, KkStatus.fromCode(1))
        assertEquals(KkStatus.InvalidArgument, KkStatus.fromCode(2))
        assertEquals(KkStatus.Unknown(99), KkStatus.fromCode(99))
    }

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

    @Test
    fun `runtime close marks owned strings closed`() {
        val runtime = KkRuntime.create().getOrThrow()
        val string = runtime.string("owned").getOrThrow()

        runtime.close()

        assertFailsWith<IllegalStateException> { string.text }
        string.close()
    }

    @Test
    fun `typed failure throws runtime exception`() {
        val failure = KkResult.Failure<KkRuntime>(KkStatus.InvalidArgument)

        val exception = assertFailsWith<KkRuntimeException> {
            failure.getOrThrow()
        }
        assertEquals(KkStatus.InvalidArgument, exception.status)
    }
}
