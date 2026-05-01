package cn.llonvne.kklang.execution

import cn.llonvne.kklang.frontend.SourceText
import cn.llonvne.kklang.frontend.lexing.Lexer
import cn.llonvne.kklang.frontend.parsing.Parser

class ExecutionEngine(
    private val lexer: Lexer = Lexer(),
    private val lowerer: IrLowerer = CoreIrLowerer(),
    private val evaluator: IrEvaluator = CoreIrEvaluator(),
) {
    fun execute(source: SourceText): ExecutionResult {
        val lexResult = lexer.tokenize(source)
        val parseResult = Parser(lexResult.tokens).parseExpressionDocument()
        val frontendDiagnostics = lexResult.diagnostics + parseResult.diagnostics
        if (frontendDiagnostics.isNotEmpty()) {
            return ExecutionResult.Failure(frontendDiagnostics)
        }

        val loweringResult = lowerer.lower(parseResult.expression)
        val ir = loweringResult.ir
        if (ir == null) {
            return ExecutionResult.Failure(loweringResult.diagnostics)
        }
        if (loweringResult.hasErrors) {
            return ExecutionResult.Failure(loweringResult.diagnostics)
        }

        val evaluationResult = evaluator.evaluate(ir)
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
