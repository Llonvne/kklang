package cn.llonvne.kklang.binding

import cn.llonvne.kklang.frontend.SourceSpan
import cn.llonvne.kklang.frontend.diagnostics.Diagnostic
import cn.llonvne.kklang.frontend.diagnostics.DiagnosticBag
import cn.llonvne.kklang.frontend.parsing.AstProgram
import cn.llonvne.kklang.frontend.parsing.BinaryExpression
import cn.llonvne.kklang.frontend.parsing.Expression
import cn.llonvne.kklang.frontend.parsing.GroupedExpression
import cn.llonvne.kklang.frontend.parsing.IdentifierExpression
import cn.llonvne.kklang.frontend.parsing.IntegerExpression
import cn.llonvne.kklang.frontend.parsing.MissingExpression
import cn.llonvne.kklang.frontend.parsing.PrefixExpression
import cn.llonvne.kklang.frontend.parsing.ValDeclaration

/**
 * binding 阶段创建的不可变符号。
 * Immutable symbol created by the binding phase.
 */
data class BindingSymbol(
    val name: String,
    val declaration: ValDeclaration,
)

/**
 * 当前 binding 作用域的有序符号表。
 * Ordered symbol table for the current binding scope.
 */
class BindingScope private constructor(
    private val symbolsByName: LinkedHashMap<String, BindingSymbol>,
) {
    /**
     * 按成功定义顺序返回当前作用域中的符号。
     * Returns symbols in the current scope in successful definition order.
     */
    val symbols: List<BindingSymbol>
        get() = symbolsByName.values.toList()

    /**
     * 定义一个符号；同名符号已存在时返回 false 且不替换原符号。
     * Defines one symbol; returns false and keeps the original symbol when the name already exists.
     */
    fun define(symbol: BindingSymbol): Boolean {
        if (symbolsByName.containsKey(symbol.name)) {
            return false
        }
        symbolsByName[symbol.name] = symbol
        return true
    }

    /**
     * 解析当前作用域中已经定义的符号。
     * Resolves a symbol already defined in the current scope.
     */
    fun resolve(name: String): BindingSymbol? =
        symbolsByName[name]

    /**
     * BindingScope 的工厂入口。
     * Factory entry point for BindingScope.
     */
    companion object {
        /**
         * 创建空的当前作用域。
         * Creates an empty current scope.
         */
        fun empty(): BindingScope =
            BindingScope(LinkedHashMap())
    }
}

/**
 * binding 后 expression 的共同接口，保留原始 AST 和来源 span。
 * Shared interface for bound expressions, preserving original AST and source span.
 */
sealed interface BoundExpression {
    val syntax: Expression
    val span: SourceSpan
}

/**
 * binding 后的整数字面量。
 * Bound integer literal.
 */
data class BoundInteger(
    override val syntax: IntegerExpression,
) : BoundExpression {
    override val span: SourceSpan
        get() = syntax.span
}

/**
 * binding 后的变量引用，携带解析到的符号。
 * Bound variable reference carrying the resolved symbol.
 */
data class BoundVariable(
    override val syntax: IdentifierExpression,
    val symbol: BindingSymbol,
) : BoundExpression {
    override val span: SourceSpan
        get() = syntax.span
}

/**
 * binding 后的分组 expression。
 * Bound grouped expression.
 */
data class BoundGrouped(
    override val syntax: GroupedExpression,
    val inner: BoundExpression,
) : BoundExpression {
    override val span: SourceSpan
        get() = syntax.span
}

/**
 * binding 后的 prefix expression。
 * Bound prefix expression.
 */
data class BoundPrefix(
    override val syntax: PrefixExpression,
    val operand: BoundExpression,
) : BoundExpression {
    override val span: SourceSpan
        get() = syntax.span
}

/**
 * binding 后的 binary expression。
 * Bound binary expression.
 */
data class BoundBinary(
    override val syntax: BinaryExpression,
    val left: BoundExpression,
    val right: BoundExpression,
) : BoundExpression {
    override val span: SourceSpan
        get() = syntax.span
}

/**
 * binding 后保留的 parser recovery missing expression。
 * Bound parser-recovery missing expression.
 */
data class BoundMissing(
    override val syntax: MissingExpression,
) : BoundExpression {
    override val span: SourceSpan
        get() = syntax.span
}

/**
 * binding 后的不可变 val declaration。
 * Immutable val declaration after binding.
 */
data class BoundValDeclaration(
    val syntax: ValDeclaration,
    val symbol: BindingSymbol,
    val initializer: BoundExpression,
) {
    val name: String
        get() = symbol.name
}

/**
 * binding 后的 program，保留原始 AST、声明、最终表达式和有序符号。
 * Bound program preserving the original AST, declarations, final expression, and ordered symbols.
 */
data class BoundProgram(
    val syntax: AstProgram,
    val declarations: List<BoundValDeclaration>,
    val expression: BoundExpression,
    val symbols: List<BindingSymbol>,
) {
    val span: SourceSpan
        get() = syntax.span
}

