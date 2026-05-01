package cn.llonvne.kklang.spec

data class CompilerPipelineSpec(
    val name: String,
    val phases: List<String>,
    val resultTypes: List<String>,
    val stopRules: List<String>,
    val diagnostics: List<DiagnosticSpec>,
)

fun compilerPipelineSpec(name: String, block: CompilerPipelineSpecBuilder.() -> Unit): CompilerPipelineSpec =
    CompilerPipelineSpecBuilder(name).apply(block).build()

@LanguageSpecDsl
class CompilerPipelineSpecBuilder(private val name: String) {
    private val phases = mutableListOf<String>()
    private val resultTypes = mutableListOf<String>()
    private val stopRules = mutableListOf<String>()
    private val diagnostics = mutableListOf<DiagnosticSpec>()

    fun phase(name: String) {
        phases += name
    }

    fun resultType(name: String) {
        resultTypes += name
    }

    fun stopRule(name: String) {
        stopRules += name
    }

    fun diagnostic(code: String, message: String) {
        diagnostics += DiagnosticSpec(code, message)
    }

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
    phase("lowering")

    resultType("CompiledProgram")
    resultType("CompilationResult.Success")
    resultType("CompilationResult.Failure")

    stopRule("lexer diagnostics stop before parsing")
    stopRule("parser diagnostics stop before lowering")
    stopRule("lowering diagnostics stop before execution")

    diagnostic("COMPILER001", "internal compiler contract violation")
}
