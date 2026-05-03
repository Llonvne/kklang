package cn.llonvne.kklang.binding

import cn.llonvne.kklang.frontend.SourceSpan
import cn.llonvne.kklang.frontend.diagnostics.Diagnostic
import cn.llonvne.kklang.frontend.diagnostics.DiagnosticBag
import cn.llonvne.kklang.frontend.parsing.AstProgram
import cn.llonvne.kklang.frontend.parsing.BinaryExpression
import cn.llonvne.kklang.frontend.parsing.CallExpression
import cn.llonvne.kklang.frontend.parsing.Declaration
import cn.llonvne.kklang.frontend.parsing.Expression
import cn.llonvne.kklang.frontend.parsing.FunctionDeclaration
import cn.llonvne.kklang.frontend.parsing.FunctionParameter
import cn.llonvne.kklang.frontend.parsing.GroupedExpression
import cn.llonvne.kklang.frontend.parsing.IdentifierExpression
import cn.llonvne.kklang.frontend.parsing.IntegerExpression
import cn.llonvne.kklang.frontend.parsing.MissingExpression
import cn.llonvne.kklang.frontend.parsing.ModifierDeclaration
import cn.llonvne.kklang.frontend.parsing.PrefixExpression
import cn.llonvne.kklang.frontend.parsing.RawModifierApplication
import cn.llonvne.kklang.frontend.parsing.SymbolSyntax
import cn.llonvne.kklang.frontend.parsing.StringExpression
import cn.llonvne.kklang.frontend.parsing.ValDeclaration

/**
 * binding 符号的来源种类。
 * Source kind for a binding symbol.
 */
enum class BindingSymbolKind {
    Val,
    Function,
    Parameter,
}

/**
 * binding 阶段创建的不可变符号。
 * Immutable symbol created by the binding phase.
 */
data class BindingSymbol(
    val name: String,
    val declaration: SymbolSyntax,
    val kind: BindingSymbolKind = BindingSymbolKind.Val,
)

/**
 * 当前 binding 作用域的有序符号表。
 * Ordered symbol table for the current binding scope.
 */
