package cn.llonvne.kklang.spec

/**
 * type system 层的可执行 DSL 规范快照。
 * Executable DSL spec snapshot for the type-system layer.
 */
data class TypeSystemSpec(
    val name: String,
    val phases: List<String>,
    val types: List<String>,
    val typedNodes: List<String>,
    val supportedForms: List<String>,
    val bindingRules: List<String>,
    val diagnostics: List<DiagnosticSpec>,
)

/**
 * 创建 type system DSL 规范。
 * Creates a type-system DSL spec.
 */
fun typeSystemSpec(name: String, block: TypeSystemSpecBuilder.() -> Unit): TypeSystemSpec =
    TypeSystemSpecBuilder(name).apply(block).build()

/**
 * type system 规范 builder，记录阶段、类型、typed AST 节点、支持表面和 diagnostics。
 * Type-system spec builder recording phases, types, typed AST nodes, supported surface, and diagnostics.
 */
@LanguageSpecDsl
class TypeSystemSpecBuilder(private val name: String) {
    private val phases = mutableListOf<String>()
    private val types = mutableListOf<String>()
    private val typedNodes = mutableListOf<String>()
    private val supportedForms = mutableListOf<String>()
    private val bindingRules = mutableListOf<String>()
    private val diagnostics = mutableListOf<DiagnosticSpec>()

    /**
     * 记录一个 type-system pipeline 阶段。
     * Records one type-system pipeline phase.
     */
    fun phase(name: String) {
        phases += name
    }

    /**
     * 记录一个类型名。
     * Records one type name.
     */
    fun type(name: String) {
        types += name
    }

    /**
     * 记录一个 typed AST 节点名。
     * Records one typed AST node name.
     */
    fun typedNode(name: String) {
        typedNodes += name
    }

    /**
     * 记录当前支持类型检查的源码形式。
     * Records one currently type-checkable source form.
     */
    fun supportedForm(name: String) {
        supportedForms += name
    }

    /**
     * 记录一个类型检查期绑定规则。
     * Records one type-check-time binding rule.
     */
    fun bindingRule(name: String) {
        bindingRules += name
    }

    /**
     * 记录一个 type-system diagnostic。
     * Records one type-system diagnostic.
     */
    fun diagnostic(code: String, message: String) {
        diagnostics += DiagnosticSpec(code, message)
    }

    /**
     * 构造不可变 type-system 规范。
     * Builds the immutable type-system spec.
     */
    fun build(): TypeSystemSpec =
        TypeSystemSpec(
            name = name,
            phases = phases.toList(),
            types = types.toList(),
            typedNodes = typedNodes.toList(),
            supportedForms = supportedForms.toList(),
            bindingRules = bindingRules.toList(),
            diagnostics = diagnostics.toList(),
        )
}

val minimalTypeSystemSpec = typeSystemSpec("minimal-type-system") {
    phase("type checking")

    type("TypeRef.Int64")

    typedNode("TypedProgram")
    typedNode("TypedValDeclaration")
    typedNode("TypedExpression")
    typedNode("TypedInteger")
    typedNode("TypedVariable")
    typedNode("TypedGrouped")
    typedNode("TypedPrefix")
    typedNode("TypedBinary")

    supportedForm("val declaration")
    supportedForm("identifier reference")
    supportedForm("integer literal")
    supportedForm("grouped expression")
    supportedForm("unary plus")
    supportedForm("unary minus")
    supportedForm("binary plus")
    supportedForm("binary minus")
    supportedForm("binary multiply")
    supportedForm("binary divide")

    bindingRule("val declaration binds initializer type")
    bindingRule("identifier reference uses bound val type")

    diagnostic("BIND001", "duplicate immutable value")
    diagnostic("TYPE001", "unresolved identifier")
    diagnostic("TYPE002", "unsupported expression")
}
