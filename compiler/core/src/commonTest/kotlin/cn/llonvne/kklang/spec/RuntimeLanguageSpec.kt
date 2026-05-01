package cn.llonvne.kklang.spec

/**
 * runtime 层的可执行 DSL 规范快照。
 * Executable DSL spec snapshot for the runtime layer.
 */
data class RuntimeSpec(
    val name: String,
    val guidingPrinciples: List<String>,
    val statuses: List<RuntimeStatusSpec>,
    val abiFunctions: List<RuntimeFunctionSpec>,
    val valueTags: List<RuntimeValueTagSpec>,
    val wrapperTypes: List<RuntimeWrapperSpec>,
    val debugRules: List<String>,
)

/**
 * C runtime status 的规范项。
 * Spec item for a C runtime status.
 */
data class RuntimeStatusSpec(val name: String, val meaning: String)

/**
 * C runtime ABI 函数的规范项。
 * Spec item for a C runtime ABI function.
 */
data class RuntimeFunctionSpec(
    val name: String,
    val ownership: String,
    val invalidArguments: String,
)

/**
 * C runtime value tag 的规范项。
 * Spec item for a C runtime value tag.
 */
data class RuntimeValueTagSpec(val name: String, val meaning: String)

/**
 * Kotlin/Native runtime wrapper 类型的规范项。
 * Spec item for a Kotlin/Native runtime wrapper type.
 */
data class RuntimeWrapperSpec(val name: String, val rule: String)

/**
 * 创建 runtime DSL 规范。
 * Creates a runtime DSL spec.
 */
fun runtimeSpec(name: String, block: RuntimeSpecBuilder.() -> Unit): RuntimeSpec =
    RuntimeSpecBuilder(name).apply(block).build()

/**
 * runtime 规范 builder，记录 ABI、status、value tag、wrapper 和调试规则。
 * Runtime spec builder recording ABI, status, value tag, wrapper, and debug rules.
 */
@LanguageSpecDsl
class RuntimeSpecBuilder(private val name: String) {
    private val guidingPrinciples = mutableListOf<String>()
    private val statuses = mutableListOf<RuntimeStatusSpec>()
    private val abiFunctions = mutableListOf<RuntimeFunctionSpec>()
    private val valueTags = mutableListOf<RuntimeValueTagSpec>()
    private val wrapperTypes = mutableListOf<RuntimeWrapperSpec>()
    private val debugRules = mutableListOf<String>()

    /**
     * 记录一条 runtime 指导原则。
     * Records one runtime guiding principle.
     */
    fun guidingPrinciple(text: String) {
        guidingPrinciples += text
    }

    /**
     * 记录一个 C runtime status。
     * Records one C runtime status.
     */
    fun status(name: String, meaning: String) {
        statuses += RuntimeStatusSpec(name, meaning)
    }

    /**
     * 记录一个 C ABI 函数及其所有权/非法参数规则。
     * Records one C ABI function with its ownership and invalid-argument rules.
     */
    fun abiFunction(name: String, ownership: String, invalidArguments: String) {
        abiFunctions += RuntimeFunctionSpec(name, ownership, invalidArguments)
    }

    /**
     * 记录一个 runtime value tag。
     * Records one runtime value tag.
     */
    fun valueTag(name: String, meaning: String) {
        valueTags += RuntimeValueTagSpec(name, meaning)
    }

    /**
     * 记录一个 Kotlin/Native wrapper 类型规则。
     * Records one Kotlin/Native wrapper type rule.
     */
    fun wrapper(name: String, rule: String) {
        wrapperTypes += RuntimeWrapperSpec(name, rule)
    }

    /**
     * 记录一条 runtime 调试规则。
     * Records one runtime debug rule.
     */
    fun debugRule(text: String) {
        debugRules += text
    }

    /**
     * 构造不可变 runtime 规范。
     * Builds the immutable runtime spec.
     */
    fun build(): RuntimeSpec =
        RuntimeSpec(
            name = name,
            guidingPrinciples = guidingPrinciples.toList(),
            statuses = statuses.toList(),
            abiFunctions = abiFunctions.toList(),
            valueTags = valueTags.toList(),
            wrapperTypes = wrapperTypes.toList(),
            debugRules = debugRules.toList(),
        )
}

val minimalRuntimeSpec = runtimeSpec("minimal-runtime") {
    guidingPrinciple("C layer provides a stable ABI")
    guidingPrinciple("Kotlin Native layer provides a type-safe wrapper")
    guidingPrinciple("runtime defines mechanisms only, not high-level language semantics")

    status("KK_OK", "operation succeeded")
    status("KK_ERR_OOM", "memory allocation failed")
    status("KK_ERR_INVALID_ARGUMENT", "invalid argument")

    abiFunction("kk_runtime_create", "returns runtime ownership through an out parameter", "null out parameter")
    abiFunction("kk_runtime_destroy", "consumes runtime ownership", "null runtime")
    abiFunction("kk_string_new", "returns string ownership through an out parameter", "null runtime, utf8, or out parameter")
    abiFunction("kk_string_size", "reads string byte size", "null string or out parameter")
    abiFunction("kk_string_data", "reads runtime-owned string data", "null string or out parameter")
    abiFunction("kk_string_release", "consumes string ownership", "null runtime or string, or string owned by another runtime")

    valueTag("KK_VALUE_UNIT", "Unit value")
    valueTag("KK_VALUE_BOOL", "Boolean value")
    valueTag("KK_VALUE_INT64", "64-bit signed integer")
    valueTag("KK_VALUE_STRING", "runtime-owned string reference")
    valueTag("KK_VALUE_OBJECT_REF", "reserved object reference")

    wrapper("KkRuntime", "does not expose raw C pointers, closes idempotently, and closes child strings")
    wrapper("KkString", "does not expose raw C pointers and rejects use after close")
    wrapper("KkValue", "type-safe Kotlin Native representation of runtime-backed values")
    wrapper("KkRuntimeExecutionEngine", "compiles, evaluates, and materializes successful values through the runtime backend")
    wrapper("KkRuntimeExecutionResult", "returns runtime-backed success values or original compiler and evaluator diagnostics")

    debugRule("C debug symbols for Kotlin Native host tests")
    debugRule("printRuntimeHostDebugCommand")
    debugRule("target modules add")
}
