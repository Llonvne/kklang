package cn.llonvne.kklang.spec

data class ExecutionSpec(
    val name: String,
    val phases: List<String>,
    val irNodes: List<String>,
    val supportedForms: List<String>,
    val diagnostics: List<DiagnosticSpec>,
)

fun executionSpec(name: String, block: ExecutionSpecBuilder.() -> Unit): ExecutionSpec =
    ExecutionSpecBuilder(name).apply(block).build()

@LanguageSpecDsl
class ExecutionSpecBuilder(private val name: String) {
    private val phases = mutableListOf<String>()
    private val irNodes = mutableListOf<String>()
    private val supportedForms = mutableListOf<String>()
    private val diagnostics = mutableListOf<DiagnosticSpec>()

    fun phase(name: String) {
        phases += name
    }

    fun irNode(name: String) {
        irNodes += name
    }

    fun supportedForm(name: String) {
        supportedForms += name
    }

    fun diagnostic(code: String, message: String) {
        diagnostics += DiagnosticSpec(code, message)
    }

    fun build(): ExecutionSpec =
        ExecutionSpec(
            name = name,
            phases = phases.toList(),
            irNodes = irNodes.toList(),
            supportedForms = supportedForms.toList(),
            diagnostics = diagnostics.toList(),
        )
}

val minimalExecutionSpec = executionSpec("minimal-execution") {
    phase("lexer")
    phase("parser")
    phase("core ir lowering")
    phase("core ir evaluation")

    irNode("IrInt64")
    irNode("IrUnary")
    irNode("IrBinary")

    supportedForm("integer literal")
    supportedForm("grouped expression")
    supportedForm("unary plus")
    supportedForm("unary minus")
    supportedForm("binary plus")
    supportedForm("binary minus")
    supportedForm("binary multiply")
    supportedForm("binary divide")

    diagnostic("EXEC001", "unsupported expression")
    diagnostic("EXEC002", "division by zero")
    diagnostic("EXEC003", "Int64 overflow")
}

