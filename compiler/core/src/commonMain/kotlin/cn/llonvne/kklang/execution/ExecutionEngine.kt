package cn.llonvne.kklang.execution

import cn.llonvne.kklang.compiler.CompilationInput
import cn.llonvne.kklang.compiler.CompilationResult
import cn.llonvne.kklang.compiler.CompilerPipeline
import cn.llonvne.kklang.frontend.SourceText

class ExecutionEngine(
    private val compiler: CompilerPipeline = CompilerPipeline(),
    private val evaluator: IrEvaluator = CoreIrEvaluator(),
) {
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
