package cn.llonvne.kklang.typechecking

import cn.llonvne.kklang.binding.BoundProgram
import cn.llonvne.kklang.binding.BoundBinary
import cn.llonvne.kklang.binding.BoundDeclaration
import cn.llonvne.kklang.binding.BoundExpression
import cn.llonvne.kklang.binding.BoundFunctionCall
import cn.llonvne.kklang.binding.BoundFunctionDeclaration
import cn.llonvne.kklang.binding.BoundFunctionParameter
import cn.llonvne.kklang.binding.BoundGrouped
import cn.llonvne.kklang.binding.BoundInteger
import cn.llonvne.kklang.binding.BoundMissing
import cn.llonvne.kklang.binding.BoundPrintCall
import cn.llonvne.kklang.binding.BoundPrefix
import cn.llonvne.kklang.binding.BoundValDeclaration
import cn.llonvne.kklang.binding.BoundVariable
import cn.llonvne.kklang.binding.BindingSymbol
import cn.llonvne.kklang.binding.BoundString
import cn.llonvne.kklang.binding.SeedBindingResolver
import cn.llonvne.kklang.frontend.diagnostics.Diagnostic
import cn.llonvne.kklang.frontend.diagnostics.DiagnosticBag
import cn.llonvne.kklang.frontend.lexing.TokenKinds
import cn.llonvne.kklang.frontend.parsing.AstProgram
import cn.llonvne.kklang.frontend.parsing.BinaryExpression
import cn.llonvne.kklang.frontend.parsing.CallExpression
import cn.llonvne.kklang.frontend.parsing.Expression
import cn.llonvne.kklang.frontend.parsing.GroupedExpression
import cn.llonvne.kklang.frontend.parsing.IdentifierExpression
import cn.llonvne.kklang.frontend.parsing.IntegerExpression
import cn.llonvne.kklang.frontend.parsing.MissingExpression
import cn.llonvne.kklang.frontend.parsing.PrefixExpression
import cn.llonvne.kklang.frontend.parsing.StringExpression
import cn.llonvne.kklang.frontend.parsing.ValDeclaration

/**
 * 当前类型系统可表达的类型引用。
 * Type references expressible by the current type system.
 */
sealed interface TypeRef {
    /**
     * 64 位有符号整数类型。
     * Signed 64-bit integer type.
     */
    data object Int64 : TypeRef

    /**
     * 字符串类型。
     * String type.
     */
    data object String : TypeRef

    /**
     * Unit 类型，只表示副作用完成。
     * Unit type that only represents completion of a side effect.
     */
    data object Unit : TypeRef

    /**
     * 函数类型，参数和返回值都是已解析类型引用。
     * Function type whose parameters and return value are resolved type references.
     */
    data class Function(val parameters: List<TypeRef>, val returns: TypeRef) : TypeRef
}

/**
 * 类型检查后的 expression 共同接口，保留原始 AST、类型和来源 span。
 * Shared interface for type-checked expressions, preserving original AST, type, and source span.
 */
sealed interface TypedExpression {
    val syntax: Expression
    val type: TypeRef
}

/**
 * 类型检查后的 program。
 * Type-checked program.
 */
data class TypedProgram(
    val declarations: List<TypedValDeclaration>,
    val expression: TypedExpression,
    val type: TypeRef = expression.type,
    val functions: List<TypedFunctionDeclaration> = emptyList(),
    val orderedDeclarations: List<TypedDeclaration> = declarations + functions,
)

/**
 * 类型检查后 declaration 的共同接口。
 * Shared interface for type-checked declarations.
 */
sealed interface TypedDeclaration {
    val name: String
    val type: TypeRef
}

/**
 * 类型检查后的不可变 val declaration。
 * Type-checked immutable val declaration.
 */
data class TypedValDeclaration(
    val syntax: ValDeclaration,
    val symbol: BindingSymbol,
    val initializer: TypedExpression,
    override val type: TypeRef = initializer.type,
) : TypedDeclaration {
    override val name: String
        get() = symbol.name
}

/**
 * 类型检查后的函数参数。
 * Type-checked function parameter.
 */
