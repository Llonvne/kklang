# Binding Spec / 绑定规范

本规范定义 `kklang` 第一版变量绑定机制。
This spec defines the first variable-binding mechanism for `kklang`.

## Current Scope / 当前范围

第一版只引入 Kotlin 风格 `val` declaration。
The first version introduces only Kotlin-style `val` declarations.

binding resolver 是 parsing 之后、type checking 之前的语言核心阶段。
The binding resolver is a language-core phase after parsing and before type
checking.

DSL term / DSL 术语：`binding resolver runs before type checking`。

binding resolver 成功时必须产生 `BoundProgram`，并保留原始 `AstProgram`、`BoundValDeclaration` 和 `BindingSymbol`。
On success, the binding resolver must produce `BoundProgram` while preserving
the original `AstProgram`, `BoundValDeclaration`, and `BindingSymbol`.

DSL term / DSL 术语：`binding resolver emits BoundProgram`。

`BoundProgram` 必须包含已绑定的最终 `BoundExpression`，每个 `BoundValDeclaration` 也必须包含已绑定的 initializer。
`BoundProgram` must contain a bound final `BoundExpression`, and every
`BoundValDeclaration` must contain a bound initializer.

DSL term / DSL 术语：`binding resolver emits BoundExpression`。

第一版 bound AST 节点如下。
The first bound AST nodes are:

| Node / 节点 | Meaning / 含义 |
| --- | --- |
| `BoundProgram` | bound program / 已绑定程序 |
| `BoundValDeclaration` | bound immutable val declaration / 已绑定不可变 val 声明 |
| `BindingScope` | ordered current-scope symbol table / 有序的当前作用域符号表 |
| `BindingSymbol` | immutable val symbol / 不可变 val 符号 |
| `BoundInteger` | bound integer literal / 已绑定整数字面量 |
| `BoundVariable` | bound variable reference carrying `BindingSymbol` / 携带 `BindingSymbol` 的已绑定变量引用 |
| `BoundGrouped` | bound grouped expression / 已绑定分组表达式 |
| `BoundPrefix` | bound prefix expression / 已绑定前缀表达式 |
| `BoundBinary` | bound binary expression / 已绑定二元表达式 |
| `BoundMissing` | parser recovery placeholder preserved for type checking / 保留给类型检查的 parser 恢复占位 |

`BoundVariable` 必须携带它解析到的 `BindingSymbol`，后续阶段不得再用字符串名字重新解析该引用。
`BoundVariable` must carry the `BindingSymbol` it resolved to; later phases
must not resolve that reference again by string name.

DSL term / DSL 术语：`BoundVariable carries BindingSymbol`。

`val` 绑定完全不可重新赋值；这里的不可变语义等同于 Kotlin `val`：名字绑定后不能被赋予新值。
`val` bindings are fully non-reassignable; immutability here follows Kotlin
`val` semantics: after a name is bound, it cannot be assigned a new value.

DSL term / DSL 术语：`val bindings are immutable`。

当前 parser 尚未保留 newline token，因此第一版使用 `;` 明确结束 `val` declaration。
The current parser does not preserve newline tokens yet, so the first version
uses `;` to terminate each `val` declaration explicitly.

## Syntax / 语法

`val` declaration 的语法如下。
The syntax of a `val` declaration is:

DSL term / DSL 术语：`val declaration`。

```text
val identifier = expression;
```

program 由零个或多个 `val` declaration 加一个最终 expression 组成。
A program consists of zero or more `val` declarations followed by one final
expression.

```text
val x = 1;
val y = x + 2;
y * 3
```

## Scope And Order / 作用域与顺序

当前只定义一个 program scope。
Only one program scope is currently defined.

当前 program scope 必须由 `BindingScope` 表示，并按 declaration 顺序保存已经成功定义的符号。
The current program scope must be represented by `BindingScope`, and it must
preserve successfully defined symbols in declaration order.

DSL term / DSL 术语：`BindingScope preserves declaration order`。

`BindingScope` 只能解析已经成功定义的符号；未定义的名字必须返回空结果并交给 resolver 产生 diagnostic。
`BindingScope` may resolve only successfully defined symbols; undefined names
must return an empty result so the resolver can emit diagnostics.

DSL term / DSL 术语：`BindingScope resolves only defined symbols`。

`val` initializer 可以引用它之前已经声明的 `val`。
A `val` initializer may reference earlier `val` declarations.

DSL term / DSL 术语：`initializer can reference earlier vals`。

`val` initializer 不可以引用自身或后续 declaration。
A `val` initializer may not reference itself or later declarations.

DSL term / DSL 术语：`initializer cannot reference itself or later vals`。

未解析 identifier 必须在 binding resolver 阶段被拒绝，并继续使用当前公开 diagnostic code `TYPE001`。
Unresolved identifiers must be rejected by the binding resolver while retaining
the current public diagnostic code `TYPE001`.

DSL term / DSL 术语：`unresolved identifiers are rejected`。

同一个 program scope 中重复声明同名 `val` 必须失败。
Redeclaring the same `val` name in the same program scope must fail.

DSL term / DSL 术语：`same-scope duplicate val is rejected`。

## Reassignment / 重新赋值

assignment expression 不是当前语法的一部分。
Assignment expressions are not part of the current grammar.

DSL term / DSL 术语：`assignment expression is not part of the grammar`。

写在 expression 位置的 `name = expression` 不是重新赋值语法；它必须被 parser 当作完整 expression 后的多余 token 处理。
`name = expression` in expression position is not reassignment syntax; the
parser must treat it as trailing tokens after a complete expression.

## Diagnostics / 诊断

| Code / 代码 | Meaning / 含义 |
| --- | --- |
| `BIND001` | duplicate immutable value in the current program scope / 当前 program scope 中重复声明不可变值 |
| `TYPE001` | unresolved identifier / 未解析标识符 |
