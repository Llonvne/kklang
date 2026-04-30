package cn.llonvne.kklang.spec

@DslMarker
annotation class LanguageSpecDsl

data class LanguageSpec(
    val name: String,
    val guidingPrinciples: List<String>,
    val lexerTokens: List<LexerTokenSpec>,
    val parserRules: List<ParserRuleSpec>,
    val diagnostics: List<DiagnosticSpec>,
)

data class LexerTokenSpec(val kind: String, val rule: String)

data class ParserRuleSpec(
    val name: String,
    val tokenKind: String,
    val precedence: Int? = null,
    val associativity: String? = null,
)

data class DiagnosticSpec(val code: String, val message: String)

fun languageSpec(name: String, block: LanguageSpecBuilder.() -> Unit): LanguageSpec =
    LanguageSpecBuilder(name).apply(block).build()

@LanguageSpecDsl
class LanguageSpecBuilder(private val name: String) {
    private val guidingPrinciples = mutableListOf<String>()
    private val lexerTokens = mutableListOf<LexerTokenSpec>()
    private val parserRules = mutableListOf<ParserRuleSpec>()
    private val diagnostics = mutableListOf<DiagnosticSpec>()

    fun guidingPrinciple(text: String) {
        guidingPrinciples += text
    }

    fun lexer(block: LexerSpecBuilder.() -> Unit) {
        LexerSpecBuilder(lexerTokens).apply(block)
    }

    fun parser(block: ParserSpecBuilder.() -> Unit) {
        ParserSpecBuilder(parserRules).apply(block)
    }

    fun diagnostic(code: String, message: String) {
        diagnostics += DiagnosticSpec(code, message)
    }

    fun build(): LanguageSpec =
        LanguageSpec(
            name = name,
            guidingPrinciples = guidingPrinciples.toList(),
            lexerTokens = lexerTokens.toList(),
            parserRules = parserRules.toList(),
            diagnostics = diagnostics.toList(),
        )
}

@LanguageSpecDsl
class LexerSpecBuilder(private val tokens: MutableList<LexerTokenSpec>) {
    fun token(kind: String, rule: String) {
        tokens += LexerTokenSpec(kind, rule)
    }
}

@LanguageSpecDsl
class ParserSpecBuilder(private val rules: MutableList<ParserRuleSpec>) {
    fun prefix(name: String, tokenKind: String, precedence: Int? = null) {
        rules += ParserRuleSpec(name, tokenKind, precedence = precedence)
    }

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