data class TypedFunctionParameter(
    val syntax: cn.llonvne.kklang.frontend.parsing.FunctionParameter,
    val symbol: BindingSymbol,
    override val type: TypeRef,
) : TypedDeclaration {
    override val name: String
        get() = symbol.name
}

/**
 * 类型检查后的顶层函数声明。
 * Type-checked top-level function declaration.
 */
data class TypedFunctionDeclaration(
    val syntax: cn.llonvne.kklang.frontend.parsing.FunctionDeclaration,
    val symbol: BindingSymbol,
    val parameters: List<TypedFunctionParameter>,
    val declarations: List<TypedValDeclaration>,
    val expression: TypedExpression,
    override val type: TypeRef.Function,
) : TypedDeclaration {
    override val name: String
        get() = symbol.name
}

/**
 * 类型检查后的整数字面量 expression。
 * Type-checked integer-literal expression.
 */
data class TypedInteger(
    override val syntax: IntegerExpression,
    override val type: TypeRef = TypeRef.Int64,
) : TypedExpression

/**
 * 类型检查后的字符串字面量 expression。
 * Type-checked string-literal expression.
 */
data class TypedString(
    override val syntax: StringExpression,
    override val type: TypeRef = TypeRef.String,
) : TypedExpression

/**
 * 类型检查后的内建 print 调用 expression。
 * Type-checked builtin print-call expression.
 */
data class TypedPrintCall(
    override val syntax: CallExpression,
    val argument: TypedExpression,
    override val type: TypeRef = TypeRef.Unit,
) : TypedExpression

/**
 * 类型检查后的函数调用 expression。
 * Type-checked function-call expression.
 */
data class TypedFunctionCall(
    override val syntax: CallExpression,
    val symbol: BindingSymbol,
    val arguments: List<TypedExpression>,
    override val type: TypeRef,
) : TypedExpression

/**
 * 类型检查后的变量引用 expression。
 * Type-checked variable-reference expression.
 */
data class TypedVariable(
    override val syntax: IdentifierExpression,
    val symbol: BindingSymbol,
    override val type: TypeRef,
) : TypedExpression

/**
 * 类型检查后的分组 expression，类型等于内部 expression 的类型。
 * Type-checked grouped expression whose type equals its inner expression type.
 */
data class TypedGrouped(
    override val syntax: GroupedExpression,
    val inner: TypedExpression,
    override val type: TypeRef = inner.type,
) : TypedExpression

/**
 * 类型检查后的一元 prefix expression。
 * Type-checked unary prefix expression.
 */
data class TypedPrefix(
    override val syntax: PrefixExpression,
    val operand: TypedExpression,
    override val type: TypeRef,
) : TypedExpression

/**
 * 类型检查后的二元 expression。
 * Type-checked binary expression.
 */
data class TypedBinary(
    override val syntax: BinaryExpression,
    val left: TypedExpression,
    val right: TypedExpression,
    override val type: TypeRef,
) : TypedExpression

/**
 * 类型检查结果，成功时 expression 非空，失败时 diagnostics 非空。
 * Type-checking result; expression is present on success and diagnostics are present on failure.
 */
data class TypeCheckResult(
    val expression: TypedExpression?,
    val diagnostics: List<Diagnostic>,
) {
    val hasErrors: Boolean
        get() = diagnostics.isNotEmpty()
}

/**
 * program 类型检查结果，成功时 program 非空，失败时 diagnostics 非空。
 * Program type-checking result; program is present on success and diagnostics are present on failure.
 */
data class ProgramTypeCheckResult(
    val program: TypedProgram?,
    val diagnostics: List<Diagnostic>,
) {
    val hasErrors: Boolean
        get() = diagnostics.isNotEmpty()
}

/**
 * BoundProgram 到 typed program 的可替换类型检查接口。
 * Replaceable type-checking interface from BoundProgram to typed programs.
 */
fun interface TypeChecker {
    /**
     * 检查一个 BoundProgram 并返回 typed program 或 diagnostics。
     * Checks one BoundProgram and returns either a typed program or diagnostics.
     */
    fun check(program: BoundProgram): ProgramTypeCheckResult
}

/**
 * 当前 seed expression grammar 的最小 type checker，主入口消费 BoundProgram。
 * Minimal type checker for the current seed expression grammar, with BoundProgram as the main entry point.
 */
