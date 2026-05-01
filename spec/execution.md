# Execution Spec / 执行规范

本规范定义 `kklang` 第一版最小执行链路。
This spec defines the first minimal execution path for `kklang`.

## Current Scope / 当前范围

第一版执行链路只连接现有 seed expression grammar。
The first execution path only connects the existing seed expression grammar.

执行顺序如下：source text、lexer、parser、Core IR lowering、Core IR evaluation、execution result。
The execution order is: source text, lexer, parser, Core IR lowering, Core IR
evaluation, execution result.

执行器必须通过 compiler pipeline 进入 lexer、parser 和 Core IR lowering。
The execution engine must enter lexing, parsing, and Core IR lowering through
the compiler pipeline.

本规范不定义完整 VM、对象系统、函数调用、变量绑定或类型系统。
This spec does not define a full VM, object system, function calls, bindings, or
type system.

## Result Model / 结果模型

执行结果必须是 `Success(value)` 或 `Failure(diagnostics)`。
Execution result must be either `Success(value)` or `Failure(diagnostics)`.

如果 lexer、parser、lowering 或 evaluator 产生诊断，执行结果必须是 failure。
If the lexer, parser, lowering, or evaluator produces diagnostics, execution
must return failure.

编译错误不得进入 Core IR evaluation。
Compilation errors must not enter Core IR evaluation.

如果 compiler pipeline 返回 failure，执行器不得调用 evaluator。
If the compiler pipeline returns failure, the execution engine must not call the
evaluator.

## Core IR / Core IR

Core IR 是 parser AST 和 runtime value model 之间的中间层。
Core IR is the intermediate layer between parser AST and the runtime value
model.

第一版 Core IR 只包含整数和整数运算。
The first Core IR contains only integers and integer operations.

| IR / IR | Meaning / 含义 |
| --- | --- |
| `IrInt64` | 64-bit signed integer literal / 64 位有符号整数字面量 |
| `IrUnary` | unary integer operation / 一元整数运算 |
| `IrBinary` | binary integer operation / 二元整数运算 |

## Supported Evaluation / 支持的求值

| Source form / 源码形式 | Behavior / 行为 |
| --- | --- |
| integer literal / 整数字面量 | returns the parsed Int64 value / 返回解析出的 Int64 值 |
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

identifier expression 尚无执行语义，必须产生 `EXEC001`。
Identifier expressions do not have execution semantics yet and must produce
`EXEC001`.

missing expression 或未定义的 AST 形态必须产生 `EXEC001`。
Missing expressions or undefined AST forms must produce `EXEC001`.

## Diagnostics / 诊断

| Code / 代码 | Meaning / 含义 |
| --- | --- |
| `EXEC001` | unsupported expression for current execution scope / 当前执行范围不支持的表达式 |
| `EXEC002` | division by zero / 除以零 |
| `EXEC003` | Int64 overflow / Int64 溢出 |
