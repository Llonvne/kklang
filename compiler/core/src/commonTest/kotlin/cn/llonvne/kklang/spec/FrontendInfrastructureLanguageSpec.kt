package cn.llonvne.kklang.spec

/**
 * 语言规范 DSL 的 marker，防止 builder scope 意外混用。
 * Marker for the language spec DSL that prevents accidental builder scope mixing.
 */
@DslMarker
annotation class LanguageSpecDsl

/**
 * frontend 基础设施的可执行 DSL 规范快照。
 * Executable DSL spec snapshot for frontend infrastructure.
 */
data class LanguageSpec(
    val name: String,
    val guidingPrinciples: List<String>,
    val lexerTokens: List<LexerTokenSpec>,
    val parserRules: List<ParserRuleSpec>,
    val diagnostics: List<DiagnosticSpec>,
)

/**
 * lexer token 规范项。
 * Lexer token spec item.
 */
data class LexerTokenSpec(val kind: String, val rule: String)

/**
 * parser rule 规范项。
 * Parser rule spec item.
 */
data class ParserRuleSpec(
    val name: String,
    val tokenKind: String,
    val precedence: Int? = null,
    val associativity: String? = null,
)

/**
 * diagnostic 规范项。
 * Diagnostic spec item.
 */
data class DiagnosticSpec(val code: String, val message: String)

/**
 * 创建 frontend language DSL 规范。
 * Creates a frontend language DSL spec.
 */
fun languageSpec(name: String, block: LanguageSpecBuilder.() -> Unit): LanguageSpec =
    LanguageSpecBuilder(name).apply(block).build()

/**
 * frontend language 规范 builder。
 * Frontend language spec builder.
 */
@LanguageSpecDsl
class LanguageSpecBuilder(private val name: String) {
    private val guidingPrinciples = mutableListOf<String>()
    private val lexerTokens = mutableListOf<LexerTokenSpec>()
    private val parserRules = mutableListOf<ParserRuleSpec>()
    private val diagnostics = mutableListOf<DiagnosticSpec>()

    /**
     * 记录一条指导原则。
     * Records one guiding principle.
     */
    fun guidingPrinciple(text: String) {
        guidingPrinciples += text
    }

    /**
     * 进入 lexer 规范 builder。
     * Enters the lexer spec builder.
     */
    fun lexer(block: LexerSpecBuilder.() -> Unit) {
        LexerSpecBuilder(lexerTokens).apply(block)
    }

    /**
     * 进入 parser 规范 builder。
     * Enters the parser spec builder.
     */
    fun parser(block: ParserSpecBuilder.() -> Unit) {
        ParserSpecBuilder(parserRules).apply(block)
    }

    /**
     * 记录一个 frontend diagnostic。
     * Records one frontend diagnostic.
     */
    fun diagnostic(code: String, message: String) {
        diagnostics += DiagnosticSpec(code, message)
    }

    /**
     * 构造不可变 frontend language 规范。
     * Builds the immutable frontend language spec.
     */
    fun build(): LanguageSpec =
        LanguageSpec(
            name = name,
            guidingPrinciples = guidingPrinciples.toList(),
            lexerTokens = lexerTokens.toList(),
            parserRules = parserRules.toList(),
            diagnostics = diagnostics.toList(),
        )
}

/**
 * lexer token 规范 builder。
 * Lexer token spec builder.
 */
@LanguageSpecDsl
class LexerSpecBuilder(private val tokens: MutableList<LexerTokenSpec>) {
    /**
     * 记录一个 token kind 和匹配规则说明。
     * Records one token kind and its matching rule description.
     */
    fun token(kind: String, rule: String) {
        tokens += LexerTokenSpec(kind, rule)
    }
}

/**
 * parser rule 规范 builder。
 * Parser rule spec builder.
 */
@LanguageSpecDsl
class ParserSpecBuilder(private val rules: MutableList<ParserRuleSpec>) {
    /**
     * 记录一个 prefix parse rule。
     * Records one prefix parse rule.
     */
    fun prefix(name: String, tokenKind: String, precedence: Int? = null) {
        rules += ParserRuleSpec(name, tokenKind, precedence = precedence)
    }

    /**
     * 记录一个 infix parse rule。
     * Records one infix parse rule.
     */
    fun infix(name: String, tokenKind: String, precedence: Int, associativity: String) {
        rules += ParserRuleSpec(name, tokenKind, precedence = precedence, associativity = associativity)
    }
}

val frontendInfrastructureSpec = languageSpec("frontend-infrastructure") {
    guidingPrinciple("user approves high-level language direction and guiding principles")
    guidingPrinciple("Codex owns detailed implementation design after approved guiding principles")
    guidingPrinciple("Markdown spec and Kotlin DSL spec are written before implementation")

    lexer {
        token("identifier", "ASCII letter or underscore, followed by ASCII letters, digits, or underscores")
        token("integer", "one or more ASCII digits")
        token("left_paren", "(")
        token("right_paren", ")")
        token("plus", "+")
        token("minus", "-")
        token("star", "*")
        token("slash", "/")
        token("whitespace", "one or more Kotlin whitespace characters")
        token("unknown", "one unrecognized character")
        token("eof", "zero-length synthetic end token")
    }

    parser {
        prefix("identifier expression", "identifier")
        prefix("integer expression", "integer")
        prefix("grouped expression", "left_paren")
        prefix("prefix plus", "plus", precedence = 30)
        prefix("prefix minus", "minus", precedence = 30)
        infix("add", "plus", precedence = 10, associativity = "left")
        infix("subtract", "minus", precedence = 10, associativity = "left")
        infix("multiply", "star", precedence = 20, associativity = "left")
        infix("divide", "slash", precedence = 20, associativity = "left")
    }

    diagnostic("LEX001", "unknown character")
    diagnostic("PARSE001", "expected expression")
    diagnostic("PARSE002", "unexpected trailing token")
    diagnostic("PARSE003", "expected token")
}