class SeedTypeChecker : TypeChecker {
    /**
     * 类型检查已绑定 program，并在过程中收集 type diagnostics。
     * Type-checks a bound program while collecting type diagnostics.
     */
    override fun check(program: BoundProgram): ProgramTypeCheckResult {
        val diagnostics = DiagnosticBag()
        val scope = mutableMapOf<BindingSymbol, TypeRef>()
        val declarations = mutableListOf<TypedValDeclaration>()
        val functions = mutableListOf<TypedFunctionDeclaration>()
        val orderedDeclarations = mutableListOf<TypedDeclaration>()

        for (declaration in program.orderedDeclarations) {
            when (declaration) {
                is BoundValDeclaration -> {
                    val typed = checkValDeclaration(declaration, scope, diagnostics)
                    if (typed != null) {
                        declarations += typed
                        orderedDeclarations += typed
                    }
                }
                is BoundFunctionDeclaration -> {
                    val typed = checkFunctionDeclaration(declaration, scope, diagnostics)
                    if (typed != null) {
                        functions += typed
                        orderedDeclarations += typed
                    }
                }
            }
        }

        val declarationDiagnostics = diagnostics.toList()
        if (declarationDiagnostics.isNotEmpty()) {
            return ProgramTypeCheckResult(program = null, diagnostics = declarationDiagnostics)
        }
        val expression = checkExpression(program.expression, scope, diagnostics)
            ?: return ProgramTypeCheckResult(program = null, diagnostics = diagnostics.toList())
        return ProgramTypeCheckResult(
            program = TypedProgram(
                declarations = declarations.toList(),
                expression = expression,
                functions = functions.toList(),
                orderedDeclarations = orderedDeclarations.toList(),
            ),
            diagnostics = emptyList(),
        )
    }

    /**
     * 类型检查一个已绑定 val declaration，并把类型写入当前 scope。
     * Type-checks one bound val declaration and writes its type into the current scope.
     */
    private fun checkValDeclaration(
        declaration: BoundValDeclaration,
        scope: MutableMap<BindingSymbol, TypeRef>,
        diagnostics: DiagnosticBag,
    ): TypedValDeclaration? {
        val initializer = checkExpression(declaration.initializer, scope, diagnostics) ?: return null
        scope[declaration.symbol] = initializer.type
        return TypedValDeclaration(
            syntax = declaration.syntax,
            symbol = declaration.symbol,
            initializer = initializer,
        )
    }

    /**
     * 类型检查一个函数声明，参数类型必须显式可解析，返回类型由 body 推导。
     * Type-checks one function declaration; parameter types must be explicit and resolvable, and return type is inferred from the body.
     */
    private fun checkFunctionDeclaration(
        declaration: BoundFunctionDeclaration,
        scope: MutableMap<BindingSymbol, TypeRef>,
        diagnostics: DiagnosticBag,
    ): TypedFunctionDeclaration? {
        val functionScope = scope.toMutableMap()
        val parameters = declaration.parameters.mapNotNull { checkParameter(it, functionScope, diagnostics) }
        if (parameters.size != declaration.parameters.size) {
            return null
        }
        val localDeclarations = mutableListOf<TypedValDeclaration>()
        for (localDeclaration in declaration.body.declarations) {
            checkValDeclaration(localDeclaration, functionScope, diagnostics)?.let(localDeclarations::add)
        }
        val expression = checkExpression(declaration.body.expression, functionScope, diagnostics) ?: return null
        val type = TypeRef.Function(parameters.map { it.type }, expression.type)
        scope[declaration.symbol] = type
        return TypedFunctionDeclaration(
            syntax = declaration.syntax,
            symbol = declaration.symbol,
            parameters = parameters,
            declarations = localDeclarations.toList(),
            expression = expression,
            type = type,
        )
    }

    /**
     * 类型检查函数参数注解，并把参数类型写入函数局部 scope。
     * Type-checks a function parameter annotation and writes the parameter type into the function-local scope.
     */
    private fun checkParameter(
        parameter: BoundFunctionParameter,
        scope: MutableMap<BindingSymbol, TypeRef>,
        diagnostics: DiagnosticBag,
    ): TypedFunctionParameter? {
        val typeName = parameter.syntax.typeName
        if (typeName == null) {
            diagnostics.report("TYPE004", "missing parameter type", parameter.syntax.span)
            return null
        }
        val type = typeFromAnnotation(typeName, parameter.syntax.span, diagnostics) ?: return null
        scope[parameter.symbol] = type
        return TypedFunctionParameter(syntax = parameter.syntax, symbol = parameter.symbol, type = type)
    }

