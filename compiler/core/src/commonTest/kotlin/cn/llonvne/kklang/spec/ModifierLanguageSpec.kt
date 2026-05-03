package cn.llonvne.kklang.spec

/**
 * modifier 元编程层的可执行 DSL 规范快照。
 * Executable DSL spec snapshot for the modifier metaprogramming layer.
 */
data class ModifierSpec(
    val name: String,
    val syntaxes: List<String>,
    val expansionRules: List<String>,
    val functionRules: List<String>,
    val diagnostics: List<DiagnosticSpec>,
)

/**
 * 创建 modifier DSL 规范。
 * Creates a modifier DSL spec.
 */
fun modifierSpec(name: String, block: ModifierSpecBuilder.() -> Unit): ModifierSpec =
    ModifierSpecBuilder(name).apply(block).build()

/**
 * modifier 规范 builder，记录语法、expansion、函数规则和 diagnostics。
 * Modifier spec builder recording syntax, expansion, function rules, and diagnostics.
 */
@LanguageSpecDsl
class ModifierSpecBuilder(private val name: String) {
    private val syntaxes = mutableListOf<String>()
    private val expansionRules = mutableListOf<String>()
    private val functionRules = mutableListOf<String>()
    private val diagnostics = mutableListOf<DiagnosticSpec>()

    /**
     * 记录一个 modifier 语法表面。
     * Records one modifier syntax surface.
     */
    fun syntax(name: String) {
        syntaxes += name
    }

    /**
     * 记录一条 modifier expansion 规则。
     * Records one modifier expansion rule.
     */
    fun expansionRule(name: String) {
        expansionRules += name
    }

    /**
     * 记录一条函数语义规则。
     * Records one function semantic rule.
     */
    fun functionRule(name: String) {
        functionRules += name
    }

    /**
     * 记录一个 modifier diagnostic。
     * Records one modifier diagnostic.
     */
    fun diagnostic(code: String, message: String) {
        diagnostics += DiagnosticSpec(code, message)
    }

    /**
     * 构造不可变 modifier 规范。
     * Builds the immutable modifier spec.
     */
    fun build(): ModifierSpec =
        ModifierSpec(
            name = name,
            syntaxes = syntaxes.toList(),
            expansionRules = expansionRules.toList(),
            functionRules = functionRules.toList(),
            diagnostics = diagnostics.toList(),
        )
}

val minimalModifierSpec = modifierSpec("minimal-modifier") {
    syntax("modifier declaration")
    syntax("fn modifier")
    syntax("RawModifierApplication")
    syntax("FunctionDeclaration")

    expansionRule("declarative modifier expansion")
    expansionRule("modifier expansion runs before binding")
    expansionRule("parameter type syntax is optional")

    functionRule("top-level named function")
    functionRule("functions are not first-class values")
    functionRule("function declarations bind in source order")
    functionRule("recursion and forward reference are forbidden")
    functionRule("function return type is inferred from body")

    diagnostic("MOD001", "unknown modifier application")
    diagnostic("MOD002", "duplicate modifier declaration")
    diagnostic("MOD003", "modifier pattern or application shape mismatch")
}
