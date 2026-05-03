# Execution Spec / 执行规范

本规范定义 `kklang` 第一版最小执行链路。
This spec defines the first minimal execution path for `kklang`.

## Current Scope / 当前范围

第一版执行链路只连接现有 seed expression grammar。
The first execution path only connects the existing seed expression grammar.

执行顺序如下：source text、lexer、parser、binding、type checking、Core IR lowering、Core IR evaluation、execution result。
The execution order is: source text, lexer, parser, binding, type checking,
Core IR lowering, Core IR evaluation, execution result.

执行器必须通过 compiler pipeline 进入 lexer、parser 和 Core IR lowering。
The execution engine must enter lexing, parsing, and Core IR lowering through
the compiler pipeline.

本规范不定义完整 VM、对象系统、用户自定义函数调用、可变变量或完整类型系统。
This spec does not define a full VM, object system, user-defined function
calls, mutable variables, or a full type system.

## Result Model / 结果模型

执行结果必须是 `Success(value, output)` 或 `Failure(diagnostics, output)`。
Execution result must be either `Success(value, output)` or
`Failure(diagnostics, output)`.

如果 lexer、parser、binding resolver、type checker、lowering 或 evaluator 产生诊断，执行结果必须是 failure。
If the lexer, parser, binding resolver, type checker, lowering, or evaluator
produces diagnostics, execution must return failure.

编译错误不得进入 Core IR evaluation。
Compilation errors must not enter Core IR evaluation.

如果 compiler pipeline 返回 failure，执行器不得调用 evaluator。
If the compiler pipeline returns failure, the execution engine must not call the
evaluator.

Kotlin/Native runtime backend 使用 `KkRuntimeExecutionEngine`，它复用本规范定义的 compiler pipeline 和 Core IR evaluator。
The Kotlin/Native runtime backend uses `KkRuntimeExecutionEngine`, which reuses
the compiler pipeline and Core IR evaluator defined by this spec.

runtime backend 成功时把当前 `ExecutionValue.Int64`、`ExecutionValue.String` 或 `ExecutionValue.Unit` materialize 为对应的 `KkValue`。
On success, the runtime backend materializes the current `ExecutionValue.Int64`,
`ExecutionValue.String`, or `ExecutionValue.Unit` as the corresponding
`KkValue`.

对应关系是 `ExecutionValue.Int64` 到 `KkValue.Int64`、`ExecutionValue.String` 到 `KkValue.String`、`ExecutionValue.Unit` 到 `KkValue.Unit`。
The mapping is `ExecutionValue.Int64` to `KkValue.Int64`,
`ExecutionValue.String` to `KkValue.String`, and `ExecutionValue.Unit` to
`KkValue.Unit`.

Native 单文件执行入口使用 `KkNativeSingleFileRunner`，它把 `KkRuntimeExecutionEngine` 的结果映射为进程级 stdout、stderr 和 exit code。
The Native single-file execution entry point uses `KkNativeSingleFileRunner`,
which maps `KkRuntimeExecutionEngine` results into process-level stdout,
stderr, and exit code.

## Core IR / Core IR

Core IR 是 typed AST 和 runtime value model 之间的中间层。
Core IR is the intermediate layer between typed AST and the runtime value model.

第一版 Core IR 包含整数、字符串、内建 `print` 和整数运算。
The first Core IR contains integers, strings, builtin `print`, and integer
operations.

| IR / IR | Meaning / 含义 |
| --- | --- |
| `IrProgram` | declarations plus final expression / 声明加最终表达式 |
| `IrValDeclaration` | immutable val declaration / 不可变 val 声明 |
| `IrInt64` | 64-bit signed integer literal / 64 位有符号整数字面量 |
| `IrString` | string literal / 字符串字面量 |
| `IrPrint` | builtin print side effect returning Unit / 返回 Unit 的内建 print 副作用 |
| `IrVariable` | immutable variable reference / 不可变变量引用 |
| `IrUnary` | unary integer operation / 一元整数运算 |
| `IrBinary` | binary integer operation / 二元整数运算 |

## Supported Evaluation / 支持的求值

| Source form / 源码形式 | Behavior / 行为 |
| --- | --- |
| integer literal / 整数字面量 | returns the parsed Int64 value / 返回解析出的 Int64 值 |
| string literal / 字符串字面量 | returns the literal string value / 返回字符串字面量值 |
| builtin `print(value)` / 内建 `print(value)` | evaluates `value`, appends its text form to output without a newline, and returns `Unit` / 先求值 `value`，把其文本形式无换行追加到输出，并返回 `Unit` |
| immutable `val` declaration / 不可变 `val` 声明 | evaluates initializer once and binds the name / 对 initializer 求值一次并绑定名字 |
| identifier reference / 标识符引用 | returns the value bound by an earlier `val` / 返回之前 `val` 绑定的值 |
| grouped expression / 分组表达式 | evaluates the contained expression / 求值内部表达式 |
| unary `+` / 一元 `+` | returns operand unchanged / 原样返回操作数 |
| unary `-` / 一元 `-` | returns negated operand / 返回操作数取负 |
| binary `+` / 二元 `+` | checked Int64 addition / 检查溢出的 Int64 加法 |
| binary `-` / 二元 `-` | checked Int64 subtraction / 检查溢出的 Int64 减法 |
| binary `*` / 二元 `*` | checked Int64 multiplication / 检查溢出的 Int64 乘法 |
| binary `/` / 二元 `/` | Int64 division truncating toward zero / 向零截断的 Int64 除法 |

所有整数溢出必须产生 `EXEC003`。
All integer overflow must produce `EXEC003`.

除以零必须产生 `EXEC002`。
Division by zero must produce `EXEC002`.

`print` 输出的文本形式如下：`Int64` 使用十进制文本，`String` 使用字面量内容，`Unit` 使用 `Unit`。
The text form written by `print` is: decimal text for `Int64`, literal content
for `String`, and `Unit` for `Unit`.

## Unsupported Surface / 不支持的表面

未绑定的 identifier expression 必须在 binding resolver 阶段产生 `TYPE001`。
Unbound identifier expressions must produce `TYPE001` during binding
resolution.

malformed typed expression 或 lowering 防御分支必须产生 `EXEC001`。
Malformed typed expressions or lowering defensive branches must produce
`EXEC001`.

## Diagnostics / 诊断

| Code / 代码 | Meaning / 含义 |
| --- | --- |
| `EXEC001` | unsupported expression for current execution scope / 当前执行范围不支持的表达式 |
| `EXEC002` | division by zero / 除以零 |
| `EXEC003` | Int64 overflow / Int64 溢出 |
