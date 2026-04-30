package cn.llonvne.kklang.frontend.lexing

import cn.llonvne.kklang.frontend.SourceSpan
import cn.llonvne.kklang.frontend.SourceText
import cn.llonvne.kklang.frontend.diagnostics.Diagnostic
import cn.llonvne.kklang.frontend.diagnostics.DiagnosticBag

data class LexResult(
    val tokens: List<Token>,
    val diagnostics: List<Diagnostic>,
) {
    val hasErrors: Boolean
        get() = diagnostics.isNotEmpty()
}

class Lexer(
    private val config: LexerConfig = LexerConfig.default(),
) {
    fun tokenize(source: SourceText): LexResult {
        val tokens = mutableListOf<Token>()
        val diagnostics = DiagnosticBag()
        var offset = 0

        while (offset < source.length) {
            val match = selectMatch(source, offset)
            if (match == null) {
                val span = SourceSpan(source.name, offset, offset + 1)
                tokens += Token(TokenKinds.Unknown, source.slice(offset, offset + 1), span)
                diagnostics.report("LEX001", "unknown character", span)
                offset += 1
            } else {
                val endOffset = offset + match.length
                val span = SourceSpan(source.name, offset, endOffset)
                if (match.kind != TokenKinds.Whitespace || config.emitTrivia) {
                    tokens += Token(match.kind, source.slice(offset, endOffset), span)
                }
                offset = endOffset
            }
        }

        tokens += Token(TokenKinds.EndOfFile, "", SourceSpan(source.name, source.length, source.length))
        return LexResult(tokens = tokens, diagnostics = diagnostics.toList())
    }

    private fun selectMatch(source: SourceText, offset: Int): TokenMatch? {
        var best: TokenMatch? = null
        for (rule in config.rules) {
            val candidate = rule.match(source, offset)
            if (candidate != null && (best == null || candidate.length > best.length)) {
                best = candidate
            }
        }
        return best
    }
}

