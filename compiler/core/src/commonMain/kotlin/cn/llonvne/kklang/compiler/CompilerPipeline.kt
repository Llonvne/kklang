package cn.llonvne.kklang.compiler

import cn.llonvne.kklang.execution.CoreIrLowerer
import cn.llonvne.kklang.execution.IrExpression
import cn.llonvne.kklang.execution.IrProgram
import cn.llonvne.kklang.execution.IrLowerer
import cn.llonvne.kklang.frontend.SourceSpan
import cn.llonvne.kklang.frontend.SourceText
import cn.llonvne.kklang.frontend.diagnostics.Diagnostic
import cn.llonvne.kklang.frontend.lexing.Lexer
import cn.llonvne.kklang.frontend.lexing.Token
import cn.llonvne.kklang.frontend.parsing.Parser
import cn.llonvne.kklang.typechecking.SeedTypeChecker
import cn.llonvne.kklang.typechecking.TypeChecker
import cn.llonvne.kklang.typechecking.TypeRef

/**
 * 编译请求的最小输入，目前只包含一份源码文本。
 * Minimal compiler request input; it currently contains a single source text.
 */
data class CompilationInput(val source: SourceText)

/**
 * 成功编译后的最小 program，目前包装一个 Core IR program 和根类型。
 * Minimal successfully compiled program; it currently wraps one Core IR program and root type.
 */
data class CompiledProgram(
    val ir: IrProgram,
    val type: TypeRef,
) {
    val expression: IrExpression
        get() = ir.expression

    val span: SourceSpan
        get() = ir.span
}

/**
 * 编译管线实际运行过的阶段标记。
 * Phase marker for the stages actually run by the compiler pipeline.
 */
enum class CompilerPhase {
    Lexing,
    Parsing,
    TypeChecking,
    Lowering,
}

/**
 * 编译管线的封闭结果模型，成功时携带 program，失败时携带 diagnostics。
 * Closed compiler-pipeline result model carrying a program on success and diagnostics on failure.
 */
sealed interface CompilationResult {
    val diagnostics: List<Diagnostic>
    val phaseTrace: List<CompilerPhase>
    val hasErrors: Boolean

    /**
     * 编译成功结果，diagnostics 固定为空。
     * Successful compilation result with an always-empty diagnostics list.
     */
    data class Success(
        val program: CompiledProgram,
        override val phaseTrace: List<CompilerPhase>,
    ) : CompilationResult {
        override val diagnostics: List<Diagnostic> = emptyList()
        override val hasErrors: Boolean = false
    }

    /**
     * 编译失败结果，保留失败前已经运行的阶段轨迹。
     * Failed compilation result preserving the phase trace reached before failure.
     */
    data class Failure(
        override val diagnostics: List<Diagnostic>,
        override val phaseTrace: List<CompilerPhase>,
    ) : CompilationResult {
        override val hasErrors: Boolean
            get() = diagnostics.isNotEmpty()
    }
}

/**
 * 最小编译管线，按 lexing、parsing、type checking、lowering 顺序运行并在诊断出现时短路。
 * Minimal compiler pipeline that runs lexing, parsing, type checking, and lowering in order and short-circuits on diagnostics.
 */
class CompilerPipeline(
    private val lexer: Lexer = Lexer(),
    private val parserFactory: (List<Token>) -> Parser = { Parser(it) },
    private val typeChecker: TypeChecker = SeedTypeChecker(),
    private val lowerer: IrLowerer = CoreIrLowerer(),
) {
    /**
     * 编译输入源码并返回完整成功结果或第一个失败阶段的 diagnostics。
     * Compiles the input source and returns either a complete success or diagnostics from the first failed phase.
     */
    fun compile(input: CompilationInput): CompilationResult {
        val phaseTrace = mutableListOf<CompilerPhase>()

        phaseTrace += CompilerPhase.Lexing
        val lexResult = lexer.tokenize(input.source)
        if (lexResult.hasErrors) {
            return failure(lexResult.diagnostics, phaseTrace)
        }

        phaseTrace += CompilerPhase.Parsing
        val parseResult = parserFactory(lexResult.tokens).parseProgramDocument()
        if (parseResult.hasErrors) {
            return failure(parseResult.diagnostics, phaseTrace)
        }

        phaseTrace += CompilerPhase.TypeChecking
        val typeCheckResult = typeChecker.check(parseResult.program)
        if (typeCheckResult.hasErrors) {
            return failure(typeCheckResult.diagnostics, phaseTrace)
        }

        val typedProgram = typeCheckResult.program ?: return failure(
            listOf(
                Diagnostic(
                    code = "COMPILER001",
                    message = "type checking succeeded without typed program",
                    span = parseResult.program.span,
                ),
            ),
            phaseTrace,
        )

        phaseTrace += CompilerPhase.Lowering
        val loweringResult = lowerer.lower(typedProgram)
        if (loweringResult.hasErrors) {
            return failure(loweringResult.diagnostics, phaseTrace)
        }

        val irProgram = loweringResult.program ?: return failure(
            listOf(
                Diagnostic(
                    code = "COMPILER001",
                    message = "lowering succeeded without Core IR program",
                    span = typedProgram.expression.syntax.span,
                ),
            ),
            phaseTrace,
        )

        return CompilationResult.Success(
            program = CompiledProgram(ir = irProgram, type = typedProgram.type),
            phaseTrace = phaseTrace.toList(),
        )
    }

    /**
     * 构造失败结果并冻结当前阶段轨迹，避免调用方观察到后续突变。
     * Builds a failure result and freezes the current phase trace so callers cannot observe later mutation.
     */
    private fun failure(
        diagnostics: List<Diagnostic>,
        phaseTrace: List<CompilerPhase>,
    ): CompilationResult.Failure =
        CompilationResult.Failure(diagnostics = diagnostics, phaseTrace = phaseTrace.toList())
}
