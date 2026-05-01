package cn.llonvne.kklang.lsp

import java.io.InputStream
import java.io.OutputStream

/**
 * 将 LSP message framing 连接到 `KkLspServer`。
 * Connects LSP message framing to `KkLspServer`.
 */
class KkLspLauncher(
    private val server: KkLspServer = KkLspServer(),
) {
    /**
     * 从输入流读取请求，将 server 输出写入输出流，直到 EOF。
     * Reads requests from input, writes server output to output, and stops at EOF.
     */
    fun run(input: InputStream, output: OutputStream) {
        val reader = KkLspMessageReader(input)
        val writer = KkLspMessageWriter(output)
        while (true) {
            val message = reader.read() ?: return
            for (response in server.handle(message)) {
                writer.write(response)
            }
        }
    }
}

/**
 * LSP 进程入口。
 * LSP process entry point.
 */
object KkLspMain {
    /**
     * 通过 stdio 启动 LSP server。
     * Starts the LSP server over stdio.
     */
    @JvmStatic
    fun main(args: Array<String>) {
        KkLspLauncher().run(System.`in`, System.out)
    }
}
