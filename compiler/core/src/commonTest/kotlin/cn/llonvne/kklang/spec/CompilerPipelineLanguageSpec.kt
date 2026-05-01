package cn.llonvne.kklang.spec

/**
 * compiler pipeline 的可执行 DSL 规范快照。
 * Executable DSL spec snapshot for the compiler pipeline.
 */
data class CompilerPipelineSpec(
    val name: String,
    val phases: List<String>,
    val resultTypes: List<String>,
    val stopRules: List<String>,
    val diagnostics: List<DiagnosticSpec>,
)

/**
 * 创建 compiler pipeline DSL 规范。
 * Creates a compiler pipeline DSL spec.
 */
fun compilerPipelineSpec(name: String, block: CompilerPipelineSpecBuilder.() -> Unit): CompilerPipelineSpec =
    CompilerPipelineSpecBuilder(name).apply(block).build()

/**
 * compiler pipeline 规范 builder，记录阶段、结果类型、短路规则和 diagnostics。
 * Compiler pipeline spec builder recording phases, result types, stop rules, and diagnostics.
 */
@LanguageSpecDsl
class CompilerPipelineSpecBuilder(private val name: String) {
    private val phases = mutableListOf<String>()
    private val resultTypes = mutableListOf<String>()
    private val stopRules = mutableListOf<String>()
    private val diagnostics = mutableListOf<DiagnosticSpec>()

    /**
     * 记录一个编译阶段。
     * Records one compiler phase.
     */
    fun phase(name: String) {
        phases += name
    }

    /**
     * 记录一个可观察结果类型。
     * Records one observable result type.
     */
    fun resultType(name: String) {
        resultTypes += name
    }

    /**
     * 记录一个阶段短路规则。
     * Records one phase stop rule.
     */
    fun stopRule(name: String) {
        stopRules += name
    }

    /**
     * 记录一个 compiler pipeline diagnostic。
     * Records one compiler pipeline diagnostic.
     */
    fun diagnostic(code: String, message: String) {
        diagnostics += DiagnosticSpec(code, message)
    }

    /**
     * 构造不可变 compiler pipeline 规范。
     * Builds the immutable compiler pipeline spec.
     */
    fun build(): CompilerPipelineSpec =
        CompilerPipelineSpec(
            name = name,
            phases = phases.toList(),
            resultTypes = resultTypes.toList(),
            stopRules = stopRules.toList(),
            diagnostics = diagnostics.toList(),
        )
}

val minimalCompilerPipelineSpec = compilerPipelineSpec("minimal-compiler-pipeline") {
    phase("lexing")
    phase("parsing")
    phase("type checking")
    phase("lowering")

    resultType("CompiledProgram")
    resultType("CompilationResult.Success")
    resultType("CompilationResult.Failure")

    stopRule("lexer diagnostics stop before parsing")
    stopRule("parser diagnostics stop before type checking")
    stopRule("type checker diagnostics stop before lowering")
    stopRule("lowering diagnostics stop before execution")

    diagnostic("COMPILER001", "internal compiler contract violation")
}