/**
 * binding resolver 的结果，成功时 program 非空，失败时 diagnostics 非空。
 * Binding-resolver result; program is present on success and diagnostics are present on failure.
 */
data class BindingResult(
    val program: BoundProgram?,
    val diagnostics: List<Diagnostic>,
) {
    val hasErrors: Boolean
        get() = diagnostics.isNotEmpty()
}

/**
 * AST program 到 BoundProgram 的可替换 binding 接口。
 * Replaceable binding interface from AST programs to BoundProgram.
 */
fun interface BindingResolver {
    /**
     * 解析一个 AST program 的名字绑定并返回 BoundProgram 或 diagnostics。
     * Resolves name bindings for one AST program and returns either BoundProgram or diagnostics.
     */
    fun resolve(program: AstProgram): BindingResult
}

/**
 * 当前最小 binding resolver，支持单一 program scope 和不可变 val。
 * Current minimal binding resolver supporting one program scope and immutable vals.
 */
class SeedBindingResolver : BindingResolver {
    /**
     * 解析 program 的 val 声明顺序、重复声明和 identifier 引用。
     * Resolves val declaration order, duplicate declarations, and identifier references in a program.
     */
    override fun resolve(program: AstProgram): BindingResult {
        val diagnostics = DiagnosticBag()
        val scope = BindingScope.empty()
        val boundDeclarations = mutableListOf<BoundValDeclaration>()

        for (declaration in program.declarations) {
            val duplicate = scope.resolve(declaration.name) != null
            val initializer = bindExpression(declaration.initializer, scope, diagnostics)
            if (duplicate) {
                diagnostics.report("BIND001", "duplicate immutable value", declaration.nameToken.span)
            }
            if (!duplicate && initializer != null) {
                val symbol = BindingSymbol(name = declaration.name, declaration = declaration)
                scope.define(symbol)
                boundDeclarations += BoundValDeclaration(syntax = declaration, symbol = symbol, initializer = initializer)
            }
        }

        val expression = bindExpression(program.expression, scope, diagnostics)

        val diagnosticsList = diagnostics.toList()
        if (diagnosticsList.isNotEmpty()) {
            return BindingResult(program = null, diagnostics = diagnosticsList)
        }
        return BindingResult(
            program = BoundProgram(
                syntax = program,
                declarations = boundDeclarations.toList(),
                expression = expression!!,
                symbols = scope.symbols,
            ),
            diagnostics = diagnosticsList,
        )
    }

    /**
     * 按 expression 类型遍历并生成 bound expression。
     * Walks by expression type and builds a bound expression.
     */
    private fun bindExpression(
        expression: Expression,
        scope: BindingScope,
        diagnostics: DiagnosticBag,
    ): BoundExpression? =
        when (expression) {
            is IntegerExpression -> BoundInteger(expression)
            is IdentifierExpression -> bindIdentifier(expression, scope, diagnostics)
            is GroupedExpression -> bindGrouped(expression, scope, diagnostics)
            is PrefixExpression -> bindPrefix(expression, scope, diagnostics)
            is BinaryExpression -> {
                val left = bindExpression(expression.left, scope, diagnostics)
                val right = bindExpression(expression.right, scope, diagnostics)
                if (left == null || right == null) null else BoundBinary(expression, left, right)
            }
            is MissingExpression -> BoundMissing(expression)
        }

    /**
     * binding 分组 expression，并传播内部失败。
     * Binds a grouped expression and propagates inner failure.
     */
    private fun bindGrouped(
        expression: GroupedExpression,
        scope: BindingScope,
        diagnostics: DiagnosticBag,
    ): BoundExpression? {
        val inner = bindExpression(expression.expression, scope, diagnostics) ?: return null
        return BoundGrouped(syntax = expression, inner = inner)
    }

    /**
     * binding prefix expression，并传播 operand 失败。
     * Binds a prefix expression and propagates operand failure.
     */
    private fun bindPrefix(
        expression: PrefixExpression,
        scope: BindingScope,
        diagnostics: DiagnosticBag,
    ): BoundExpression? {
        val operand = bindExpression(expression.operand, scope, diagnostics) ?: return null
        return BoundPrefix(syntax = expression, operand = operand)
    }

    /**
     * binding identifier；未绑定时报告当前公开的 TYPE001。
     * Binds an identifier; reports the current public TYPE001 when it is unbound.
     */
    private fun bindIdentifier(
        expression: IdentifierExpression,
        scope: BindingScope,
        diagnostics: DiagnosticBag,
    ): BoundExpression? {
        val symbol = scope.resolve(expression.name)
        if (symbol != null) {
            return BoundVariable(syntax = expression, symbol = symbol)
        }
        diagnostics.report("TYPE001", "unresolved identifier", expression.span)
        return null
    }
}
