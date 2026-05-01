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

本规范不定义完整 VM、对象系统、函数调用、可变变量或完整类型系统。
This spec does not define a full VM, object system, function calls, mutable
variables, or a full type system.

## Result Model / 结果模型

执行结果必须是 `Success(value)` 或 `Failure(diagnostics)`。
Execution result must be either `Success(value)` or `Failure(diagnostics)`.

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

runtime backend 成功时只把当前 `ExecutionValue.Int64` materialize 为 `KkValue.Int64`。
On success, the runtime backend only materializes the current
`ExecutionValue.Int64` as `KkValue.Int64`.

## Core IR / Core IR

Core IR 是 typed AST 和 runtime value model 之间的中间层。
Core IR is the intermediate layer between typed AST and the runtime value model.

第一版 Core IR 只包含整数和整数运算。
The first Core IR contains only integers and integer operations.

| IR / IR | Meaning / 含义 |
| --- | --- |
| `IrProgram` | declarations plus final expression / 声明加最终表达式 |
| `IrValDeclaration` | immutable val declaration / 不可变 val 声明 |
| `IrInt64` | 64-bit signed integer literal / 64 位有符号整数字面量 |
| `IrVariable` | immutable variable reference / 不可变变量引用 |
| `IrUnary` | unary integer operation / 一元整数运算 |
| `IrBinary` | binary integer operation / 二元整数运算 |

## Supported Evaluation / 支持的求值

| Source form / 源码形式 | Behavior / 行为 |
| --- | --- |
| integer literal / 整数字面量 | returns the parsed Int64 value / 返回解析出的 Int64 值 |
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
