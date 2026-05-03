package cn.llonvne.kklang.metaprogramming

import cn.llonvne.kklang.frontend.SourceSpan
import cn.llonvne.kklang.frontend.diagnostics.Diagnostic
import cn.llonvne.kklang.frontend.diagnostics.DiagnosticBag
import cn.llonvne.kklang.frontend.lexing.Token
import cn.llonvne.kklang.frontend.lexing.TokenKinds
import cn.llonvne.kklang.frontend.parsing.AstProgram
import cn.llonvne.kklang.frontend.parsing.Declaration
import cn.llonvne.kklang.frontend.parsing.FunctionBody
import cn.llonvne.kklang.frontend.parsing.FunctionDeclaration
import cn.llonvne.kklang.frontend.parsing.FunctionParameter
import cn.llonvne.kklang.frontend.parsing.ModifierDeclaration
import cn.llonvne.kklang.frontend.parsing.Parser
import cn.llonvne.kklang.frontend.parsing.RawModifierApplication
import cn.llonvne.kklang.frontend.parsing.ValDeclaration

/**
 * modifier expansion 结果，成功时 program 非空，失败时 diagnostics 非空。
 * Modifier-expansion result; program is present on success and diagnostics are present on failure.
 */
data class ModifierExpansionResult(
    val program: AstProgram?,
    val diagnostics: List<Diagnostic>,
) {
    val hasErrors: Boolean
        get() = diagnostics.isNotEmpty()
}

/**
 * AST program 到 expanded AST program 的可替换 modifier expansion 接口。
 * Replaceable modifier-expansion interface from AST programs to expanded AST programs.
 */
fun interface ModifierExpander {
    /**
     * 展开一个 AST program 中的 raw modifier applications。
     * Expands raw modifier applications in one AST program.
     */
    fun expand(program: AstProgram): ModifierExpansionResult
}

/**
 * 第一版 modifier expander，只支持 canonical `fn` modifier。
 * First modifier expander supporting only the canonical `fn` modifier.
 */
class SeedModifierExpander : ModifierExpander {
    /**
     * 展开 program 中的 modifier declarations 和 raw applications。
     * Expands modifier declarations and raw applications in a program.
     */
    override fun expand(program: AstProgram): ModifierExpansionResult {
        val diagnostics = DiagnosticBag()
        val sourceModifierNames = mutableSetOf<String>()
        val expandedDeclarations = mutableListOf<Declaration>()

        for (declaration in program.declarations) {
            when (declaration) {
                is ValDeclaration -> expandedDeclarations += declaration
                is FunctionDeclaration -> expandedDeclarations += declaration
                is ModifierDeclaration -> registerModifier(declaration, sourceModifierNames, diagnostics)
                is RawModifierApplication -> expandRawApplication(declaration, diagnostics)?.let(expandedDeclarations::add)
            }
        }

        val diagnosticsList = diagnostics.toList()
        if (diagnosticsList.isNotEmpty()) {
            return ModifierExpansionResult(program = null, diagnostics = diagnosticsList)
        }
        return ModifierExpansionResult(
            program = AstProgram(expression = program.expression, declarations = expandedDeclarations.toList()),
            diagnostics = diagnosticsList,
        )
    }

    /**
     * 注册或校验源码中的 modifier declaration。
     * Registers or validates a modifier declaration from source.
     */
    private fun registerModifier(
        declaration: ModifierDeclaration,
        sourceModifierNames: MutableSet<String>,
        diagnostics: DiagnosticBag,
    ) {
        if (declaration.name != FN_MODIFIER_NAME) {
            diagnostics.report("MOD001", "unknown modifier", declaration.nameToken.span)
            return
        }
        if (!sourceModifierNames.add(declaration.name)) {
            diagnostics.report("MOD002", "duplicate modifier declaration", declaration.nameToken.span)
            return
        }
        val pattern = declaration.patternTokens.map { it.lexeme }.filter { it.isNotBlank() }
        if (pattern != FN_PATTERN_LEXEMES) {
            diagnostics.report("MOD003", "modifier pattern mismatch", declaration.span)
        }
    }

    /**
     * 展开 raw modifier application。
     * Expands one raw modifier application.
     */
    private fun expandRawApplication(
        application: RawModifierApplication,
        diagnostics: DiagnosticBag,
    ): FunctionDeclaration? {
        if (application.name != FN_MODIFIER_NAME) {
            diagnostics.report("MOD001", "unknown modifier application", application.nameToken.span)
            return null
        }
        return FnApplicationParser(application.tokens, diagnostics).parse()
    }

