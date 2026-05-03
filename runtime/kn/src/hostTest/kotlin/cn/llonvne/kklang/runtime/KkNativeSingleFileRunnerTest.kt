package cn.llonvne.kklang.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 覆盖 Native 单文件 runner 的进程级 stdout、stderr 和 exit code 映射。
 * Covers process-level stdout, stderr, and exit-code mapping for the Native single-file runner.
 */
class KkNativeSingleFileRunnerTest {
    /**
     * 验证 runner 成功执行整数表达式并输出最终值。
     * Verifies that the runner executes an integer expression and writes the final value.
     */
    @Test
    fun `runner executes integer expression`() {
        val result = runnerReturning("1 + 2").run(arrayOf("/tmp/main.kk"))

        assertEquals(KkNativeProcessResult(exitCode = 0, stdout = "Result: 3\n", stderr = ""), result)
    }

    /**
     * 验证 runner 成功执行字符串表达式并使用引号显示结果。
     * Verifies that the runner executes a string expression and displays the result with quotes.
     */
    @Test
    fun `runner executes string expression`() {
        val result = runnerReturning("\"hello\"").run(arrayOf("/tmp/main.kk"))

        assertEquals(KkNativeProcessResult(exitCode = 0, stdout = "Result: \"hello\"\n", stderr = ""), result)
    }

    /**
     * 验证 runner 保留 print stdout 并追加 Unit 结果。
     * Verifies that the runner preserves print stdout and appends the Unit result.
     */
    @Test
    fun `runner preserves print output`() {
        val result = runnerReturning("print(\"hello\")").run(arrayOf("/tmp/main.kk"))

        assertEquals(KkNativeProcessResult(exitCode = 0, stdout = "hello\nResult: Unit\n", stderr = ""), result)
    }

    /**
     * 验证 runner 把语言 diagnostics 写入 stderr 并返回失败 exit code。
     * Verifies that the runner writes language diagnostics to stderr and returns a failing exit code.
     */
    @Test
    fun `runner reports diagnostics`() {
        val result = runnerReturning("1 / 0").run(arrayOf("/tmp/main.kk"))

        assertEquals(KkNativeProcessResult(exitCode = 1, stdout = "", stderr = "EXEC002: division by zero\n"), result)
    }

    /**
     * 验证 runner 校验参数数量、文件扩展名和读取失败。
     * Verifies that the runner validates argument count, file extension, and read failures.
     */
    @Test
    fun `runner validates process inputs`() {
        val runner = runnerReturning("1")
        val failingReader = KkNativeSingleFileRunner(readText = { path: String ->
            throw IllegalArgumentException("Cannot open .kk file: $path")
        })

        assertEquals(KkNativeProcessResult(64, "", "Usage: kkrun <file.kk>\n"), runner.run(emptyArray()))
        assertEquals(KkNativeProcessResult(64, "", "Usage: kkrun <file.kk>\n"), runner.run(arrayOf("a.kk", "b.kk")))
        assertEquals(
            KkNativeProcessResult(64, "", "kkrun requires a .kk file: main.txt\n"),
            runner.run(arrayOf("main.txt")),
        )
        assertEquals(
            KkNativeProcessResult(66, "", "Cannot open .kk file: missing.kk\n"),
            failingReader.run(arrayOf("missing.kk")),
        )
    }

    /**
     * 验证源码级 execute 拒绝空 source name。
     * Verifies that source-level execute rejects a blank source name.
     */
    @Test
    fun `runner source execution validates source name`() {
        assertFailsWith<IllegalArgumentException> {
            runnerReturning("1").execute("", "1")
        }
    }

    /**
     * 创建固定读取内容的 runner。
     * Creates a runner with fixed file-read content.
     */
    private fun runnerReturning(text: String): KkNativeSingleFileRunner =
        KkNativeSingleFileRunner(readText = { text })
}
