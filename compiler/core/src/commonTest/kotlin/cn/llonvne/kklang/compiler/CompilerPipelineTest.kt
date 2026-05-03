package cn.llonvne.kklang.compiler

import cn.llonvne.kklang.binding.BindingResolver
import cn.llonvne.kklang.binding.BindingResult
import cn.llonvne.kklang.execution.CoreIrEvaluator
import cn.llonvne.kklang.execution.EvaluationResult
import cn.llonvne.kklang.execution.ExecutionEngine
import cn.llonvne.kklang.execution.ExecutionResult
import cn.llonvne.kklang.execution.ExecutionValue
import cn.llonvne.kklang.execution.IrEvaluator
import cn.llonvne.kklang.execution.IrInt64
import cn.llonvne.kklang.execution.IrLowerer
import cn.llonvne.kklang.execution.IrLoweringResult
import cn.llonvne.kklang.execution.IrProgram
import cn.llonvne.kklang.execution.IrUnary
import cn.llonvne.kklang.execution.IrUnaryOperator
import cn.llonvne.kklang.frontend.SourceSpan
import cn.llonvne.kklang.frontend.SourceText
import cn.llonvne.kklang.frontend.diagnostics.Diagnostic
import cn.llonvne.kklang.frontend.parsing.Parser
import cn.llonvne.kklang.metaprogramming.ModifierExpander
import cn.llonvne.kklang.metaprogramming.ModifierExpansionResult
import cn.llonvne.kklang.typechecking.ProgramTypeCheckResult
import cn.llonvne.kklang.typechecking.TypeChecker
import cn.llonvne.kklang.typechecking.TypeRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 覆盖 compiler pipeline 阶段顺序、短路行为和 execution engine 边界。
 * Covers compiler pipeline phase order, short-circuit behavior, and execution-engine boundaries.
 */
class CompilerPipelineTest {
    /**
     * 验证合法 seed expression 会编译成包含完整阶段轨迹的 compiled program。
     * Verifies that a valid seed expression compiles into a compiled program with the full phase trace.
     */
    @Test
    fun `pipeline compiles seed expression into compiled program`() {
        val result = CompilerPipeline().compile(CompilationInput(SourceText.of("sample.kk", "1 + 2 * 3")))

        assertIs<CompilationResult.Success>(result)
        assertEquals(
            listOf(
                CompilerPhase.Lexing,
                CompilerPhase.Parsing,
                CompilerPhase.ModifierExpansion,
                CompilerPhase.Binding,
                CompilerPhase.TypeChecking,
                CompilerPhase.Lowering,
            ),
            result.phaseTrace,
        )
        assertEquals(SourceSpan("sample.kk", 0, 9), result.program.span)
        assertEquals(TypeRef.Int64, result.program.type)
        assertFalse(result.hasErrors)
    }

    /**
     * 验证 compiler pipeline 会编译不可变 val program。
     * Verifies that the compiler pipeline compiles an immutable val program.
     */
    @Test
    fun `pipeline compiles immutable val program`() {
        val result = CompilerPipeline().compile(CompilationInput(SourceText.of("sample.kk", "val x = 1; x + 2")))

        assertIs<CompilationResult.Success>(result)
        assertEquals(listOf("x"), result.program.ir.declarations.map { it.name })
        assertEquals(TypeRef.Int64, result.program.type)
        assertEquals(SourceSpan("sample.kk", 0, 16), result.program.span)
        assertEquals(result.program.ir.expression, result.program.expression)
    }

    /**
     * 验证 compiler pipeline 会通过 modifier expansion 编译函数声明和调用。
     * Verifies that the compiler pipeline compiles function declarations and calls through modifier expansion.
     */
    @Test
    fun `pipeline compiles fn modifier function program`() {
        val result = CompilerPipeline().compile(
            CompilationInput(SourceText.of("sample.kk", "fn add(a: Int, b: Int) { a + b } add(1, 2)")),
        )

        assertIs<CompilationResult.Success>(result)
        assertEquals(TypeRef.Int64, result.program.type)
        assertEquals(listOf("add"), result.program.ir.functions.map { it.name })
        assertEquals(
            listOf(
                CompilerPhase.Lexing,
                CompilerPhase.Parsing,
                CompilerPhase.ModifierExpansion,
                CompilerPhase.Binding,
                CompilerPhase.TypeChecking,
                CompilerPhase.Lowering,
            ),
            result.phaseTrace,
        )
    }

