# Compiler Pipeline Spec / 编译管线规范

本规范定义 `kklang` 当前最小编译管线。
This spec defines the current minimal compiler pipeline for `kklang`.

## Current Scope / 当前范围

编译管线负责把源码转换为可执行后端可以消费的 Core IR program。
The compiler pipeline converts source text into a Core IR program consumable by
execution backends.

当前管线只处理已有的 seed expression grammar。
The current pipeline only handles the existing seed expression grammar.

当前管线只定义最小类型检查骨架，不定义优化、字节码生成、对象布局或完整模块系统。
The current pipeline defines only the minimal type-checking skeleton; it does
not define optimization, bytecode generation, object layout, or a full module
system.

## Inputs And Outputs / 输入与输出

编译输入必须包含 `SourceText`。
Compiler input must contain `SourceText`.

成功编译必须返回 `CompiledProgram`。
Successful compilation must return `CompiledProgram`.

成功结果类型是 `CompilationResult.Success`。
The success result type is `CompilationResult.Success`.

失败编译必须返回 diagnostics，并且不得返回部分成功 program。
Failed compilation must return diagnostics and must not return a partially
successful program.

失败结果类型是 `CompilationResult.Failure`。
The failure result type is `CompilationResult.Failure`.

## Program Model / Program 模型

当前 `AstProgram` 包含零个或多个 `val` declaration 和一个最终 expression。
The current `AstProgram` contains zero or more `val` declarations and one final
expression.

当前 `CompiledProgram` 包装一个 Core IR program。
The current `CompiledProgram` wraps one Core IR program.

当前 `CompiledProgram` 同时暴露根表达式的类型。
The current `CompiledProgram` also exposes the type of the root expression.

这个模型是有意的最小形态，后续可以扩展为 statements、declarations、modules 和 packages。
This model is intentionally minimal and may later grow into statements,
declarations, modules, and packages.

## Phase Order / 阶段顺序

编译阶段必须按以下顺序运行。
Compiler phases must run in this order.

1. lexing / 词法分析
2. parsing / 语法分析
3. type checking / 类型检查
4. lowering / Core IR 降级

如果某个阶段产生 diagnostics，管线必须立即停止，不得运行后续阶段。
If any phase produces diagnostics, the pipeline must stop immediately and must
not run later phases.

成功结果和失败结果都必须暴露已经运行过的 phase trace。
Both success and failure results must expose the phase trace that actually ran.

## Diagnostics / 诊断

编译管线本身不改写 lexer、parser 或 lowering diagnostics。
The compiler pipeline itself does not rewrite lexer, parser, or lowering
diagnostics.

诊断的 `SourceSpan` 必须保持原始阶段产生的值。
Diagnostic `SourceSpan` values must preserve the values produced by the
original phase.

如果某个内部阶段报告成功但没有产生必需产物，编译管线必须产生 `COMPILER001`。
If an internal phase reports success but does not produce its required artifact,
the compiler pipeline must produce `COMPILER001`.

| Code / 代码 | Meaning / 含义 |
| --- | --- |
| `COMPILER001` | internal compiler contract violation / 编译器内部契约违规 |

## Execution Boundary / 执行边界

执行器必须先调用编译管线。
Execution engines must call the compiler pipeline first.

如果编译失败，执行器必须返回 failure，并且不得调用 evaluator 或 runtime backend。
If compilation fails, execution engines must return failure and must not call
the evaluator or runtime backend.

成功编译后的 `CompiledProgram` 可以交给纯 Kotlin evaluator 或 Kotlin/Native runtime backend。
The successfully compiled `CompiledProgram` may be passed to the pure Kotlin
evaluator or the Kotlin/Native runtime backend.
