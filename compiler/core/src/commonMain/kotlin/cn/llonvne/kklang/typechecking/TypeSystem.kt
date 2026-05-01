package cn.llonvne.kklang.typechecking

import cn.llonvne.kklang.binding.BoundProgram
import cn.llonvne.kklang.binding.BoundBinary
import cn.llonvne.kklang.binding.BoundExpression
import cn.llonvne.kklang.binding.BoundGrouped
import cn.llonvne.kklang.binding.BoundInteger
import cn.llonvne.kklang.binding.BoundMissing
import cn.llonvne.kklang.binding.BoundPrefix
import cn.llonvne.kklang.binding.BoundVariable
import cn.llonvne.kklang.binding.BindingSymbol
import cn.llonvne.kklang.binding.SeedBindingResolver
import cn.llonvne.kklang.frontend.diagnostics.Diagnostic
import cn.llonvne.kklang.frontend.diagnostics.DiagnosticBag
import cn.llonvne.kklang.frontend.lexing.TokenKinds
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
 * 当前类型系统可表达的类型引用。
 * Type references expressible by the current type system.
 */
sealed interface TypeRef {
    /**
     * 64 位有符号整数类型。
     * Signed 64-bit integer type.
     */
    data object Int64 : TypeRef
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
)

/**
 * 类型检查后的不可变 val declaration。
 * Type-checked immutable val declaration.
 */
data class TypedValDeclaration(
    val syntax: ValDeclaration,
    val symbol: BindingSymbol,
    val initializer: TypedExpression,
    val type: TypeRef = initializer.type,
) {
    val name: String
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

        for (declaration in program.declarations) {
            val initializer = checkExpression(declaration.initializer, scope, diagnostics)
            if (initializer != null) {
                scope[declaration.symbol] = initializer.type
                declarations += TypedValDeclaration(
                    syntax = declaration.syntax,
                    symbol = declaration.symbol,
                    initializer = initializer,
                )
            }
        }

        val expression = checkExpression(program.expression, scope, diagnostics)
            ?: return ProgramTypeCheckResult(program = null, diagnostics = diagnostics.toList())
        val diagnosticsList = diagnostics.toList()
        if (diagnosticsList.isNotEmpty()) {
            return ProgramTypeCheckResult(program = null, diagnostics = diagnosticsList)
        }
        return ProgramTypeCheckResult(
            program = TypedProgram(declarations = declarations.toList(), expression = expression),
            diagnostics = diagnosticsList,
        )
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
            is BoundGrouped -> checkGrouped(expression, scope, diagnostics)
            is BoundPrefix -> checkPrefix(expression, scope, diagnostics)
            is BoundBinary -> checkBinary(expression, scope, diagnostics)
            is BoundVariable -> checkIdentifier(expression, scope, diagnostics)
            is BoundMissing -> unsupported(expression.syntax, diagnostics)
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
     * 报告当前类型系统范围不支持的 expression。
     * Reports an expression unsupported by the current type-system scope.
     */
    private fun unsupported(expression: Expression, diagnostics: DiagnosticBag): TypedExpression? {
        diagnostics.report("TYPE002", "unsupported expression", expression.span)
        return null
    }
}