    /**
     * 验证 compiled program 直接暴露 Core IR span 和根类型。
     * Verifies that a compiled program directly exposes the Core IR span and root type.
     */
    @Test
    fun `compiled program exposes span and type`() {
        val span = SourceSpan("sample.kk", 0, 1)
        val program = CompiledProgram(
            ir = IrProgram(emptyList(), IrUnary(IrUnaryOperator.Plus, IrInt64(1, span), span)),
            type = TypeRef.Int64,
        )

        assertEquals(span, program.span)
        assertEquals(TypeRef.Int64, program.type)
    }

    /**
     * 验证 lexing diagnostics 会阻止 parser 创建和后续阶段运行。
     * Verifies that lexing diagnostics prevent parser creation and later phases.
     */
    @Test
    fun `pipeline stops before parsing when lexing fails`() {
        var parserWasRequested = false
        val result = CompilerPipeline(
            parserFactory = {
                parserWasRequested = true
                Parser(it)
            },
        ).compile(CompilationInput(SourceText.of("sample.kk", "@")))

        assertIs<CompilationResult.Failure>(result)
        assertEquals(listOf(CompilerPhase.Lexing), result.phaseTrace)
        assertEquals(listOf("LEX001"), result.diagnostics.map { it.code })
        assertFalse(parserWasRequested)
        assertTrue(result.hasErrors)
    }

    /**
     * 验证 parsing diagnostics 会阻止 modifier expansion、binding、type checker 和 lowering 运行。
     * Verifies that parsing diagnostics prevent modifier expansion, binding, the type checker, and lowering from running.
     */
    @Test
    fun `pipeline stops before type checking when parsing fails`() {
        var bindingResolverWasCalled = false
        var modifierExpanderWasCalled = false
        var typeCheckerWasCalled = false
        var lowererWasCalled = false
        val result = CompilerPipeline(
            bindingResolver = BindingResolver {
                bindingResolverWasCalled = true
                BindingResult(null, emptyList())
            },
            modifierExpander = ModifierExpander {
                modifierExpanderWasCalled = true
                ModifierExpansionResult(it, emptyList())
            },
            typeChecker = TypeChecker {
                typeCheckerWasCalled = true
                ProgramTypeCheckResult(null, emptyList())
            },
            lowerer = IrLowerer {
                lowererWasCalled = true
                IrLoweringResult(
                    IrProgram(emptyList(), IrInt64(1, it.expression.syntax.span)),
                    emptyList(),
                )
            },
        ).compile(CompilationInput(SourceText.of("sample.kk", "1 +")))

        assertIs<CompilationResult.Failure>(result)
        assertEquals(listOf(CompilerPhase.Lexing, CompilerPhase.Parsing), result.phaseTrace)
        assertEquals(listOf("PARSE001"), result.diagnostics.map { it.code })
        assertFalse(modifierExpanderWasCalled)
        assertFalse(bindingResolverWasCalled)
        assertFalse(typeCheckerWasCalled)
        assertFalse(lowererWasCalled)
    }

    /**
     * 验证 modifier expansion diagnostics 会阻止 binding 和后续阶段。
     * Verifies that modifier expansion diagnostics prevent binding and later phases.
     */
    @Test
    fun `pipeline stops before binding when modifier expansion fails`() {
        var bindingResolverWasCalled = false
        val result = CompilerPipeline(
            bindingResolver = BindingResolver {
                bindingResolverWasCalled = true
                BindingResult(null, emptyList())
            },
        ).compile(CompilationInput(SourceText.of("sample.kk", "unknown thing { 1 } 0")))

        assertIs<CompilationResult.Failure>(result)
        assertEquals(listOf(CompilerPhase.Lexing, CompilerPhase.Parsing, CompilerPhase.ModifierExpansion), result.phaseTrace)
        assertEquals(listOf("MOD001"), result.diagnostics.map { it.code })
        assertFalse(bindingResolverWasCalled)
    }

