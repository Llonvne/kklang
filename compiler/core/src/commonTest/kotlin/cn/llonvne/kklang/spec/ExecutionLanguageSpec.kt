package cn.llonvne.kklang.spec

/**
 * execution 层的可执行 DSL 规范快照。
 * Executable DSL spec snapshot for the execution layer.
 */
data class ExecutionSpec(
    val name: String,
    val phases: List<String>,
    val irNodes: List<String>,
    val supportedForms: List<String>,
    val diagnostics: List<DiagnosticSpec>,
)

/**
 * 创建 execution DSL 规范。
 * Creates an execution DSL spec.
 */
fun executionSpec(name: String, block: ExecutionSpecBuilder.() -> Unit): ExecutionSpec =
    ExecutionSpecBuilder(name).apply(block).build()

/**
 * execution 规范 builder，记录阶段、IR 节点、可执行形式和 diagnostics。
 * Execution spec builder recording phases, IR nodes, executable forms, and diagnostics.
 */
@LanguageSpecDsl
class ExecutionSpecBuilder(private val name: String) {
    private val phases = mutableListOf<String>()
    private val irNodes = mutableListOf<String>()
    private val supportedForms = mutableListOf<String>()
    private val diagnostics = mutableListOf<DiagnosticSpec>()

    /**
     * 记录 execution pipeline 阶段。
     * Records one execution pipeline phase.
     */
    fun phase(name: String) {
        phases += name
    }

    /**
     * 记录一个 Core IR 节点名。
     * Records one Core IR node name.
     */
    fun irNode(name: String) {
        irNodes += name
    }

    /**
     * 记录当前支持执行的源码形式。
     * Records one currently executable source form.
     */
    fun supportedForm(name: String) {
        supportedForms += name
    }

    /**
     * 记录一个 execution diagnostic。
     * Records one execution diagnostic.
     */
    fun diagnostic(code: String, message: String) {
        diagnostics += DiagnosticSpec(code, message)
    }

    /**
     * 构造不可变 execution 规范。
     * Builds the immutable execution spec.
     */
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
    phase("modifier expansion")
    phase("binding")
    phase("type checking")
    phase("core ir lowering")
    phase("core ir evaluation")

    irNode("IrProgram")
    irNode("IrValDeclaration")
    irNode("IrFunctionDeclaration")
    irNode("IrInt64")
    irNode("IrString")
    irNode("IrPrint")
    irNode("IrVariable")
    irNode("IrCall")
    irNode("IrUnary")
    irNode("IrBinary")

    supportedForm("immutable val declaration")
    supportedForm("top-level function declaration")
    supportedForm("function call")
    supportedForm("identifier reference")
    supportedForm("integer literal")
    supportedForm("string literal")
    supportedForm("builtin print call")
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
