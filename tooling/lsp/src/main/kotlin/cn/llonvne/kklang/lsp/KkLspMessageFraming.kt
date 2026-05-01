package cn.llonvne.kklang.lsp

import kotlinx.serialization.json.JsonObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

private const val CONTENT_LENGTH_HEADER = "content-length"

/**
 * 读取 stdio LSP `Content-Length` framed JSON-RPC 消息。
 * Reads stdio LSP `Content-Length` framed JSON-RPC messages.
 */
class KkLspMessageReader(private val input: InputStream) {
    /**
     * 读取下一条 JSON-RPC 消息；输入已经结束时返回 null。
     * Reads the next JSON-RPC message; returns null when input has ended.
     */
    fun read(): JsonObject? {
        val headers = readHeaders() ?: return null
        val contentLength = headers[CONTENT_LENGTH_HEADER]?.toIntOrNull()
            ?: throw IllegalArgumentException("missing Content-Length header")
        val body = input.readNBytes(contentLength)
        require(body.size == contentLength) { "truncated LSP message body" }
        return KkLspJson.parseObject(body.toString(Charsets.UTF_8))
    }

    /**
     * 读取 header 行直到空行。
     * Reads header lines until the empty line.
     */
    private fun readHeaders(): Map<String, String>? {
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = readAsciiLine() ?: return if (headers.isEmpty()) null else headers
            if (line.isEmpty()) {
                return headers
            }
            val separator = line.indexOf(':')
            require(separator > 0) { "invalid LSP header line" }
            headers[line.substring(0, separator).lowercase()] = line.substring(separator + 1).trim()
        }
    }

    /**
     * 读取一行 ASCII header，支持 CRLF 和 LF。
     * Reads one ASCII header line, supporting CRLF and LF.
     */
    private fun readAsciiLine(): String? {
        val bytes = ByteArrayOutputStream()
        while (true) {
            val value = input.read()
            when (value) {
                -1 -> return if (bytes.size() == 0) null else bytes.toString(Charsets.US_ASCII)
                '\r'.code -> {
                    val next = input.read()
                    require(next == '\n'.code) { "CR must be followed by LF" }
                    return bytes.toString(Charsets.US_ASCII)
                }
                '\n'.code -> return bytes.toString(Charsets.US_ASCII)
                else -> bytes.write(value)
            }
        }
    }
}

/**
 * 写出 stdio LSP `Content-Length` framed JSON-RPC 消息。
 * Writes stdio LSP `Content-Length` framed JSON-RPC messages.
 */
class KkLspMessageWriter(private val output: OutputStream) {
    /**
     * 写出一条 JSON-RPC 消息并 flush。
     * Writes one JSON-RPC message and flushes the stream.
     */
    fun write(message: JsonObject) {
        val body = KkLspJson.encode(message).toByteArray(Charsets.UTF_8)
        output.write("Content-Length: ${body.size}\r\n\r\n".toByteArray(Charsets.US_ASCII))
        output.write(body)
        output.flush()
    }
}
