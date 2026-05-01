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
 * binding 后的不可变 val declaration。
 * Immutable val declaration after binding.
 */
data class BoundValDeclaration(
    val syntax: ValDeclaration,
    val symbol: BindingSymbol,
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
    val expression: Expression,
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
        val symbolsByName = mutableMapOf<String, BindingSymbol>()
        val boundDeclarations = mutableListOf<BoundValDeclaration>()

        for (declaration in program.declarations) {
            val duplicate = symbolsByName.containsKey(declaration.name)
            val initializerIsBound = validateExpression(declaration.initializer, symbolsByName, diagnostics)
            if (duplicate) {
                diagnostics.report("BIND001", "duplicate immutable value", declaration.nameToken.span)
            }
            if (!duplicate && initializerIsBound) {
                val symbol = BindingSymbol(name = declaration.name, declaration = declaration)
                symbolsByName[declaration.name] = symbol
                boundDeclarations += BoundValDeclaration(syntax = declaration, symbol = symbol)
            }
        }

        validateExpression(program.expression, symbolsByName, diagnostics)

        val diagnosticsList = diagnostics.toList()
        if (diagnosticsList.isNotEmpty()) {
            return BindingResult(program = null, diagnostics = diagnosticsList)
        }
        return BindingResult(
            program = BoundProgram(
                syntax = program,
                declarations = boundDeclarations.toList(),
                expression = program.expression,
                symbols = boundDeclarations.map { it.symbol },
            ),
            diagnostics = diagnosticsList,
        )
    }

    /**
     * 按 expression 类型遍历并验证所有 identifier 引用。
     * Walks by expression type and validates every identifier reference.
     */
    private fun validateExpression(
        expression: Expression,
        symbolsByName: Map<String, BindingSymbol>,
        diagnostics: DiagnosticBag,
    ): Boolean =
        when (expression) {
            is IntegerExpression -> true
            is IdentifierExpression -> validateIdentifier(expression, symbolsByName, diagnostics)
            is GroupedExpression -> validateExpression(expression.expression, symbolsByName, diagnostics)
            is PrefixExpression -> validateExpression(expression.operand, symbolsByName, diagnostics)
            is BinaryExpression -> {
                val leftIsBound = validateExpression(expression.left, symbolsByName, diagnostics)
                val rightIsBound = validateExpression(expression.right, symbolsByName, diagnostics)
                leftIsBound && rightIsBound
            }
            is MissingExpression -> true
        }

    /**
     * 验证 identifier 已经绑定；未绑定时报告当前公开的 TYPE001。
     * Validates that an identifier is already bound; reports the current public TYPE001 when it is not.
     */
    private fun validateIdentifier(
        expression: IdentifierExpression,
        symbolsByName: Map<String, BindingSymbol>,
        diagnostics: DiagnosticBag,
    ): Boolean {
        if (symbolsByName.containsKey(expression.name)) {
            return true
        }
        diagnostics.report("TYPE001", "unresolved identifier", expression.span)
        return false
    }
}
