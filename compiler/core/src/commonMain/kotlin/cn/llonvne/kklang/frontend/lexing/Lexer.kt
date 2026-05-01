package cn.llonvne.kklang.frontend.lexing

import cn.llonvne.kklang.frontend.SourceSpan
import cn.llonvne.kklang.frontend.SourceText
import cn.llonvne.kklang.frontend.diagnostics.Diagnostic
import cn.llonvne.kklang.frontend.diagnostics.DiagnosticBag

/**
 * lexer 输出，包含 token 流和词法 diagnostics。
 * Lexer output containing the token stream and lexical diagnostics.
 */
data class LexResult(
    val tokens: List<Token>,
    val diagnostics: List<Diagnostic>,
) {
    val hasErrors: Boolean
        get() = diagnostics.isNotEmpty()
}

/**
 * 默认 lexer 实现，按配置规则执行最长匹配和未知字符诊断。
 * Default lexer implementation that applies configured rules with longest-match selection and unknown-character diagnostics.
 */
class Lexer(
    private val config: LexerConfig = LexerConfig.default(),
) {
    /**
     * 将源码转换为 token 流，并始终追加 EOF token。
     * Converts source into a token stream and always appends an EOF token.
     */
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

    /**
     * 在指定 offset 选择最长匹配；长度相同时保留最早注册规则。
     * Selects the longest match at the given offset and keeps the earliest registered rule on ties.
     */
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
