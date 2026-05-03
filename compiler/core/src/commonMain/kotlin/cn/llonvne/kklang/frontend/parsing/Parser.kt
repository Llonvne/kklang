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
 * parser program 输出，包含 AST program 和语法 diagnostics。
 * Parser program output containing an AST program and syntax diagnostics.
 */
data class ProgramParseResult(
    val program: AstProgram,
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
     * 解析完整 program 文档：零个或多个 declaration 后接最终 expression。
     * Parses a complete program document: zero or more declarations followed by a final expression.
     */
    fun parseProgramDocument(): ProgramParseResult {
        val declarations = mutableListOf<Declaration>()
        while (isDeclarationStart()) {
            declarations += parseDeclaration()
        }

        val expression = parseExpression()
        while (current.kind != TokenKinds.EndOfFile) {
            diagnostics.report("PARSE002", "unexpected trailing token", current.span)
            advance()
        }
        return ProgramParseResult(
            program = AstProgram(expression = expression, declarations = declarations.toList()),
            diagnostics = diagnostics.toList(),
        )
    }

    /**
     * 判断当前位置是否开始一个顶层 declaration。
     * Checks whether the current position starts a top-level declaration.
     */
    private fun isDeclarationStart(): Boolean =
        current.kind == TokenKinds.Val ||
            current.kind == TokenKinds.Modifier ||
            (current.kind == TokenKinds.Identifier && hasIdentifierHeadedBlockAhead())

    /**
     * 解析一个顶层 declaration。
     * Parses one top-level declaration.
     */
    private fun parseDeclaration(): Declaration =
        when (current.kind) {
            TokenKinds.Val -> parseValDeclaration()
            TokenKinds.Modifier -> parseModifierDeclaration()
            else -> parseRawModifierApplication()
        }

    /**
     * 解析一个以分号结束的 val declaration。
     * Parses one semicolon-terminated val declaration.
     */
    fun parseValDeclaration(): ValDeclaration {
        val valToken = expect(TokenKinds.Val, "PARSE003", "expected val")
        val name = expect(TokenKinds.Identifier, "PARSE003", "expected identifier")
        val equals = expect(TokenKinds.Equals, "PARSE003", "expected equals")
        val initializer = parseExpression()
        val semicolon = expect(TokenKinds.Semicolon, "PARSE003", "expected semicolon")
        return ValDeclaration(
            valToken = valToken,
            nameToken = name,
            equalsToken = equals,
            initializer = initializer,
            semicolonToken = semicolon,
        )
    }

    /**
     * 解析 modifier declaration，并保留 outer braces 内的 pattern tokens。
     * Parses a modifier declaration and preserves pattern tokens inside the outer braces.
     */
    private fun parseModifierDeclaration(): ModifierDeclaration {
        val modifierToken = expect(TokenKinds.Modifier, "PARSE003", "expected modifier")
        val nameToken = expect(TokenKinds.Identifier, "PARSE003", "expected identifier")
        val leftBrace = expect(TokenKinds.LeftBrace, "PARSE003", "expected left_brace")
        val patternTokens = mutableListOf<Token>()
        var depth = 1
        var rightBrace: Token? = null
        while (current.kind != TokenKinds.EndOfFile && depth > 0) {
            val token = advance()
            when (token.kind) {
                TokenKinds.LeftBrace -> {
                    depth += 1
                    patternTokens += token
                }
                TokenKinds.RightBrace -> {
                    depth -= 1
                    if (depth == 0) {
                        rightBrace = token
                    } else {
                        patternTokens += token
                    }
                }
                else -> patternTokens += token
            }
        }
        val closing = rightBrace ?: expect(TokenKinds.RightBrace, "PARSE003", "expected right_brace")
        return ModifierDeclaration(
            modifierToken = modifierToken,
            nameToken = nameToken,
            leftBraceToken = leftBrace,
            patternTokens = patternTokens.toList(),
            rightBraceToken = closing,
        )
    }

    /**
     * 解析 identifier 开头且包含 block 的 raw modifier application。
     * Parses a raw modifier application that starts with an identifier and contains a block.
     */
    private fun parseRawModifierApplication(): RawModifierApplication {
        val applicationTokens = mutableListOf<Token>()
        val nameToken = expect(TokenKinds.Identifier, "PARSE003", "expected identifier")
        applicationTokens += nameToken
        var depth = 0
        var sawBrace = false
        while (current.kind != TokenKinds.EndOfFile) {
            val token = advance()
            applicationTokens += token
            when (token.kind) {
                TokenKinds.LeftBrace -> {
                    sawBrace = true
                    depth += 1
                }
                TokenKinds.RightBrace -> {
                    depth -= 1
                    if (sawBrace && depth == 0) {
                        break
                    }
                }
            }
        }
        return RawModifierApplication(nameToken = nameToken, tokens = applicationTokens.toList())
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
            if (left is IdentifierExpression && current.kind == TokenKinds.LeftParen && ParserConfig.CALL_PRECEDENCE > minBindingPower) {
                left = parseCall(left)
                continue
            }

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
     * 解析调用语法：identifier 后跟括号包裹的零个或多个 argument。
     * Parses call syntax: an identifier followed by zero or more parenthesized arguments.
     */
    private fun parseCall(callee: IdentifierExpression): CallExpression {
        val leftParen = expect(TokenKinds.LeftParen, "PARSE003", "expected left_paren")
        val arguments = mutableListOf<Expression>()
        if (current.kind != TokenKinds.RightParen) {
            while (true) {
                arguments += parseExpression()
                if (current.kind != TokenKinds.Comma) {
                    break
                }
                advance()
            }
        }
        val rightParen = expect(TokenKinds.RightParen, "PARSE003", "expected right_paren")
        return CallExpression(callee = callee, leftParen = leftParen, arguments = arguments.toList(), rightParen = rightParen)
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

    /**
     * 查找当前 identifier 后面是否存在顶层 block，以决定它是否是 raw modifier application。
     * Looks ahead for a top-level block after the current identifier to decide whether it is a raw modifier application.
     */
    private fun hasIdentifierHeadedBlockAhead(): Boolean {
        var index = position + 1
        var parenDepth = 0
        while (true) {
            val token = tokens[index]
            when (token.kind) {
                TokenKinds.EndOfFile,
                TokenKinds.Semicolon,
                -> return false
                TokenKinds.LeftParen -> parenDepth += 1
                TokenKinds.RightParen -> if (parenDepth > 0) parenDepth -= 1
                TokenKinds.LeftBrace -> return parenDepth == 0
            }
            index += 1
        }
    }
}
