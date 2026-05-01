package cn.llonvne.kklang.runtime

/**
 * Native runtime backend 的可执行 DSL 规范快照。
 * Executable DSL spec snapshot for the Native runtime backend.
 */
data class RuntimeBackendSpec(
    val name: String,
    val engineTypes: List<String>,
    val valueTypes: List<String>,
    val materializers: List<String>,
    val failureSources: List<String>,
)

/**
 * 创建 Native runtime backend DSL 规范。
 * Creates a Native runtime backend DSL spec.
 */
fun runtimeBackendSpec(name: String, block: RuntimeBackendSpecBuilder.() -> Unit): RuntimeBackendSpec =
    RuntimeBackendSpecBuilder(name).apply(block).build()

/**
 * Native runtime backend 规范 builder。
 * Native runtime backend spec builder.
 */
class RuntimeBackendSpecBuilder(private val name: String) {
    private val engineTypes = mutableListOf<String>()
    private val valueTypes = mutableListOf<String>()
    private val materializers = mutableListOf<String>()
    private val failureSources = mutableListOf<String>()

    /**
     * 记录一个 backend engine/result 类型。
     * Records one backend engine or result type.
     */
    fun engineType(name: String) {
        engineTypes += name
    }

    /**
     * 记录一个 runtime-backed value 类型。
     * Records one runtime-backed value type.
     */
    fun valueType(name: String) {
        valueTypes += name
    }

    /**
     * 记录一个 C ABI materializer。
     * Records one C ABI materializer.
     */
    fun materializer(name: String) {
        materializers += name
    }

    /**
     * 记录一个 backend 失败来源。
     * Records one backend failure source.
     */
    fun failureSource(name: String) {
        failureSources += name
    }

    /**
     * 构造不可变 Native runtime backend 规范。
     * Builds the immutable Native runtime backend spec.
     */
    fun build(): RuntimeBackendSpec =
        RuntimeBackendSpec(
            name = name,
            engineTypes = engineTypes.toList(),
            valueTypes = valueTypes.toList(),
            materializers = materializers.toList(),
            failureSources = failureSources.toList(),
        )
}

val minimalRuntimeBackendSpec = runtimeBackendSpec("minimal-native-runtime-backend") {
    engineType("KkRuntimeExecutionEngine")
    engineType("KkRuntimeExecutionResult")

    valueType("KkValue")
    valueType("KkValue.Int64")

    materializer("kk_value_int64")

    failureSource("compiler diagnostics")
    failureSource("evaluator diagnostics")
}
