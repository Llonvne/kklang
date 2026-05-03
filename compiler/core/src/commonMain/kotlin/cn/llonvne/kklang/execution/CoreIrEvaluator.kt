package cn.llonvne.kklang.execution

import cn.llonvne.kklang.frontend.diagnostics.Diagnostic
import cn.llonvne.kklang.frontend.diagnostics.DiagnosticBag

/**
 * Core IR 求值的结果，成功时 value 非空，失败时 diagnostics 非空，并始终保留已产生输出。
 * Result of Core IR evaluation; value is present on success, diagnostics are present on failure, and produced output is always preserved.
 */
data class EvaluationResult(
    val value: ExecutionValue?,
    val diagnostics: List<Diagnostic>,
    val output: String = "",
) {
    val hasErrors: Boolean
        get() = diagnostics.isNotEmpty()
}

/**
 * Core IR evaluator 的可替换接口，允许执行器接入不同后端。
 * Replaceable Core IR evaluator interface that lets the execution engine use different backends.
 */
fun interface IrEvaluator {
    /**
     * 求值单个 Core IR program 并返回值或 diagnostics。
     * Evaluates one Core IR program and returns either a value or diagnostics.
     */
    fun evaluate(program: IrProgram): EvaluationResult
}

/**
 * 当前最小 Core IR evaluator，支持 Int64、String、Unit、不可变 val、print 和受检整数运算。
 * Current minimal Core IR evaluator supporting Int64, String, Unit, immutable vals, print, and checked integer operations.
 */
class CoreIrEvaluator : IrEvaluator {
    /**
     * 求值 Core IR program 并收集所有求值期 diagnostics。
     * Evaluates the Core IR program and collects all evaluation diagnostics.
     */
    override fun evaluate(program: IrProgram): EvaluationResult {
        val diagnostics = DiagnosticBag()
        val environment = mutableMapOf<String, ExecutionValue>()
        val functions = program.functions.associateBy { it.name }
        val output = StringBuilder()
        for (declaration in program.declarations) {
            val value = evaluateExpression(declaration.initializer, environment, functions, diagnostics, output)
                ?: return EvaluationResult(null, diagnostics.toList(), output.toString())
            environment[declaration.name] = value
        }
        val value = evaluateExpression(program.expression, environment, functions, diagnostics, output)
        return EvaluationResult(value = value, diagnostics = diagnostics.toList(), output = output.toString())
    }

    /**
     * 求值单个 Core IR expression，测试和局部调用可使用此便捷入口。
     * Evaluates one Core IR expression; tests and local callers may use this convenience entry point.
     */
    fun evaluate(expression: IrExpression): EvaluationResult =
        evaluate(IrProgram(declarations = emptyList(), expression = expression))

    /**
     * 按节点类型分派 Core IR expression 求值。
     * Dispatches Core IR expression evaluation by node type.
     */
    private fun evaluateExpression(
        expression: IrExpression,
        environment: Map<String, ExecutionValue>,
        functions: Map<String, IrFunctionDeclaration>,
        diagnostics: DiagnosticBag,
        output: StringBuilder,
    ): ExecutionValue? =
        when (expression) {
            is IrInt64 -> ExecutionValue.Int64(expression.value)
            is IrString -> ExecutionValue.String(expression.value)
            is IrPrint -> evaluatePrint(expression, environment, functions, diagnostics, output)
            is IrCall -> evaluateCall(expression, environment, functions, diagnostics, output)
            is IrVariable -> evaluateVariable(expression, environment, diagnostics)
            is IrUnary -> evaluateUnary(expression, environment, functions, diagnostics, output)
            is IrBinary -> evaluateBinary(expression, environment, functions, diagnostics, output)
        }

    /**
     * 求值内建 print 调用，写出 argument 的文本形式并返回 Unit。
     * Evaluates a builtin print call, writes the argument text form, and returns Unit.
     */
    private fun evaluatePrint(
        expression: IrPrint,
        environment: Map<String, ExecutionValue>,
        functions: Map<String, IrFunctionDeclaration>,
        diagnostics: DiagnosticBag,
        output: StringBuilder,
    ): ExecutionValue? {
        val argument = evaluateExpression(expression.argument, environment, functions, diagnostics, output) ?: return null
        output.append(argument.printText())
        return ExecutionValue.Unit
    }

