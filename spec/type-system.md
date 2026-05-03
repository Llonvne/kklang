# Type System Spec / 类型系统规范

本规范定义 `kklang` 第一版最小类型系统骨架。
This spec defines the first minimal type-system skeleton for `kklang`.

## Current Scope / 当前范围

第一版类型系统服务现有 seed expression grammar，以及由 `fn` modifier expansion 产生的顶层函数。
The first type system serves the existing seed expression grammar plus
top-level functions produced by `fn` modifier expansion.

类型检查阶段位于 binding 和 Core IR lowering 之间。
The type-checking phase sits between binding and Core IR lowering.

类型检查阶段只消费 binding resolver 成功产生的 `BoundProgram`。
The type-checking phase consumes only a `BoundProgram` successfully produced by
the binding resolver.

类型检查阶段必须从 `BoundExpression` 读取已经解析好的 `BindingSymbol`，不得重新按字符串名字解析变量引用。
The type-checking phase must read already-resolved `BindingSymbol` values from
`BoundExpression` and must not resolve variable references again by string name.

Core IR lowering 只消费 typed AST，不直接决定源码表达式是否合法。
Core IR lowering consumes only typed AST and does not directly decide whether a
source expression is semantically valid.

## Type Model / 类型模型

第一版定义 `TypeRef.Int64`、`TypeRef.String`、`TypeRef.Unit` 和 `TypeRef.Function`。
The first version defines `TypeRef.Int64`, `TypeRef.String`, `TypeRef.Unit`,
and `TypeRef.Function`.

整数 literal、分组表达式、一元整数运算和二元整数运算的类型都是 `TypeRef.Int64`。
Integer literals, grouped expressions, unary integer operations, and binary
integer operations all have type `TypeRef.Int64`.

字符串 literal 的类型是 `TypeRef.String`。
String literals have type `TypeRef.String`.

内建 `print(value)` 表达式接受当前所有已定义值类型，并返回 `TypeRef.Unit`。
The builtin `print(value)` expression accepts every currently defined value
type and returns `TypeRef.Unit`.

函数参数类型语法允许省略，但第一版可执行函数必须为每个参数显式写出类型。
Function parameter type syntax may omit the type, but first-version executable
functions must explicitly write a type for every parameter.

DSL term / DSL 术语：`parameter type syntax is optional`。

`Int` 类型注解映射到当前内部 `TypeRef.Int64`。
The `Int` type annotation maps to the current internal `TypeRef.Int64`.

函数返回类型从函数体最终 expression 推导，并形成 `TypeRef.Function(parameters, returns)`。
Function return type is inferred from the function body final expression and
forms `TypeRef.Function(parameters, returns)`.

DSL term / DSL 术语：`function return type is inferred from body`。

binding 成功的 `val` declaration 会把名字绑定到 initializer 的类型。
A successfully bound `val` declaration binds its name to the initializer type.

DSL term / DSL 术语：`val declaration binds initializer type`。

已绑定的 identifier expression 如果引用已绑定 `val`，它的类型等于该 `val` 的类型。
A bound identifier expression has the same type as the `val` it references.

DSL term / DSL 术语：`identifier reference uses bound val type`。

分组表达式的类型必须等于内部表达式的类型。
The type of a grouped expression must equal the type of its inner expression.

## Typed AST / Typed AST

类型检查成功时必须返回 `TypedExpression`。
Successful type checking must return `TypedExpression`.

`TypedExpression` 必须保留原始 AST、source span 和推导出的 `TypeRef`。
`TypedExpression` must preserve the original AST, source span, and inferred
`TypeRef`.

`TypedValDeclaration` 和 `TypedVariable` 必须保留 binding 阶段产生的 `BindingSymbol`。
`TypedValDeclaration` and `TypedVariable` must preserve the `BindingSymbol`
produced by the binding phase.

DSL terms / DSL 术语：`TypedValDeclaration preserves BindingSymbol`；
`TypedVariable preserves BindingSymbol`。

