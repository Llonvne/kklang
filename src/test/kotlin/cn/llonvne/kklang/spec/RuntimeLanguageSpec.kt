package cn.llonvne.kklang.spec

data class RuntimeSpec(
    val name: String,
    val guidingPrinciples: List<String>,
    val statuses: List<RuntimeStatusSpec>,
    val abiFunctions: List<RuntimeFunctionSpec>,
    val valueTags: List<RuntimeValueTagSpec>,
    val wrapperTypes: List<RuntimeWrapperSpec>,
)

data class RuntimeStatusSpec(val name: String, val meaning: String)

data class RuntimeFunctionSpec(
    val name: String,
    val ownership: String,
    val invalidArguments: String,
)

data class RuntimeValueTagSpec(val name: String, val meaning: String)

data class RuntimeWrapperSpec(val name: String, val rule: String)

fun runtimeSpec(name: String, block: RuntimeSpecBuilder.() -> Unit): RuntimeSpec =
    RuntimeSpecBuilder(name).apply(block).build()

@LanguageSpecDsl
class RuntimeSpecBuilder(private val name: String) {
    private val guidingPrinciples = mutableListOf<String>()
    private val statuses = mutableListOf<RuntimeStatusSpec>()
    private val abiFunctions = mutableListOf<RuntimeFunctionSpec>()
    private val valueTags = mutableListOf<RuntimeValueTagSpec>()
    private val wrapperTypes = mutableListOf<RuntimeWrapperSpec>()

    fun guidingPrinciple(text: String) {
        guidingPrinciples += text
    }

    fun status(name: String, meaning: String) {
        statuses += RuntimeStatusSpec(name, meaning)
    }

    fun abiFunction(name: String, ownership: String, invalidArguments: String) {
        abiFunctions += RuntimeFunctionSpec(name, ownership, invalidArguments)
    }

    fun valueTag(name: String, meaning: String) {
        valueTags += RuntimeValueTagSpec(name, meaning)
    }

    fun wrapper(name: String, rule: String) {
        wrapperTypes += RuntimeWrapperSpec(name, rule)
    }

    fun build(): RuntimeSpec =
        RuntimeSpec(
            name = name,
            guidingPrinciples = guidingPrinciples.toList(),
            statuses = statuses.toList(),
            abiFunctions = abiFunctions.toList(),
            valueTags = valueTags.toList(),
            wrapperTypes = wrapperTypes.toList(),
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
}
