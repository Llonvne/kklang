package cn.llonvne.kklang.compiler

import cn.llonvne.kklang.execution.CoreIrLowerer
import cn.llonvne.kklang.execution.IrExpression
import cn.llonvne.kklang.execution.IrLowerer
import cn.llonvne.kklang.frontend.SourceSpan
import cn.llonvne.kklang.frontend.SourceText
import cn.llonvne.kklang.frontend.diagnostics.Diagnostic
import cn.llonvne.kklang.frontend.lexing.Lexer
import cn.llonvne.kklang.frontend.lexing.Token
import cn.llonvne.kklang.frontend.parsing.AstProgram
import cn.llonvne.kklang.frontend.parsing.Parser

data class CompilationInput(val source: SourceText)

data class CompiledProgram(
    val expression: IrExpression,
) {
    val span: SourceSpan
        get() = expression.span
}

enum class CompilerPhase {
    Lexing,
    Parsing,
    Lowering,
}

sealed interface CompilationResult {
    val diagnostics: List<Diagnostic>
    val phaseTrace: List<CompilerPhase>
    val hasErrors: Boolean

    data class Success(
        val program: CompiledProgram,
        override val phaseTrace: List<CompilerPhase>,
    ) : CompilationResult {
        override val diagnostics: List<Diagnostic> = emptyList()
        override val hasErrors: Boolean = false
    }

    data class Failure(
        override val diagnostics: List<Diagnostic>,
        override val phaseTrace: List<CompilerPhase>,
    ) : CompilationResult {
        override val hasErrors: Boolean
            get() = diagnostics.isNotEmpty()
    }
}

class CompilerPipeline(
    private val lexer: Lexer = Lexer(),
    private val parserFactory: (List<Token>) -> Parser = { Parser(it) },
    private val lowerer: IrLowerer = CoreIrLowerer(),
) {
    fun compile(input: CompilationInput): CompilationResult {
        val phaseTrace = mutableListOf<CompilerPhase>()

        phaseTrace += CompilerPhase.Lexing
        val lexResult = lexer.tokenize(input.source)
        if (lexResult.hasErrors) {
            return failure(lexResult.diagnostics, phaseTrace)
        }

        phaseTrace += CompilerPhase.Parsing
        val parseResult = parserFactory(lexResult.tokens).parseExpressionDocument()
        if (parseResult.hasErrors) {
            return failure(parseResult.diagnostics, phaseTrace)
        }

        phaseTrace += CompilerPhase.Lowering
        val program = AstProgram(parseResult.expression)
        val loweringResult = lowerer.lower(program.expression)
        if (loweringResult.hasErrors) {
            return failure(loweringResult.diagnostics, phaseTrace)
        }

        val expression = loweringResult.ir ?: return failure(
            listOf(
                Diagnostic(
                    code = "COMPILER001",
                    message = "lowering succeeded without Core IR",
                    span = program.span,
                ),
            ),
            phaseTrace,
        )

        return CompilationResult.Success(
            program = CompiledProgram(expression),
            phaseTrace = phaseTrace.toList(),
        )
    }

    private fun failure(
        diagnostics: List<Diagnostic>,
        phaseTrace: List<CompilerPhase>,
    ): CompilationResult.Failure =
        CompilationResult.Failure(diagnostics = diagnostics, phaseTrace = phaseTrace.toList())
}
