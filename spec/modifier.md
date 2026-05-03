# Modifier Spec / Modifier 规范

本规范定义 `kklang` 第一版声明级元编程机制。
This spec defines the first declaration-level metaprogramming mechanism for
`kklang`.

## Current Scope / 当前范围

`modifier` 是第一版唯一的元编程语法入口。
`modifier` is the only metaprogramming syntax entry point in the first version.

DSL term / DSL 术语：`modifier declaration`。

第一版 `modifier` 只定义声明形状匹配和结构化 AST expansion，不执行任意编译期代码。
The first `modifier` version only defines declaration-shape matching and
structured AST expansion; it does not execute arbitrary compile-time code.

DSL term / DSL 术语：`declarative modifier expansion`。

`fn` 是第一批由 `modifier` 定义的 modifier；parser 不把 `fn` 作为硬编码函数语法。
`fn` is the first modifier defined through `modifier`; the parser does not treat
`fn` as hard-coded function syntax.

DSL term / DSL 术语：`fn modifier`。

## Syntax / 语法

`modifier` declaration 的语法如下。
The syntax of a `modifier` declaration is:

```text
modifier identifier {
    modifier-pattern
}
```

第一版 canonical `fn` modifier pattern 如下。
The first canonical `fn` modifier pattern is:

```text
modifier fn {
    [modifiers] fn [identifier]([identifier:type?]) {
        [body]
    }
}
```

`[modifiers]` 为未来声明修饰符保留；第一版函数声明不得携带额外 modifier。
`[modifiers]` is reserved for future declaration modifiers; first-version
function declarations must not carry additional modifiers.

`[identifier:type?]` 表示参数语法允许省略类型；第一版可执行语义仍要求每个参数显式写出类型。
`[identifier:type?]` means parameter syntax may omit the type; first-version
executable semantics still require every parameter to write an explicit type.

DSL term / DSL 术语：`parameter type syntax is optional`。

## Expansion / 展开

parser 必须把未知 identifier 开头并包含 block 的顶层声明解析为 `RawModifierApplication`。
The parser must parse an unknown top-level identifier-headed declaration with a
block as `RawModifierApplication`.

DSL term / DSL 术语：`RawModifierApplication`。

modifier expansion 阶段位于 parsing 和 binding 之间。
The modifier expansion phase runs between parsing and binding.

DSL term / DSL 术语：`modifier expansion runs before binding`。

`fn` modifier application 必须 expansion 为结构化 `FunctionDeclaration`，而不是字符串替换。
An `fn` modifier application must expand into a structured
`FunctionDeclaration`, not string replacement.

DSL term / DSL 术语：`FunctionDeclaration`。

第一版 `fn` application 语法如下。
The first `fn` application syntax is:

```text
fn identifier(parameter-list) {
    val-declaration*
    expression
}
```

`parameter-list` 可以为空，也可以是以逗号分隔的 `identifier` 或 `identifier: Type`。
`parameter-list` may be empty, or it may contain comma-separated `identifier`
or `identifier: Type` entries.

函数体是 block program：零个或多个 `val` declaration，后接一个最终 expression。
A function body is a block program: zero or more `val` declarations followed by
one final expression.

## Function Semantics / 函数语义

第一版函数只支持顶层 named function。
The first version supports only top-level named functions.

DSL term / DSL 术语：`top-level named function`。

函数不是 first-class value；函数不能赋值给 `val`，也不能作为参数传递。
Functions are not first-class values; a function cannot be assigned to `val` or
passed as an argument.

DSL term / DSL 术语：`functions are not first-class values`。

函数声明按源码顺序绑定；函数体和后续代码只能引用更早成功绑定的函数。
Function declarations bind in source order; function bodies and later code may
reference only earlier successfully bound functions.

DSL term / DSL 术语：`function declarations bind in source order`。

第一版禁止递归和 forward reference，但 AST、binding 和 type model 必须保留未来扩展空间。
The first version forbids recursion and forward references, but the AST,
binding, and type model must keep room for future extension.

DSL term / DSL 术语：`recursion and forward reference are forbidden`。

函数返回类型由 body 最终 expression 推导。
The function return type is inferred from the body final expression.

DSL term / DSL 术语：`function return type is inferred from body`。

## Diagnostics / 诊断

| Code / 代码 | Meaning / 含义 |
| --- | --- |
| `MOD001` | unknown modifier application / 未知 modifier application |
| `MOD002` | duplicate modifier declaration / 重复 modifier declaration |
| `MOD003` | modifier pattern or application shape mismatch / modifier pattern 或 application 形状不匹配 |