类型检查成功时必须返回 `TypedProgram`。
Successful type checking must return `TypedProgram`.

第一版 typed AST 节点如下。
The first typed AST nodes are:

| Node / 节点 | Meaning / 含义 |
| --- | --- |
| `TypedProgram` | typed program / 已类型检查的程序 |
| `TypedValDeclaration` | typed immutable val declaration / 已类型检查的不可变 val 声明 |
| `TypedFunctionDeclaration` | typed top-level function declaration / 已类型检查的顶层函数声明 |
| `TypedFunctionParameter` | typed function parameter / 已类型检查的函数参数 |
| `TypedInteger` | typed integer literal / 已类型检查的整数字面量 |
| `TypedString` | typed string literal / 已类型检查的字符串字面量 |
| `TypedPrintCall` | typed builtin `print` call / 已类型检查的内建 `print` 调用 |
| `TypedVariable` | typed variable reference / 已类型检查的变量引用 |
| `TypedGrouped` | typed grouped expression / 已类型检查的分组表达式 |
| `TypedPrefix` | typed prefix expression / 已类型检查的前缀表达式 |
| `TypedBinary` | typed binary expression / 已类型检查的二元表达式 |
| `TypedFunctionCall` | typed function call / 已类型检查的函数调用 |

## Supported Surface / 支持表面

当前支持的 typed source forms 如下。
Currently supported typed source forms:

| Source form / 源码形式 | Type / 类型 |
| --- | --- |
| `val` declaration / `val` 声明 | initializer type / initializer 类型 |
| identifier reference / 标识符引用 | referenced `val` type / 被引用 `val` 的类型 |
| integer literal / 整数字面量 | `TypeRef.Int64` |
| string literal / 字符串字面量 | `TypeRef.String` |
| builtin `print(value)` / 内建 `print(value)` | `TypeRef.Unit` |
| top-level function declaration / 顶层函数声明 | `TypeRef.Function(parameters, returns)` |
| function call / 函数调用 | function return type / 函数返回类型 |
| grouped expression / 分组表达式 | inner expression type / 内部表达式类型 |
| unary `+` / 一元 `+` | `TypeRef.Int64` |
| unary `-` / 一元 `-` | `TypeRef.Int64` |
| binary `+` / 二元 `+` | `TypeRef.Int64` |
| binary `-` / 二元 `-` | `TypeRef.Int64` |
| binary `*` / 二元 `*` | `TypeRef.Int64` |
| binary `/` / 二元 `/` | `TypeRef.Int64` |

## Unsupported Surface / 不支持表面

未解析 identifier expression 必须在 binding resolver 阶段产生 `TYPE001`，不得进入类型检查。
Unresolved identifier expressions must produce `TYPE001` in the binding
resolver phase and must not enter type checking.

parser 恢复产生的 missing expression 或未定义 AST 形态必须产生 `TYPE002`。
Missing expressions produced by parser recovery or undefined AST forms must
produce `TYPE002`.

不属于当前 seed expression grammar 的 operator 必须产生 `TYPE002`。
Operators outside the current seed expression grammar must produce `TYPE002`.

未知调用名必须在 binding 阶段产生 `TYPE001`；当前类型系统只接受已经绑定的内建 `print` 调用。
Unknown call names must produce `TYPE001` during binding; the current type
system accepts already-bound builtin `print` calls and function calls.

## Diagnostics / 诊断

| Code / 代码 | Meaning / 含义 |
| --- | --- |
| `TYPE001` | unresolved identifier emitted by binding before type checking / 类型检查前由 binding 发出的未解析标识符 |
| `TYPE002` | unsupported expression for current type-system scope / 当前类型系统范围不支持的表达式 |
| `TYPE003` | unknown type annotation / 未知类型注解 |
| `TYPE004` | missing parameter type for executable function / 可执行函数缺少参数类型 |
| `TYPE005` | function arity mismatch / 函数参数数量不匹配 |
| `TYPE006` | function argument type mismatch / 函数参数类型不匹配 |
