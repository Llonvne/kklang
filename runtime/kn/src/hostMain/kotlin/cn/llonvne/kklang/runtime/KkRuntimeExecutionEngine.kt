package cn.llonvne.kklang.runtime

import cn.llonvne.kklang.compiler.CompilationInput
import cn.llonvne.kklang.compiler.CompilationResult
import cn.llonvne.kklang.compiler.CompilerPipeline
import cn.llonvne.kklang.execution.CoreIrEvaluator
import cn.llonvne.kklang.execution.ExecutionValue
import cn.llonvne.kklang.execution.IrEvaluator
import cn.llonvne.kklang.frontend.SourceText
import cn.llonvne.kklang.frontend.diagnostics.Diagnostic

/**
 * Kotlin/Native runtime backend 的执行结果，成功值是 runtime-backed KkValue。
 * Execution result for the Kotlin/Native runtime backend, whose success value is runtime-backed KkValue.
 */
sealed interface KkRuntimeExecutionResult {
    val hasErrors: Boolean
    val output: String

    /**
     * Native runtime backend 执行成功结果。
     * Successful execution result from the Native runtime backend.
     */
    data class Success(val value: KkValue, override val output: String = "") : KkRuntimeExecutionResult {
        override val hasErrors: Boolean = false
    }

    /**
     * Native runtime backend 执行失败结果，保留 compiler/evaluator diagnostics 和失败前输出。
     * Failed execution result from the Native runtime backend preserving compiler/evaluator diagnostics and output produced before failure.
     */
    data class Failure(val diagnostics: List<Diagnostic>, override val output: String = "") : KkRuntimeExecutionResult {
        override val hasErrors: Boolean
            get() = diagnostics.isNotEmpty()
    }
}

/**
 * 最小 Kotlin/Native runtime backend，复用 compiler pipeline 和 Core IR evaluator，再 materialize runtime value。
 * Minimal Kotlin/Native runtime backend that reuses the compiler pipeline and Core IR evaluator before materializing runtime values.
 */
class KkRuntimeExecutionEngine(
    private val compiler: CompilerPipeline = CompilerPipeline(),
    private val evaluator: IrEvaluator = CoreIrEvaluator(),
) {
    /**
     * 执行源码；编译失败或求值失败时直接返回原 diagnostics。
     * Executes source and directly returns original diagnostics for compilation or evaluation failures.
     */
    fun execute(source: SourceText): KkRuntimeExecutionResult {
        val compilation = compiler.compile(CompilationInput(source))
        val program = when (compilation) {
            is CompilationResult.Failure -> return KkRuntimeExecutionResult.Failure(compilation.diagnostics)
            is CompilationResult.Success -> compilation.program
        }

        val evaluation = evaluator.evaluate(program.ir)
        val value = evaluation.value
        if (value == null) {
            return KkRuntimeExecutionResult.Failure(evaluation.diagnostics, evaluation.output)
        }
        if (evaluation.hasErrors) {
            return KkRuntimeExecutionResult.Failure(evaluation.diagnostics, evaluation.output)
        }

        return KkRuntimeExecutionResult.Success(materialize(value), evaluation.output)
    }

    /**
     * 把当前 execution value materialize 为 C runtime-backed KkValue。
     * Materializes the current execution value into a C runtime-backed KkValue.
     */
    private fun materialize(value: ExecutionValue): KkValue =
        when (value) {
            is ExecutionValue.Int64 -> KkValue.int64(value.value)
            is ExecutionValue.String -> KkValue.string(value.value)
            ExecutionValue.Unit -> KkValue.unit()
        }
}