class BindingScope private constructor(
    private val symbolsByName: LinkedHashMap<String, BindingSymbol>,
    private val parent: BindingScope?,
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
        symbolsByName[name] ?: parent?.resolve(name)

    /**
     * 创建以当前 scope 为 parent 的子作用域。
     * Creates a child scope with this scope as its parent.
     */
    fun child(): BindingScope =
        BindingScope(LinkedHashMap(), this)

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
            BindingScope(LinkedHashMap(), parent = null)
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
 * binding 后的字符串字面量。
 * Bound string literal.
 */
data class BoundString(
    override val syntax: StringExpression,
) : BoundExpression {
    override val span: SourceSpan
        get() = syntax.span
}

/**
 * binding 后的内建 print 调用，携带已绑定 argument。
 * Bound builtin print call carrying its bound argument.
 */
data class BoundPrintCall(
    override val syntax: CallExpression,
    val arguments: List<BoundExpression>,
) : BoundExpression {
    constructor(syntax: CallExpression, argument: BoundExpression) : this(syntax, listOf(argument))

    val argument: BoundExpression
        get() = arguments.single()

    override val span: SourceSpan
        get() = syntax.span
}

/**
 * binding 后的函数调用，携带解析到的函数符号和已绑定参数。
 * Bound function call carrying the resolved function symbol and bound arguments.
 */
data class BoundFunctionCall(
    override val syntax: CallExpression,
    val symbol: BindingSymbol,
    val arguments: List<BoundExpression>,
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
 * binding 后 declaration 的共同接口。
 * Shared interface for declarations after binding.
 */
sealed interface BoundDeclaration {
    val name: String
}

/**
 * binding 后的不可变 val declaration。
 * Immutable val declaration after binding.
 */
data class BoundValDeclaration(
    val syntax: ValDeclaration,
    val symbol: BindingSymbol,
    val initializer: BoundExpression,
) : BoundDeclaration {
    override val name: String
        get() = symbol.name
}

/**
 * binding 后的函数参数。
 * Function parameter after binding.
 */
data class BoundFunctionParameter(
    val syntax: FunctionParameter,
    val symbol: BindingSymbol,
) {
    val name: String
        get() = symbol.name
}

/**
 * binding 后的函数体。
 * Function body after binding.
 */
data class BoundFunctionBody(
    val declarations: List<BoundValDeclaration>,
    val expression: BoundExpression,
)

/**
 * binding 后的顶层函数声明。
 * Top-level function declaration after binding.
 */
data class BoundFunctionDeclaration(
    val syntax: FunctionDeclaration,
    val symbol: BindingSymbol,
    val parameters: List<BoundFunctionParameter>,
    val body: BoundFunctionBody,
) : BoundDeclaration {
    override val name: String
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
    val functions: List<BoundFunctionDeclaration> = emptyList(),
    val orderedDeclarations: List<BoundDeclaration> = declarations + functions,
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
 * 当前最小 binding resolver，支持 program scope、不可变 val 和顺序绑定函数。
 * Current minimal binding resolver supporting a program scope, immutable vals, and source-ordered functions.
 */
class SeedBindingResolver : BindingResolver {
    /**
     * 解析 program 的声明顺序、重复声明和 identifier 引用。
     * Resolves declaration order, duplicate declarations, and identifier references in a program.
     */
    override fun resolve(program: AstProgram): BindingResult {
        val diagnostics = DiagnosticBag()
        val scope = BindingScope.empty()
        val boundDeclarations = mutableListOf<BoundValDeclaration>()
        val boundFunctions = mutableListOf<BoundFunctionDeclaration>()
        val orderedDeclarations = mutableListOf<BoundDeclaration>()

        for (declaration in program.declarations) {
            when (declaration) {
                is ValDeclaration -> bindValDeclaration(declaration, scope, diagnostics)?.let {
                    boundDeclarations += it
                    orderedDeclarations += it
                }
                is FunctionDeclaration -> bindFunctionDeclaration(declaration, scope, diagnostics)?.let {
                    boundFunctions += it
                    orderedDeclarations += it
                }
                is ModifierDeclaration,
                is RawModifierApplication,
                -> diagnostics.report("BIND001", "unexpanded modifier declaration", declaration.span)
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
                functions = boundFunctions.toList(),
                orderedDeclarations = orderedDeclarations.toList(),
            ),
            diagnostics = diagnosticsList,
        )
    }

    /**
     * binding 一个 val declaration，并在成功后写入当前 scope。
     * Binds one val declaration and writes it into the current scope on success.
     */
    private fun bindValDeclaration(
        declaration: ValDeclaration,
        scope: BindingScope,
        diagnostics: DiagnosticBag,
    ): BoundValDeclaration? {
        val duplicate = scope.resolve(declaration.name) != null
        val initializer = bindExpression(declaration.initializer, scope, diagnostics)
        if (duplicate) {
            diagnostics.report("BIND001", "duplicate immutable value", declaration.nameToken.span)
        }
        if (duplicate || initializer == null) {
            return null
        }
        val symbol = BindingSymbol(name = declaration.name, declaration = declaration, kind = BindingSymbolKind.Val)
        scope.define(symbol)
        return BoundValDeclaration(syntax = declaration, symbol = symbol, initializer = initializer)
    }

    /**
     * binding 一个函数声明；函数体在函数名进入 scope 之前解析，因此禁止递归。
     * Binds one function declaration; the body is resolved before the function name enters scope, which forbids recursion.
     */
    private fun bindFunctionDeclaration(
        declaration: FunctionDeclaration,
        scope: BindingScope,
        diagnostics: DiagnosticBag,
    ): BoundFunctionDeclaration? {
        val duplicate = scope.resolve(declaration.name) != null
        val functionScope = scope.child()
        val parameters = bindParameters(declaration.parameters, functionScope, diagnostics)
        val bodyDeclarations = mutableListOf<BoundValDeclaration>()
        for (localDeclaration in declaration.body.declarations) {
            bindValDeclaration(localDeclaration, functionScope, diagnostics)?.let(bodyDeclarations::add)
        }
        val bodyExpression = bindExpression(declaration.body.expression, functionScope, diagnostics)
        if (duplicate) {
            diagnostics.report("BIND001", "duplicate function", declaration.nameToken.span)
        }
        if (duplicate || bodyExpression == null || diagnostics.toList().isNotEmpty()) {
            return null
        }
        val symbol = BindingSymbol(name = declaration.name, declaration = declaration, kind = BindingSymbolKind.Function)
        scope.define(symbol)
        return BoundFunctionDeclaration(
            syntax = declaration,
            symbol = symbol,
            parameters = parameters,
            body = BoundFunctionBody(declarations = bodyDeclarations.toList(), expression = bodyExpression),
        )
    }

    /**
     * binding 函数参数并检测同一参数列表中的重复名字。
     * Binds function parameters and detects duplicate names in the same parameter list.
     */
    private fun bindParameters(
        parameters: List<FunctionParameter>,
        scope: BindingScope,
        diagnostics: DiagnosticBag,
    ): List<BoundFunctionParameter> {
        val boundParameters = mutableListOf<BoundFunctionParameter>()
        for (parameter in parameters) {
            val symbol = BindingSymbol(name = parameter.name, declaration = parameter, kind = BindingSymbolKind.Parameter)
            if (!scope.define(symbol)) {
                diagnostics.report("BIND001", "duplicate function parameter", parameter.nameToken.span)
            } else {
                boundParameters += BoundFunctionParameter(syntax = parameter, symbol = symbol)
            }
        }
        return boundParameters.toList()
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
            is StringExpression -> BoundString(expression)
            is CallExpression -> bindCall(expression, scope, diagnostics)
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
     * binding 内建 `print(argument)` 或已解析函数调用。
     * Binds builtin `print(argument)` or a resolved function call.
     */
    private fun bindCall(
        expression: CallExpression,
        scope: BindingScope,
        diagnostics: DiagnosticBag,
    ): BoundExpression? {
        val arguments = expression.arguments.mapNotNull { bindExpression(it, scope, diagnostics) }
        if (arguments.size != expression.arguments.size) {
            return null
        }
        if (expression.callee.name == "print") {
            return BoundPrintCall(expression, arguments)
        }
        val symbol = scope.resolve(expression.callee.name)
        if (symbol == null) {
            diagnostics.report("TYPE001", "unresolved identifier", expression.callee.span)
            return null
        }
        return BoundFunctionCall(syntax = expression, symbol = symbol, arguments = arguments)
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
