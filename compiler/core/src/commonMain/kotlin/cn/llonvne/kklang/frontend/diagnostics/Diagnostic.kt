package cn.llonvne.kklang.frontend.diagnostics

import cn.llonvne.kklang.frontend.SourceSpan

/**
 * 一个带 code、message 和源码 span 的编译诊断。
 * A compiler diagnostic carrying a code, message, and source span.
 */
data class Diagnostic(
    val code: String,
    val message: String,
    val span: SourceSpan,
) {
    init {
        require(code.isNotBlank()) { "diagnostic code must not be blank" }
        require(message.isNotBlank()) { "diagnostic message must not be blank" }
    }
}

/**
 * 按产生顺序收集 diagnostics 的可变容器。
 * Mutable container that collects diagnostics in emission order.
 */
class DiagnosticBag {
    private val diagnostics = mutableListOf<Diagnostic>()

    /**
     * 创建并记录一个 diagnostic。
     * Creates and records one diagnostic.
     */
    fun report(code: String, message: String, span: SourceSpan): Diagnostic {
        val diagnostic = Diagnostic(code = code, message = message, span = span)
        diagnostics += diagnostic
        return diagnostic
    }

    /**
     * 返回当前 diagnostics 的不可变快照。
     * Returns an immutable snapshot of the current diagnostics.
     */
    fun toList(): List<Diagnostic> = diagnostics.toList()
}