    /**
     * 求值函数调用，按顺序求值参数并在函数局部环境中求值函数体。
     * Evaluates a function call by evaluating arguments in order and evaluating the body in a function-local environment.
     */
    private fun evaluateCall(
        expression: IrCall,
        environment: Map<String, ExecutionValue>,
        functions: Map<String, IrFunctionDeclaration>,
        diagnostics: DiagnosticBag,
        output: StringBuilder,
    ): ExecutionValue? {
        val function = functions[expression.callee]
        if (function == null) {
            diagnostics.report("EXEC001", "unbound function", expression.span)
            return null
        }
        if (function.parameters.size != expression.arguments.size) {
            diagnostics.report("EXEC001", "function arity mismatch", expression.span)
            return null
        }
        val argumentValues = expression.arguments.mapNotNull { evaluateExpression(it, environment, functions, diagnostics, output) }
        if (argumentValues.size != expression.arguments.size) {
            return null
        }
        val localEnvironment = environment.toMutableMap()
        for ((index, parameter) in function.parameters.withIndex()) {
            localEnvironment[parameter] = argumentValues[index]
        }
        for (declaration in function.body.declarations) {
            val value = evaluateExpression(declaration.initializer, localEnvironment, functions, diagnostics, output) ?: return null
            localEnvironment[declaration.name] = value
        }
        return evaluateExpression(function.body.expression, localEnvironment, functions, diagnostics, output)
    }

    /**
     * 求值变量引用，未绑定时报告防御性 EXEC001。
     * Evaluates a variable reference and reports defensive EXEC001 when it is unbound.
     */
    private fun evaluateVariable(
        expression: IrVariable,
        environment: Map<String, ExecutionValue>,
        diagnostics: DiagnosticBag,
    ): ExecutionValue? {
        val value = environment[expression.name]
        if (value == null) {
            diagnostics.report("EXEC001", "unbound variable", expression.span)
            return null
        }
        return value
    }

    /**
     * 求值一元运算，并在 operand 已失败时传播失败。
     * Evaluates a unary operation and propagates failure when the operand already failed.
     */
    private fun evaluateUnary(
        expression: IrUnary,
        environment: Map<String, ExecutionValue>,
        functions: Map<String, IrFunctionDeclaration>,
        diagnostics: DiagnosticBag,
        output: StringBuilder,
    ): ExecutionValue? {
        val operand = evaluateExpression(expression.operand, environment, functions, diagnostics, output)?.asInt64(expression, diagnostics) ?: return null
        return when (expression.operator) {
            IrUnaryOperator.Plus -> ExecutionValue.Int64(operand)
            IrUnaryOperator.Minus -> negate(expression, operand, diagnostics)
        }
    }

    /**
     * 求值二元运算，并在任一 operand 已失败时传播失败。
     * Evaluates a binary operation and propagates failure when either operand already failed.
     */
    private fun evaluateBinary(
        expression: IrBinary,
        environment: Map<String, ExecutionValue>,
        functions: Map<String, IrFunctionDeclaration>,
        diagnostics: DiagnosticBag,
        output: StringBuilder,
    ): ExecutionValue? {
        val left = evaluateExpression(expression.left, environment, functions, diagnostics, output)?.asInt64(expression, diagnostics)
        val right = evaluateExpression(expression.right, environment, functions, diagnostics, output)?.asInt64(expression, diagnostics)
        if (left == null || right == null) {
            return null
        }

        return when (expression.operator) {
            IrBinaryOperator.Plus -> add(expression, left, right, diagnostics)
            IrBinaryOperator.Minus -> subtract(expression, left, right, diagnostics)
            IrBinaryOperator.Multiply -> multiply(expression, left, right, diagnostics)
            IrBinaryOperator.Divide -> divide(expression, left, right, diagnostics)
        }
    }

