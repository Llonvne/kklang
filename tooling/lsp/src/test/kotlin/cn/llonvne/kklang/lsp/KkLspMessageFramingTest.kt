package cn.llonvne.kklang.lsp

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * 覆盖 LSP stdio JSON-RPC 消息 framing。
 * Covers LSP stdio JSON-RPC message framing.
 */
class KkLspMessageFramingTest {
    /**
     * 验证 writer 和 reader 可以往返一条 JSON-RPC 消息。
     * Verifies that the writer and reader can round-trip one JSON-RPC message.
     */
    @Test
    fun `writer and reader round trip one message`() {
        val output = ByteArrayOutputStream()
        KkLspMessageWriter(output).write(JsonObject(mapOf("jsonrpc" to JsonPrimitive("2.0"))))

        val input = ByteArrayInputStream(output.toByteArray())
        val message = KkLspMessageReader(input).read()

        assertEquals("2.0", message?.get("jsonrpc")?.jsonPrimitiveContent())
    }

    /**
     * 验证 reader 在 EOF 时返回 null。
     * Verifies that the reader returns null at EOF.
     */
    @Test
    fun `reader returns null on eof`() {
        assertNull(KkLspMessageReader(ByteArrayInputStream(ByteArray(0))).read())
    }

    /**
     * 验证 reader 拒绝缺少 Content-Length 的消息。
     * Verifies that the reader rejects messages without Content-Length.
     */
    @Test
    fun `reader rejects missing content length`() {
        val input = ByteArrayInputStream("\r\n{}".toByteArray())

        assertFailsWith<IllegalArgumentException> {
            KkLspMessageReader(input).read()
        }
    }

    /**
     * 验证 reader 拒绝截断 body。
     * Verifies that the reader rejects truncated bodies.
     */
    @Test
    fun `reader rejects truncated body`() {
        val input = ByteArrayInputStream("Content-Length: 4\r\n\r\n{}".toByteArray())

        assertFailsWith<IllegalArgumentException> {
            KkLspMessageReader(input).read()
        }
    }

    /**
     * 验证 reader 拒绝非法 Content-Length。
     * Verifies that the reader rejects invalid Content-Length values.
     */
    @Test
    fun `reader rejects invalid content length`() {
        val input = ByteArrayInputStream("Content-Length: nope\r\n\r\n{}".toByteArray())

        assertFailsWith<IllegalArgumentException> {
            KkLspMessageReader(input).read()
        }
    }

    /**
     * 验证 reader 拒绝非法 header 行。
     * Verifies that the reader rejects invalid header lines.
     */
    @Test
    fun `reader rejects invalid header line`() {
        val input = ByteArrayInputStream("Broken\r\n\r\n{}".toByteArray())

        assertFailsWith<IllegalArgumentException> {
            KkLspMessageReader(input).read()
        }
    }

    /**
     * 验证 reader 拒绝不完整 CRLF。
     * Verifies that the reader rejects incomplete CRLF sequences.
     */
    @Test
    fun `reader rejects carriage return without line feed`() {
        val input = ByteArrayInputStream("Content-Length: 2\rX".toByteArray())

        assertFailsWith<IllegalArgumentException> {
            KkLspMessageReader(input).read()
        }
    }

    /**
     * 验证 reader 支持 LF-only header，并覆盖 EOF 后的部分 header。
     * Verifies that the reader supports LF-only headers and covers partial headers before EOF.
     */
    @Test
    fun `reader supports lf only headers and partial header eof`() {
        val lfOnly = ByteArrayInputStream("Content-Length: 2\n\n{}".toByteArray())
        assertEquals(JsonObject(emptyMap()), KkLspMessageReader(lfOnly).read())

        val partial = ByteArrayInputStream("Content-Length: 2".toByteArray())
        assertFailsWith<IllegalArgumentException> {
            KkLspMessageReader(partial).read()
        }
    }

    /**
     * 验证测试 helper 会返回 JSON object。
     * Verifies that the test helper returns a JSON object.
     */
    @Test
    fun `json helper returns object`() {
        val message = KkLspJson.parseObject("""{"method":"initialize"}""")

        assertEquals("initialize", message.jsonObject["method"]?.jsonPrimitiveContent())
    }
}
