# Type System Spec / 类型系统规范

本规范定义 `kklang` 第一版最小类型系统骨架。
This spec defines the first minimal type-system skeleton for `kklang`.

## Current Scope / 当前范围

第一版类型系统只服务现有 seed expression grammar，不新增语言语法。
The first type system only serves the existing seed expression grammar and does
not add language syntax.

类型检查阶段位于 parsing 和 Core IR lowering 之间。
The type-checking phase sits between parsing and Core IR lowering.

Core IR lowering 只消费 typed AST，不直接决定源码表达式是否合法。
Core IR lowering consumes only typed AST and does not directly decide whether a
source expression is semantically valid.

## Type Model / 类型模型

第一版只定义 `TypeRef.Int64`。
The first version defines only `TypeRef.Int64`.

整数 literal、分组表达式、一元整数运算和二元整数运算的类型都是 `TypeRef.Int64`。
Integer literals, grouped expressions, unary integer operations, and binary
integer operations all have type `TypeRef.Int64`.

分组表达式的类型必须等于内部表达式的类型。
The type of a grouped expression must equal the type of its inner expression.

## Typed AST / Typed AST

类型检查成功时必须返回 `TypedExpression`。
Successful type checking must return `TypedExpression`.

`TypedExpression` 必须保留原始 AST、source span 和推导出的 `TypeRef`。
`TypedExpression` must preserve the original AST, source span, and inferred
`TypeRef`.

第一版 typed AST 节点如下。
The first typed AST nodes are:

| Node / 节点 | Meaning / 含义 |
| --- | --- |
| `TypedInteger` | typed integer literal / 已类型检查的整数字面量 |
| `TypedGrouped` | typed grouped expression / 已类型检查的分组表达式 |
| `TypedPrefix` | typed prefix expression / 已类型检查的前缀表达式 |
| `TypedBinary` | typed binary expression / 已类型检查的二元表达式 |

## Supported Surface / 支持表面

当前支持的 typed source forms 如下。
Currently supported typed source forms:

| Source form / 源码形式 | Type / 类型 |
| --- | --- |
| integer literal / 整数字面量 | `TypeRef.Int64` |
| grouped expression / 分组表达式 | inner expression type / 内部表达式类型 |
| unary `+` / 一元 `+` | `TypeRef.Int64` |
| unary `-` / 一元 `-` | `TypeRef.Int64` |
| binary `+` / 二元 `+` | `TypeRef.Int64` |
| binary `-` / 二元 `-` | `TypeRef.Int64` |
| binary `*` / 二元 `*` | `TypeRef.Int64` |
| binary `/` / 二元 `/` | `TypeRef.Int64` |

## Unsupported Surface / 不支持表面

identifier expression 尚无绑定或作用域语义，必须在类型检查阶段产生 `TYPE001`。
Identifier expressions do not yet have binding or scope semantics and must
produce `TYPE001` during type checking.

parser 恢复产生的 missing expression 或未定义 AST 形态必须产生 `TYPE002`。
Missing expressions produced by parser recovery or undefined AST forms must
produce `TYPE002`.

不属于当前 seed expression grammar 的 operator 必须产生 `TYPE002`。
Operators outside the current seed expression grammar must produce `TYPE002`.

## Diagnostics / 诊断

| Code / 代码 | Meaning / 含义 |
| --- | --- |
| `TYPE001` | unresolved identifier / 未解析标识符 |
| `TYPE002` | unsupported expression for current type-system scope / 当前类型系统范围不支持的表达式 |
