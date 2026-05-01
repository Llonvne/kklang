package cn.llonvne.kklang.frontend.diagnostics

import cn.llonvne.kklang.frontend.SourceSpan

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

class DiagnosticBag {
    private val diagnostics = mutableListOf<Diagnostic>()

    fun report(code: String, message: String, span: SourceSpan): Diagnostic {
        val diagnostic = Diagnostic(code = code, message = message, span = span)
        diagnostics += diagnostic
        return diagnostic
    }

    fun toList(): List<Diagnostic> = diagnostics.toList()
}