    /**
     * `fn` modifier application 的小型结构化 parser。
     * Small structured parser for `fn` modifier applications.
     */
    private class FnApplicationParser(
        private val tokens: List<Token>,
        private val diagnostics: DiagnosticBag,
    ) {
        private var position = 0

        /**
         * 解析完整 `fn` application。
         * Parses the full `fn` application.
         */
        fun parse(): FunctionDeclaration? {
            val fnToken = expectIdentifier(FN_MODIFIER_NAME) ?: return null
            val nameToken = expect(TokenKinds.Identifier, "expected function name") ?: return null
            val leftParen = expect(TokenKinds.LeftParen, "expected left_paren") ?: return null
            val parameters = parseParameters() ?: return null
            val rightParen = expect(TokenKinds.RightParen, "expected right_paren") ?: return null
            val leftBrace = expect(TokenKinds.LeftBrace, "expected left_brace") ?: return null
            val bodyTokens = collectBodyTokens() ?: return null
            val rightBrace = previous()
            val body = parseBody(leftBrace, bodyTokens, rightBrace) ?: return null
            if (!isAtEnd()) {
                diagnostics.report("MOD003", "trailing tokens in fn application", current().span)
                return null
            }
            return FunctionDeclaration(
                modifierToken = fnToken,
                nameToken = nameToken,
                leftParenToken = leftParen,
                parameters = parameters,
                rightParenToken = rightParen,
                body = body,
            )
        }

        /**
         * 解析函数参数列表。
         * Parses the function parameter list.
         */
        private fun parseParameters(): List<FunctionParameter>? {
            val parameters = mutableListOf<FunctionParameter>()
            if (peek(TokenKinds.RightParen)) {
                return parameters
            }
            while (true) {
                val name = expect(TokenKinds.Identifier, "expected parameter name") ?: return null
                var colon: Token? = null
                var type: Token? = null
                if (peek(TokenKinds.Colon)) {
                    colon = advance()
                    type = expect(TokenKinds.Identifier, "expected type annotation") ?: return null
                }
                parameters += FunctionParameter(nameToken = name, colonToken = colon, typeToken = type)
                if (!peek(TokenKinds.Comma)) {
                    break
                }
                advance()
            }
            return parameters
        }

        /**
         * 收集函数体 outer braces 内的 tokens。
         * Collects tokens inside the function-body outer braces.
         */
        private fun collectBodyTokens(): List<Token>? {
            val bodyTokens = mutableListOf<Token>()
            var depth = 1
            while (!isAtEnd()) {
                val token = advance()
                when (token.kind) {
                    TokenKinds.LeftBrace -> {
                        depth += 1
                        bodyTokens += token
                    }
                    TokenKinds.RightBrace -> {
                        depth -= 1
                        if (depth == 0) {
                            return bodyTokens.toList()
                        }
                        bodyTokens += token
                    }
                    else -> bodyTokens += token
                }
            }
            diagnostics.report("MOD003", "missing function body right brace", previous().span)
            return null
        }

        /**
         * 使用普通 parser 解析函数体 block program。
         * Parses a function-body block program using the normal parser.
         */
        private fun parseBody(
            leftBrace: Token,
            bodyTokens: List<Token>,
            rightBrace: Token,
        ): FunctionBody? {
            val parserTokens = bodyTokens + eofToken(rightBrace.span)
            val parseResult = Parser(parserTokens).parseProgramDocument()
            for (diagnostic in parseResult.diagnostics) {
                diagnostics.report(diagnostic.code, diagnostic.message, diagnostic.span)
            }
            val declarations = mutableListOf<ValDeclaration>()
            for (declaration in parseResult.program.declarations) {
                if (declaration is ValDeclaration) {
                    declarations += declaration
                } else {
                    diagnostics.report("MOD003", "function body only supports val declarations", declaration.span)
                }
            }
            if (parseResult.hasErrors || diagnostics.toList().isNotEmpty()) {
                return null
            }
            return FunctionBody(
                leftBraceToken = leftBrace,
                declarations = declarations.toList(),
                expression = parseResult.program.expression,
                rightBraceToken = rightBrace,
            )
        }

        /**
         * 消费期望的 token kind。
         * Consumes the expected token kind.
         */
        private fun expect(kind: cn.llonvne.kklang.frontend.lexing.TokenKind, message: String): Token? {
            if (peek(kind)) {
                return advance()
            }
            diagnostics.report("MOD003", message, current().span)
            return null
        }

        /**
         * 消费期望 lexeme 的 identifier。
         * Consumes an identifier with the expected lexeme.
         */
        private fun expectIdentifier(lexeme: String): Token? {
            val token = expect(TokenKinds.Identifier, "expected identifier") ?: return null
            if (token.lexeme != lexeme) {
                diagnostics.report("MOD003", "expected $lexeme modifier", token.span)
                return null
            }
            return token
        }

        /**
         * 判断当前位置 token kind。
         * Checks the current token kind.
         */
        private fun peek(kind: cn.llonvne.kklang.frontend.lexing.TokenKind): Boolean =
            !isAtEnd() && current().kind == kind

        /**
         * 返回当前 token。
         * Returns the current token.
         */
        private fun current(): Token =
            tokens[position.coerceAtMost(tokens.lastIndex)]

        /**
         * 返回上一个已消费 token。
         * Returns the previously consumed token.
         */
        private fun previous(): Token =
            tokens[(position - 1).coerceAtLeast(0)]

        /**
         * 消费并返回当前 token。
         * Consumes and returns the current token.
         */
        private fun advance(): Token {
            val token = current()
            position += 1
            return token
        }

        /**
         * 判断是否已经消费完整 application token 序列。
         * Checks whether the full application token sequence has been consumed.
         */
        private fun isAtEnd(): Boolean =
            position >= tokens.size
    }

    companion object {
        private const val FN_MODIFIER_NAME = "fn"
        private val FN_PATTERN_LEXEMES = listOf(
            "[",
            "modifiers",
            "]",
            "fn",
            "[",
            "identifier",
            "]",
            "(",
            "[",
            "identifier",
            ":",
            "type",
            "?",
            "]",
            ")",
            "{",
            "[",
            "body",
            "]",
            "}",
        )

        /**
         * 创建位于给定 span 起点的 EOF token。
         * Creates an EOF token at the start of the given span.
         */
        private fun eofToken(span: SourceSpan): Token =
            Token(TokenKinds.EndOfFile, "", SourceSpan(span.sourceName, span.startOffset, span.startOffset))
    }
}
