@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cn.llonvne.kklang.runtime

import cn.llonvne.kklang.frontend.SourceText
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.posix.SEEK_END
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fputs
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.rewind
import platform.posix.stderr
import kotlin.system.exitProcess

/**
 * Native 单文件 runner 的进程级结果。
 * Process-level result for the Native single-file runner.
 */
data class KkNativeProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

/**
 * Kotlin/Native 单文件 runner，把 `.kk` 源码执行结果映射为进程输出。
 * Kotlin/Native single-file runner that maps `.kk` source execution results to process output.
 */
class KkNativeSingleFileRunner(
    private val readText: (String) -> String = ::readHostFileText,
    private val engine: KkRuntimeExecutionEngine = KkRuntimeExecutionEngine(),
) {
    /**
     * 按 `kkrun <file.kk>` 参数约定运行单文件。
     * Runs one file according to the `kkrun <file.kk>` argument contract.
     */
    fun run(args: Array<String>): KkNativeProcessResult {
        if (args.size != 1) {
            return KkNativeProcessResult(exitCode = 64, stdout = "", stderr = "Usage: kkrun <file.kk>\n")
        }

        val path = args.single()
        if (!path.endsWith(".kk")) {
            return KkNativeProcessResult(exitCode = 64, stdout = "", stderr = "kkrun requires a .kk file: $path\n")
        }

        val text = try {
            readText(path)
        } catch (exception: IllegalArgumentException) {
            return KkNativeProcessResult(exitCode = 66, stdout = "", stderr = "${exception.message}\n")
        }

        return execute(sourceNameFromPath(path), text)
    }

    /**
     * 执行一份已读取的源码文本。
     * Executes an already-read source text.
     */
    fun execute(sourceName: String, text: String): KkNativeProcessResult {
        require(sourceName.isNotBlank()) { "source name must not be blank" }
        return when (val result = engine.execute(SourceText.of(sourceName, text))) {
            is KkRuntimeExecutionResult.Success -> KkNativeProcessResult(
                exitCode = 0,
                stdout = successStdout(result.output, result.value),
                stderr = "",
            )
            is KkRuntimeExecutionResult.Failure -> KkNativeProcessResult(
                exitCode = 1,
                stdout = result.output,
                stderr = diagnosticsStderr(result),
            )
        }
    }

    /**
     * 渲染成功执行的 stdout，并负责关闭需要释放的 runtime-backed value。
     * Renders successful stdout and closes runtime-backed values that need releasing.
     */
    private fun successStdout(output: String, value: KkValue): String {
        val valueText = try {
            value.displayText()
        } finally {
            if (value is KkValue.String) {
                value.close()
            }
        }
        return buildString {
            append(output)
            if (isNotEmpty() && !endsWith('\n')) {
                append('\n')
            }
            append("Result: ").append(valueText).append('\n')
        }
    }

    /**
     * 渲染失败执行的 diagnostics stderr。
     * Renders diagnostics stderr for failed execution.
     */
    private fun diagnosticsStderr(result: KkRuntimeExecutionResult.Failure): String =
        buildString {
            for (diagnostic in result.diagnostics) {
                append(diagnostic.code).append(": ").append(diagnostic.message).append('\n')
            }
        }

    /**
     * 返回 Native runner 中的 runtime value 显示文本。
     * Returns runtime value display text for the Native runner.
     */
    private fun KkValue.displayText(): String =
        when (this) {
            is KkValue.Int64 -> value.toString()
            is KkValue.String -> "\"$value\""
            is KkValue.Unit -> "Unit"
        }
}

/**
 * Native executable 入口，执行单个 `.kk` 文件并映射到真实进程退出码。
 * Native executable entry point that runs one `.kk` file and maps to a real process exit code.
 */
fun main(args: Array<String>) {
    val result = KkNativeSingleFileRunner().run(args)
    print(result.stdout)
    if (result.stderr.isNotEmpty()) {
        fputs(result.stderr, stderr)
    }
    exitProcess(result.exitCode)
}

/**
 * 从路径中提取 source name。
 * Extracts the source name from a path.
 */
private fun sourceNameFromPath(path: String): String =
    path.substringAfterLast('/')

/**
 * 使用 POSIX API 读取 Native host 上的源码文件。
 * Reads a source file on the Native host using POSIX APIs.
 */
private fun readHostFileText(path: String): String {
    val file = fopen(path, "rb") ?: throw IllegalArgumentException("Cannot open .kk file: $path")
    try {
        if (fseek(file, 0, SEEK_END) != 0) {
            throw IllegalArgumentException("Cannot seek .kk file: $path")
        }
        val size = ftell(file)
        if (size < 0) {
            throw IllegalArgumentException("Cannot read .kk file size: $path")
        }
        rewind(file)
        if (size == 0L) {
            return ""
        }

        val bytes = ByteArray(size.toInt())
        val read = bytes.usePinned { pinned ->
            fread(pinned.addressOf(0), 1.convert(), bytes.size.convert(), file)
        }
        if (read.toLong() != size) {
            throw IllegalArgumentException("Cannot read complete .kk file: $path")
        }
        return bytes.decodeToString()
    } finally {
        fclose(file)
    }
}
