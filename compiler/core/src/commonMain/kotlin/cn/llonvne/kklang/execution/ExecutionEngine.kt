package cn.llonvne.kklang.execution

import cn.llonvne.kklang.compiler.CompilationInput
import cn.llonvne.kklang.compiler.CompilationResult
import cn.llonvne.kklang.compiler.CompilerPipeline
import cn.llonvne.kklang.frontend.SourceText

/**
 * 最小执行入口，先编译源码，再用配置的 evaluator 求值 Core IR。
 * Minimal execution entry point that compiles source first and then evaluates Core IR with the configured evaluator.
 */
class ExecutionEngine(
    private val compiler: CompilerPipeline = CompilerPipeline(),
    private val evaluator: IrEvaluator = CoreIrEvaluator(),
) {
    /**
     * 执行一份源码文本，编译失败时不会调用 evaluator。
     * Executes one source text and does not call the evaluator when compilation fails.
     */
    fun execute(source: SourceText): ExecutionResult {
        val compilation = compiler.compile(CompilationInput(source))
        val program = when (compilation) {
            is CompilationResult.Failure -> return ExecutionResult.Failure(compilation.diagnostics)
            is CompilationResult.Success -> compilation.program
        }

        val evaluationResult = evaluator.evaluate(program.expression)
        val value = evaluationResult.value
        if (value == null) {
            return ExecutionResult.Failure(evaluationResult.diagnostics)
        }
        if (evaluationResult.hasErrors) {
            return ExecutionResult.Failure(evaluationResult.diagnostics)
        }

        return ExecutionResult.Success(value)
    }
}
