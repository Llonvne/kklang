package cn.llonvne.kklang.lsp

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 覆盖 LSP launcher 的 stdio 循环。
 * Covers the LSP launcher's stdio loop.
 */
class KkLspLauncherTest {
    /**
     * 验证 launcher 会处理 framed initialize 请求。
     * Verifies that the launcher handles a framed initialize request.
     */
    @Test
    fun `launcher handles framed initialize request`() {
        val inputBytes = frame("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""")
        val output = ByteArrayOutputStream()

        KkLspLauncher().run(ByteArrayInputStream(inputBytes), output)

        val response = KkLspMessageReader(ByteArrayInputStream(output.toByteArray())).read()
        assertEquals("2.0", response?.get("jsonrpc")?.jsonPrimitiveContent())
    }

    /**
     * 验证 launcher 在空输入时正常结束。
     * Verifies that the launcher exits normally on empty input.
     */
    @Test
    fun `launcher exits on empty input`() {
        val output = ByteArrayOutputStream()

        KkLspLauncher().run(ByteArrayInputStream(ByteArray(0)), output)

        assertNull(KkLspMessageReader(ByteArrayInputStream(output.toByteArray())).read())
    }

    /**
     * 验证 main 在 EOF 输入下可以正常返回。
     * Verifies that main returns normally with EOF input.
     */
    @Test
    fun `main returns on eof input`() {
        val previousInput = System.`in`
        val previousOutput = System.out
        val output = ByteArrayOutputStream()
        try {
            System.setIn(ByteArrayInputStream(ByteArray(0)))
            System.setOut(PrintStream(output))

            KkLspMain.main(emptyArray())

            assertEquals("", output.toString())
        } finally {
            System.setIn(previousInput)
            System.setOut(previousOutput)
        }
    }

    /**
     * 构造 Content-Length framed JSON-RPC payload。
     * Builds a Content-Length framed JSON-RPC payload.
     */
    private fun frame(payload: String): ByteArray {
        val body = payload.toByteArray(Charsets.UTF_8)
        return "Content-Length: ${body.size}\r\n\r\n".toByteArray(Charsets.US_ASCII) + body
    }
}
