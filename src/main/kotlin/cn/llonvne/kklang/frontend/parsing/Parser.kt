package cn.llonvne.kklang.frontend.parsing

import cn.llonvne.kklang.frontend.SourceSpan
import cn.llonvne.kklang.frontend.diagnostics.Diagnostic
import cn.llonvne.kklang.frontend.diagnostics.DiagnosticBag
import cn.llonvne.kklang.frontend.lexing.Token
import cn.llonvne.kklang.frontend.lexing.TokenKind
import cn.llonvne.kklang.frontend.lexing.TokenKinds

data class ParseResult(
    val expression: Expression,
    val diagnostics: List<Diagnostic>,
) {
    val hasErrors: Boolean
        get() = diagnostics.isNotEmpty()
}

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

    fun parseExpressionDocument(): ParseResult {
        val expression = parseExpression()
        while (current.kind != TokenKinds.EndOfFile) {
            diagnostics.report("PARSE002", "unexpected trailing token", current.span)
            advance()
        }
        return ParseResult(expression = expression, diagnostics = diagnostics.toList())
    }

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

    fun expect(kind: TokenKind, code: String, message: String): Token {
        if (current.kind == kind) {
            return advance()
        }

        diagnostics.report(code, message, current.span)
        return Token(kind = kind, lexeme = "", span = SourceSpan(current.span.sourceName, current.span.startOffset, current.span.startOffset))
    }

    private val current: Token
        get() = tokens[position]

    private fun advance(): Token {
        val token = current
        if (position < tokens.lastIndex) {
            position += 1
        }
        return token
    }
}

