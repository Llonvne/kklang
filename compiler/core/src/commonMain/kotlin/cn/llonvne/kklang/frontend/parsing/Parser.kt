package cn.llonvne.kklang.frontend.parsing

import cn.llonvne.kklang.frontend.SourceSpan
import cn.llonvne.kklang.frontend.diagnostics.Diagnostic
import cn.llonvne.kklang.frontend.diagnostics.DiagnosticBag
import cn.llonvne.kklang.frontend.lexing.Token
import cn.llonvne.kklang.frontend.lexing.TokenKind
import cn.llonvne.kklang.frontend.lexing.TokenKinds

/**
 * parser 输出，包含根 expression 和语法 diagnostics。
 * Parser output containing the root expression and syntax diagnostics.
 */
data class ParseResult(
    val expression: Expression,
    val diagnostics: List<Diagnostic>,
) {
    val hasErrors: Boolean
        get() = diagnostics.isNotEmpty()
}

/**
 * 基于 token 流的 Pratt parser，使用 ParserConfig 提供 prefix/infix parselet。
 * Pratt parser over a token stream using prefix and infix parselets from ParserConfig.
 */
class Parser(
    private val tokens: List<Token>,
    private val config: ParserConfig = ParserConfig.default(),
) {
    private val diagnostics = DiagnosticBag()
    private var position = 0

    init {
        require(tokens.isNotEmpty()) { "parser requires at least an EOF token" }
        require(tokens.last().kind == TokenKinds.EndOfFile) { "parser token stream must end with EOF" }
    }

    /**
     * 解析完整 expression 文档，并把 expression 后的多余 token 作为 PARSE002。
     * Parses a complete expression document and reports tokens after the expression as PARSE002.
     */
    fun parseExpressionDocument(): ParseResult {
        val expression = parseExpression()
        while (current.kind != TokenKinds.EndOfFile) {
            diagnostics.report("PARSE002", "unexpected trailing token", current.span)
            advance()
        }
        return ParseResult(expression = expression, diagnostics = diagnostics.toList())
    }

    /**
     * 以最小 binding power 解析 expression。
     * Parses an expression with the given minimum binding power.
     */
    fun parseExpression(minBindingPower: Int = 0): Expression {
        val token = advance()
        val prefix = config.prefix(token.kind)
        var left = if (prefix == null) {
            diagnostics.report("PARSE001", "expected expression", token.span)
            MissingExpression(token.span)
        } else {
            prefix.parse(this, token)
        }

        while (true) {
            val infix = config.infix(current.kind)
            if (infix == null || infix.precedence <= minBindingPower) {
                break
            }

            val operator = advance()
            left = infix.parse(this, left, operator)
        }

        return left
    }

    /**
     * 消费期望 token；缺失时报告 diagnostic 并返回零长度 synthetic token。
     * Consumes an expected token, or reports a diagnostic and returns a zero-length synthetic token when it is missing.
     */
    fun expect(kind: TokenKind, code: String, message: String): Token {
        if (current.kind == kind) {
            return advance()
        }

        diagnostics.report(code, message, current.span)
        return Token(kind = kind, lexeme = "", span = SourceSpan(current.span.sourceName, current.span.startOffset, current.span.startOffset))
    }

    private val current: Token
        get() = tokens[position]

    /**
     * 返回当前 token，并在尚未到达 EOF token 时前进。
     * Returns the current token and advances when the parser has not reached the EOF token.
     */
    private fun advance(): Token {
        val token = current
        if (position < tokens.lastIndex) {
            position += 1
        }
        return token
    }
}
