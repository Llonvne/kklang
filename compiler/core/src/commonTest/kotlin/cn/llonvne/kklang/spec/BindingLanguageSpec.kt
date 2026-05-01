package cn.llonvne.kklang.spec

/**
 * binding 层的可执行 DSL 规范快照。
 * Executable DSL spec snapshot for the binding layer.
 */
data class BindingSpec(
    val name: String,
    val syntaxes: List<String>,
    val scopeRules: List<String>,
    val diagnostics: List<DiagnosticSpec>,
)

/**
 * 创建 binding DSL 规范。
 * Creates a binding DSL spec.
 */
fun bindingSpec(name: String, block: BindingSpecBuilder.() -> Unit): BindingSpec =
    BindingSpecBuilder(name).apply(block).build()

/**
 * binding 规范 builder，记录语法、作用域规则和 diagnostics。
 * Binding spec builder recording syntax, scope rules, and diagnostics.
 */
@LanguageSpecDsl
class BindingSpecBuilder(private val name: String) {
    private val syntaxes = mutableListOf<String>()
    private val scopeRules = mutableListOf<String>()
    private val diagnostics = mutableListOf<DiagnosticSpec>()

    /**
     * 记录一个 binding 语法表面。
     * Records one binding syntax surface.
     */
    fun syntax(name: String) {
        syntaxes += name
    }

    /**
     * 记录一条 binding scope 规则。
     * Records one binding scope rule.
     */
    fun scopeRule(name: String) {
        scopeRules += name
    }

    /**
     * 记录一个 binding diagnostic。
     * Records one binding diagnostic.
     */
    fun diagnostic(code: String, message: String) {
        diagnostics += DiagnosticSpec(code, message)
    }

    /**
     * 构造不可变 binding 规范。
     * Builds the immutable binding spec.
     */
    fun build(): BindingSpec =
        BindingSpec(
            name = name,
            syntaxes = syntaxes.toList(),
            scopeRules = scopeRules.toList(),
            diagnostics = diagnostics.toList(),
        )
}

val minimalBindingSpec = bindingSpec("minimal-binding") {
    syntax("val declaration")
    syntax("val identifier = expression;")

    scopeRule("binding resolver runs before type checking")
    scopeRule("binding resolver emits BoundProgram")
    scopeRule("val bindings are immutable")
    scopeRule("initializer can reference earlier vals")
    scopeRule("initializer cannot reference itself or later vals")
    scopeRule("unresolved identifiers are rejected")
    scopeRule("same-scope duplicate val is rejected")
    scopeRule("assignment expression is not part of the grammar")

    diagnostic("BIND001", "duplicate immutable value")
    diagnostic("TYPE001", "unresolved identifier")
}