    /**
     * 先对 AST program 执行默认 binding，再类型检查成功绑定的 program。
     * Runs default binding for an AST program first, then type-checks the successfully bound program.
     */
    fun check(program: AstProgram): ProgramTypeCheckResult {
        val bindingResult = SeedBindingResolver().resolve(program)
        val boundProgram = bindingResult.program
        if (boundProgram == null) {
            return ProgramTypeCheckResult(program = null, diagnostics = bindingResult.diagnostics)
        }
        return check(boundProgram)
    }

    /**
     * 使用空作用域类型检查单个 expression。
     * Type-checks one expression with an empty scope.
     */
    fun check(expression: Expression): TypeCheckResult {
        val bindingResult = SeedBindingResolver().resolve(AstProgram(expression))
        val boundExpression = bindingResult.program?.expression
        if (boundExpression == null) {
            return TypeCheckResult(expression = null, diagnostics = bindingResult.diagnostics)
        }
        val diagnostics = DiagnosticBag()
        val typed = checkExpression(boundExpression, emptyMap(), diagnostics)
        return TypeCheckResult(expression = typed, diagnostics = diagnostics.toList())
    }

    /**
     * 按 bound expression 类型分派类型检查。
     * Dispatches type checking by bound expression type.
     */
    private fun checkExpression(
        expression: BoundExpression,
        scope: Map<BindingSymbol, TypeRef>,
        diagnostics: DiagnosticBag,
    ): TypedExpression? =
        when (expression) {
            is BoundInteger -> TypedInteger(expression.syntax)
            is BoundString -> TypedString(expression.syntax)
            is BoundPrintCall -> checkPrintCall(expression, scope, diagnostics)
            is BoundFunctionCall -> checkFunctionCall(expression, scope, diagnostics)
            is BoundGrouped -> checkGrouped(expression, scope, diagnostics)
            is BoundPrefix -> checkPrefix(expression, scope, diagnostics)
            is BoundBinary -> checkBinary(expression, scope, diagnostics)
            is BoundVariable -> checkIdentifier(expression, scope, diagnostics)
            is BoundMissing -> unsupported(expression.syntax, diagnostics)
        }

    /**
     * 类型检查内建 print 调用，argument 可为当前已定义的任何值类型。
     * Type-checks a builtin print call whose argument may be any currently defined value type.
     */
    private fun checkPrintCall(
        expression: BoundPrintCall,
        scope: Map<BindingSymbol, TypeRef>,
        diagnostics: DiagnosticBag,
    ): TypedExpression? {
        if (expression.arguments.size != 1) {
            diagnostics.report("TYPE005", "function arity mismatch", expression.syntax.span)
            return null
        }
        val argument = checkExpression(expression.argument, scope, diagnostics) ?: return null
        return TypedPrintCall(syntax = expression.syntax, argument = argument)
    }

    /**
     * 类型检查函数调用的 callee、参数数量和参数类型。
     * Type-checks a function call's callee, argument count, and argument types.
     */
    private fun checkFunctionCall(
        expression: BoundFunctionCall,
        scope: Map<BindingSymbol, TypeRef>,
        diagnostics: DiagnosticBag,
    ): TypedExpression? {
        val calleeType = scope[expression.symbol]
        if (calleeType !is TypeRef.Function) {
            diagnostics.report("TYPE002", "unsupported expression", expression.syntax.span)
            return null
        }
        if (calleeType.parameters.size != expression.arguments.size) {
            diagnostics.report("TYPE005", "function arity mismatch", expression.syntax.span)
            return null
        }
        val arguments = expression.arguments.mapNotNull { checkExpression(it, scope, diagnostics) }
        if (arguments.size != expression.arguments.size) {
            return null
        }
        for ((index, argument) in arguments.withIndex()) {
            if (argument.type != calleeType.parameters[index]) {
                diagnostics.report("TYPE006", "function argument type mismatch", argument.syntax.span)
                return null
            }
        }
        return TypedFunctionCall(
            syntax = expression.syntax,
            symbol = expression.symbol,
            arguments = arguments,
            type = calleeType.returns,
        )
    }