    /**
     * 验证 modifier expander 违反内部契约时会被转换为 COMPILER001。
     * Verifies that a modifier-expander contract violation is converted into COMPILER001.
     */
    @Test
    fun `pipeline rejects modifier expansion success without ast program`() {
        val result = CompilerPipeline(
            modifierExpander = ModifierExpander { ModifierExpansionResult(program = null, diagnostics = emptyList()) },
        ).compile(CompilationInput(SourceText.of("sample.kk", "1")))

        assertIs<CompilationResult.Failure>(result)
        assertEquals(listOf("COMPILER001"), result.diagnostics.map { it.code })
        assertEquals(listOf(CompilerPhase.Lexing, CompilerPhase.Parsing, CompilerPhase.ModifierExpansion), result.phaseTrace)
    }

    /**
     * 验证 binding diagnostics 会阻止 type checker 和 lowering 运行。
     * Verifies that binding diagnostics prevent the type checker and lowering from running.
     */
    @Test
    fun `pipeline stops before type checking when binding fails`() {
        var typeCheckerWasCalled = false
        var lowererWasCalled = false
        val result = CompilerPipeline(
            typeChecker = TypeChecker {
                typeCheckerWasCalled = true
                ProgramTypeCheckResult(null, emptyList())
            },
            lowerer = IrLowerer {
                lowererWasCalled = true
                IrLoweringResult(
                    IrProgram(emptyList(), IrInt64(1, it.expression.syntax.span)),
                    emptyList(),
                )
            },
        ).compile(CompilationInput(SourceText.of("sample.kk", "name")))

        assertIs<CompilationResult.Failure>(result)
        assertEquals(listOf(CompilerPhase.Lexing, CompilerPhase.Parsing, CompilerPhase.ModifierExpansion, CompilerPhase.Binding), result.phaseTrace)
        assertEquals(listOf("TYPE001"), result.diagnostics.map { it.code })
        assertFalse(typeCheckerWasCalled)
        assertFalse(lowererWasCalled)
    }

    /**
     * 验证 type checking diagnostics 会阻止 lowering 运行。
     * Verifies that type-checking diagnostics prevent lowering from running.
     */
    @Test
    fun `pipeline stops before lowering when type checking fails`() {
        var lowererWasCalled = false
        val source = SourceText.of("sample.kk", "1")
        val result = CompilerPipeline(
            typeChecker = TypeChecker {
                ProgramTypeCheckResult(
                    program = null,
                    diagnostics = listOf(Diagnostic("TYPE002", "type failure", it.expression.span)),
                )
            },
            lowerer = IrLowerer {
                lowererWasCalled = true
                IrLoweringResult(
                    IrProgram(emptyList(), IrInt64(1, it.expression.syntax.span)),
                    emptyList(),
                )
            },
        ).compile(CompilationInput(source))

        assertIs<CompilationResult.Failure>(result)
        assertEquals(
            listOf(CompilerPhase.Lexing, CompilerPhase.Parsing, CompilerPhase.ModifierExpansion, CompilerPhase.Binding, CompilerPhase.TypeChecking),
            result.phaseTrace,
        )
        assertEquals(listOf("TYPE002"), result.diagnostics.map { it.code })
        assertFalse(lowererWasCalled)
    }

    /**
     * 验证 lowering diagnostics 会产生失败结果且不会返回 compiled program。
     * Verifies that lowering diagnostics produce failure without returning a compiled program.
     */
    @Test
    fun `pipeline returns lowering diagnostics without compiled program`() {
        val source = SourceText.of("sample.kk", "1")
        val result = CompilerPipeline(
            lowerer = IrLowerer {
                IrLoweringResult(
                    program = null,
                    diagnostics = listOf(Diagnostic("EXEC001", "lowering failure", it.expression.syntax.span)),
                )
            },
        ).compile(CompilationInput(source))

        assertIs<CompilationResult.Failure>(result)
        assertEquals(
            listOf(
                CompilerPhase.Lexing,
                CompilerPhase.Parsing,
                CompilerPhase.ModifierExpansion,
                CompilerPhase.Binding,
                CompilerPhase.TypeChecking,
                CompilerPhase.Lowering,
            ),
            result.phaseTrace,
        )
        assertEquals(listOf("EXEC001"), result.diagnostics.map { it.code })
    }

