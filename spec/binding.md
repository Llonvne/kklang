# Binding Spec / 绑定规范

本规范定义 `kklang` 第一版变量绑定机制。
This spec defines the first variable-binding mechanism for `kklang`.

## Current Scope / 当前范围

第一版只引入 Kotlin 风格 `val` declaration。
The first version introduces only Kotlin-style `val` declarations.

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

`val` initializer 可以引用它之前已经声明的 `val`。
A `val` initializer may reference earlier `val` declarations.

DSL term / DSL 术语：`initializer can reference earlier vals`。

`val` initializer 不可以引用自身或后续 declaration。
A `val` initializer may not reference itself or later declarations.

DSL term / DSL 术语：`initializer cannot reference itself or later vals`。

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