    /**
     * 类型检查分组表达式并保留内部表达式类型。
     * Type-checks a grouped expression and preserves the inner expression type.
     */
    private fun checkGrouped(
        expression: BoundGrouped,
        scope: Map<BindingSymbol, TypeRef>,
        diagnostics: DiagnosticBag,
    ): TypedExpression? {
        val inner = checkExpression(expression.inner, scope, diagnostics) ?: return null
        return TypedGrouped(syntax = expression.syntax, inner = inner)
    }

    /**
     * 类型检查当前支持的一元 prefix expression。
     * Type-checks the currently supported unary prefix expression.
     */
    private fun checkPrefix(
        expression: BoundPrefix,
        scope: Map<BindingSymbol, TypeRef>,
        diagnostics: DiagnosticBag,
    ): TypedExpression? {
        val operand = checkExpression(expression.operand, scope, diagnostics) ?: return null
        if (operand.type != TypeRef.Int64) {
            return unsupported(expression.syntax, diagnostics)
        }
        if (expression.syntax.operator.kind != TokenKinds.Plus && expression.syntax.operator.kind != TokenKinds.Minus) {
            return unsupported(expression.syntax, diagnostics)
        }
        return TypedPrefix(syntax = expression.syntax, operand = operand, type = TypeRef.Int64)
    }

    /**
     * 类型检查当前支持的二元 expression。
     * Type-checks the currently supported binary expression.
     */
    private fun checkBinary(
        expression: BoundBinary,
        scope: Map<BindingSymbol, TypeRef>,
        diagnostics: DiagnosticBag,
    ): TypedExpression? {
        val left = checkExpression(expression.left, scope, diagnostics)
        val right = checkExpression(expression.right, scope, diagnostics)
        if (left == null || right == null) {
            return null
        }
        if (left.type != TypeRef.Int64 || right.type != TypeRef.Int64) {
            return unsupported(expression.syntax, diagnostics)
        }
        val isSupportedOperator = expression.syntax.operator.kind == TokenKinds.Plus ||
            expression.syntax.operator.kind == TokenKinds.Minus ||
            expression.syntax.operator.kind == TokenKinds.Star ||
            expression.syntax.operator.kind == TokenKinds.Slash
        if (!isSupportedOperator) {
            return unsupported(expression.syntax, diagnostics)
        }
        return TypedBinary(syntax = expression.syntax, left = left, right = right, type = TypeRef.Int64)
    }

    /**
     * 类型检查已绑定 identifier 引用，符号尚无类型时报告 TYPE001。
     * Type-checks a bound identifier reference and reports TYPE001 when its symbol has no type yet.
     */
    private fun checkIdentifier(
        expression: BoundVariable,
        scope: Map<BindingSymbol, TypeRef>,
        diagnostics: DiagnosticBag,
    ): TypedExpression? {
        val type = scope[expression.symbol]
        return if (type == null) {
            diagnostics.report("TYPE001", "unresolved identifier", expression.syntax.span)
            null
        } else {
            TypedVariable(syntax = expression.syntax, symbol = expression.symbol, type = type)
        }
    }

    /**
     * 将源码类型注解解析为当前 TypeRef。
     * Resolves a source type annotation into the current TypeRef.
     */
    private fun typeFromAnnotation(
        name: String,
        span: cn.llonvne.kklang.frontend.SourceSpan,
        diagnostics: DiagnosticBag,
    ): TypeRef? =
        when (name) {
            "Int" -> TypeRef.Int64
            "String" -> TypeRef.String
            "Unit" -> TypeRef.Unit
            else -> {
                diagnostics.report("TYPE003", "unknown type annotation", span)
                null
            }
        }

    /**
     * 报告当前类型系统范围不支持的 expression。
     * Reports an expression unsupported by the current type-system scope.
     */
    private fun unsupported(expression: Expression, diagnostics: DiagnosticBag): TypedExpression? {
        diagnostics.report("TYPE002", "unsupported expression", expression.span)
        return null
    }
}
