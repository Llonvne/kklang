package cn.llonvne.kklang.typechecking

import cn.llonvne.kklang.frontend.diagnostics.Diagnostic
import cn.llonvne.kklang.frontend.diagnostics.DiagnosticBag
import cn.llonvne.kklang.frontend.lexing.TokenKinds
import cn.llonvne.kklang.frontend.parsing.BinaryExpression
import cn.llonvne.kklang.frontend.parsing.Expression
import cn.llonvne.kklang.frontend.parsing.GroupedExpression
import cn.llonvne.kklang.frontend.parsing.IdentifierExpression
import cn.llonvne.kklang.frontend.parsing.IntegerExpression
import cn.llonvne.kklang.frontend.parsing.MissingExpression
import cn.llonvne.kklang.frontend.parsing.PrefixExpression

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
 * 类型检查后的整数字面量 expression。
 * Type-checked integer-literal expression.
 */
data class TypedInteger(
    override val syntax: IntegerExpression,
    override val type: TypeRef = TypeRef.Int64,
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
 * AST 到 typed AST 的可替换类型检查接口。
 * Replaceable type-checking interface from AST to typed AST.
 */
fun interface TypeChecker {
    /**
     * 检查一个 AST expression 并返回 typed AST 或 diagnostics。
     * Checks one AST expression and returns either typed AST or diagnostics.
     */
    fun check(expression: Expression): TypeCheckResult
}

/**
 * 当前 seed expression grammar 的最小 type checker。
 * Minimal type checker for the current seed expression grammar.
 */
class SeedTypeChecker : TypeChecker {
    /**
     * 类型检查根 expression，并在过程中收集 type diagnostics。
     * Type-checks the root expression while collecting type diagnostics.
     */
    override fun check(expression: Expression): TypeCheckResult {
        val diagnostics = DiagnosticBag()
        val typed = checkExpression(expression, diagnostics)
        return TypeCheckResult(expression = typed, diagnostics = diagnostics.toList())
    }

    /**
     * 按 AST expression 类型分派类型检查。
     * Dispatches type checking by AST expression type.
     */
    private fun checkExpression(expression: Expression, diagnostics: DiagnosticBag): TypedExpression? =
        when (expression) {
            is IntegerExpression -> TypedInteger(expression)
            is GroupedExpression -> checkGrouped(expression, diagnostics)
            is PrefixExpression -> checkPrefix(expression, diagnostics)
            is BinaryExpression -> checkBinary(expression, diagnostics)
            is IdentifierExpression -> unresolvedIdentifier(expression, diagnostics)
            is MissingExpression -> unsupported(expression, diagnostics)
        }

    /**
     * 类型检查分组表达式并保留内部表达式类型。
     * Type-checks a grouped expression and preserves the inner expression type.
     */
    private fun checkGrouped(expression: GroupedExpression, diagnostics: DiagnosticBag): TypedExpression? {
        val inner = checkExpression(expression.expression, diagnostics) ?: return null
        return TypedGrouped(syntax = expression, inner = inner)
    }

    /**
     * 类型检查当前支持的一元 prefix expression。
     * Type-checks the currently supported unary prefix expression.
     */
    private fun checkPrefix(expression: PrefixExpression, diagnostics: DiagnosticBag): TypedExpression? {
        val operand = checkExpression(expression.operand, diagnostics) ?: return null
        if (expression.operator.kind != TokenKinds.Plus && expression.operator.kind != TokenKinds.Minus) {
            return unsupported(expression, diagnostics)
        }
        return TypedPrefix(syntax = expression, operand = operand, type = TypeRef.Int64)
    }

    /**
     * 类型检查当前支持的二元 expression。
     * Type-checks the currently supported binary expression.
     */
    private fun checkBinary(expression: BinaryExpression, diagnostics: DiagnosticBag): TypedExpression? {
        val left = checkExpression(expression.left, diagnostics)
        val right = checkExpression(expression.right, diagnostics)
        if (left == null || right == null) {
            return null
        }
        val isSupportedOperator = expression.operator.kind == TokenKinds.Plus ||
            expression.operator.kind == TokenKinds.Minus ||
            expression.operator.kind == TokenKinds.Star ||
            expression.operator.kind == TokenKinds.Slash
        if (!isSupportedOperator) {
            return unsupported(expression, diagnostics)
        }
        return TypedBinary(syntax = expression, left = left, right = right, type = TypeRef.Int64)
    }

    /**
     * 报告当前没有绑定语义的 identifier。
     * Reports an identifier that has no binding semantics yet.
     */
    private fun unresolvedIdentifier(expression: IdentifierExpression, diagnostics: DiagnosticBag): TypedExpression? {
        diagnostics.report("TYPE001", "unresolved identifier", expression.span)
        return null
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