    /**
     * 对 Int64 执行受检取负，覆盖 Long.MIN_VALUE 溢出。
     * Performs checked Int64 negation and covers Long.MIN_VALUE overflow.
     */
    private fun negate(
        expression: IrUnary,
        operand: Long,
        diagnostics: DiagnosticBag,
    ): ExecutionValue? {
        if (operand == Long.MIN_VALUE) {
            return overflow(expression, diagnostics)
        }
        return ExecutionValue.Int64(-operand)
    }

    /**
     * 对 Int64 执行受检加法。
     * Performs checked Int64 addition.
     */
    private fun add(
        expression: IrBinary,
        left: Long,
        right: Long,
        diagnostics: DiagnosticBag,
    ): ExecutionValue? {
        val result = left + right
        if (((left xor result) and (right xor result)) < 0) {
            return overflow(expression, diagnostics)
        }
        return ExecutionValue.Int64(result)
    }

    /**
     * 对 Int64 执行受检减法。
     * Performs checked Int64 subtraction.
     */
    private fun subtract(
        expression: IrBinary,
        left: Long,
        right: Long,
        diagnostics: DiagnosticBag,
    ): ExecutionValue? {
        val result = left - right
        if (((left xor right) and (left xor result)) < 0) {
            return overflow(expression, diagnostics)
        }
        return ExecutionValue.Int64(result)
    }

    /**
     * 对 Int64 执行受检乘法，左操作数为零时直接允许零结果。
     * Performs checked Int64 multiplication and directly accepts a zero result when the left operand is zero.
     */
    private fun multiply(
        expression: IrBinary,
        left: Long,
        right: Long,
        diagnostics: DiagnosticBag,
    ): ExecutionValue? {
        val result = left * right
        if (left != 0L && result / left != right) {
            return overflow(expression, diagnostics)
        }
        return ExecutionValue.Int64(result)
    }

    /**
     * 对 Int64 执行除法，报告除零和 Long.MIN_VALUE / -1 溢出。
     * Performs Int64 division and reports division by zero plus Long.MIN_VALUE / -1 overflow.
     */
    private fun divide(
        expression: IrBinary,
        left: Long,
        right: Long,
        diagnostics: DiagnosticBag,
    ): ExecutionValue? {
        if (right == 0L) {
            diagnostics.report("EXEC002", "division by zero", expression.span)
            return null
        }
        if (left == Long.MIN_VALUE && right == -1L) {
            return overflow(expression, diagnostics)
        }
        return ExecutionValue.Int64(left / right)
    }

    /**
     * 报告统一的 Int64 溢出 diagnostic 并返回失败值。
     * Reports the shared Int64 overflow diagnostic and returns a failed value.
     */
    private fun overflow(
        expression: IrExpression,
        diagnostics: DiagnosticBag,
    ): ExecutionValue? {
        diagnostics.report("EXEC003", "Int64 overflow", expression.span)
        return null
    }

    /**
     * 将当前执行值拆成 Int64；非 Int64 值在 malformed IR 中产生 EXEC001。
     * Extracts the current execution value as Int64; non-Int64 values in malformed IR produce EXEC001.
     */
    private fun ExecutionValue.asInt64(expression: IrExpression, diagnostics: DiagnosticBag): Long? =
        when (this) {
            is ExecutionValue.Int64 -> value
            is ExecutionValue.String -> {
                diagnostics.report("EXEC001", "expected Int64 value", expression.span)
                null
            }
            ExecutionValue.Unit -> {
                diagnostics.report("EXEC001", "expected Int64 value", expression.span)
                null
            }
        }

    /**
     * 返回 print 使用的稳定文本形式。
     * Returns the stable text form used by print.
     */
    private fun ExecutionValue.printText(): String =
        when (this) {
            is ExecutionValue.Int64 -> value.toString()
            is ExecutionValue.String -> value
            ExecutionValue.Unit -> "Unit"
        }
}