    /**
     * 验证 binding resolver 违反内部契约时会被转换为 COMPILER001。
     * Verifies that a binding-resolver contract violation is converted into COMPILER001.
     */
    @Test
    fun `pipeline rejects binding resolver success without bound program`() {
        val result = CompilerPipeline(
            bindingResolver = BindingResolver { BindingResult(null, emptyList()) },
        ).compile(CompilationInput(SourceText.of("sample.kk", "1")))

        assertIs<CompilationResult.Failure>(result)
        assertEquals(listOf("COMPILER001"), result.diagnostics.map { it.code })
        assertEquals(listOf(CompilerPhase.Lexing, CompilerPhase.Parsing, CompilerPhase.ModifierExpansion, CompilerPhase.Binding), result.phaseTrace)
    }

    /**
     * 验证 type checker 违反内部契约时会被转换为 COMPILER001。
     * Verifies that a type-checker contract violation is converted into COMPILER001.
     */
    @Test
    fun `pipeline rejects type checker success without typed program`() {
        val result = CompilerPipeline(
            typeChecker = TypeChecker { ProgramTypeCheckResult(null, emptyList()) },
        ).compile(CompilationInput(SourceText.of("sample.kk", "1")))

        assertIs<CompilationResult.Failure>(result)
        assertEquals(listOf("COMPILER001"), result.diagnostics.map { it.code })
    }

    /**
     * 验证 lowerer 违反内部契约时会被转换为 COMPILER001。
     * Verifies that a lowerer contract violation is converted into COMPILER001.
     */
    @Test
    fun `pipeline rejects lowerer success without ir`() {
        val result = CompilerPipeline(
            lowerer = IrLowerer { IrLoweringResult(null, emptyList()) },
        ).compile(CompilationInput(SourceText.of("sample.kk", "1")))

        assertIs<CompilationResult.Failure>(result)
        assertEquals(listOf("COMPILER001"), result.diagnostics.map { it.code })
    }

    /**
     * 验证 execution engine 在编译失败时不会调用 evaluator。
     * Verifies that the execution engine does not call the evaluator after compilation failure.
     */
    @Test
    fun `execution engine uses pipeline and does not evaluate failed compilation`() {
        var evaluatorWasCalled = false
        val result = ExecutionEngine(
            compiler = CompilerPipeline(),
            evaluator = IrEvaluator {
                evaluatorWasCalled = true
                EvaluationResult(ExecutionValue.Int64(1), emptyList())
            },
        ).execute(SourceText.of("sample.kk", "@"))

        assertIs<ExecutionResult.Failure>(result)
        assertEquals(listOf("LEX001"), result.diagnostics.map { it.code })
        assertFalse(evaluatorWasCalled)
    }

    /**
     * 验证 execution engine 会求值成功编译出的 program。
     * Verifies that the execution engine evaluates a successfully compiled program.
     */
    @Test
    fun `execution engine evaluates compiled program`() {
        val result = ExecutionEngine(
            compiler = CompilerPipeline(),
            evaluator = CoreIrEvaluator(),
        ).execute(SourceText.of("sample.kk", "1 + 2"))

        assertIs<ExecutionResult.Success>(result)
        assertEquals(ExecutionValue.Int64(3), result.value)
    }

    /**
     * 验证 pipeline 产生的内部 diagnostics 保留原始源码 span。
     * Verifies that internal diagnostics produced by the pipeline preserve the original source span.
     */
    @Test
    fun `internal diagnostics preserve source spans`() {
        val source = SourceText.of("sample.kk", "1")
        val result = CompilerPipeline(
            lowerer = IrLowerer { IrLoweringResult(null, emptyList()) },
        ).compile(CompilationInput(source))

        assertIs<CompilationResult.Failure>(result)
        assertEquals(SourceSpan("sample.kk", 0, 1), result.diagnostics.single().span)
    }
}
