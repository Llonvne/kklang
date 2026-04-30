# Frontend Infrastructure Spec / 前端基础设施规范

本规范定义 `kklang` 的第一组可执行前端行为。
This spec defines the first executable frontend surface for `kklang`.

## Guiding Principle / 指导原则

用户负责语言的高层方向，并审批指导原则。
The user owns high-level language direction and approves guiding principles.

在相关指导原则获批后，Codex 负责语法机制、parser 架构、类型系统内部、编译阶段、运行/执行策略、诊断和测试的详细实现设计。
After the relevant guiding principles are approved, Codex owns detailed
implementation design for syntax mechanics, parser architecture, type-system
internals, compiler phases, runtime/execution strategy, diagnostics, and tests.

每个具体行为仍然必须先有规范证据：Markdown 规范、Kotlin DSL 规范、可执行测试，然后才是生产代码。
Every concrete behavior still needs spec evidence before implementation:
Markdown spec, Kotlin DSL spec, executable tests, then production code.

## Current Scope / 当前范围

本文只定义前端基础设施和一个种子级表达式语法。
This document specifies only frontend infrastructure and a seed expression
grammar.

种子语法用于验证可扩展性、诊断、源码位置和 parser 行为；未来语言语法和类型系统规则可以通过后续规范替换或扩展它。
The seed grammar exists to verify extensibility, diagnostics, source locations,
and parser behavior. Future language syntax and type-system rules may replace
or extend it through later spec changes.

## Source Locations / 源码位置

源码 offset 是 Kotlin `String` 中从零开始的 UTF-16 索引。
Source offsets are zero-based UTF-16 indices into the Kotlin `String`.

面向人的 line 和 column 都从一开始。
Human-readable line and column positions are one-based.

`SourceSpan` 使用半开区间：`startOffset` 包含在内，`endOffset` 不包含在内。
`SourceSpan` uses a half-open range: `startOffset` is included and `endOffset`
is excluded.

零长度 span 允许用于 EOF 或 parser 缺失 token 这类 synthetic token。
Zero-length spans are allowed for synthetic tokens such as EOF or missing parser
tokens.

## Lexer / 词法器

默认 lexer 发出以下 token kind。
The default lexer emits these token kinds.

| Kind / 类型 | Lexeme rule / 词素规则 |
| --- | --- |
| `identifier` | ASCII 字母或 `_` 开头，后接 ASCII 字母、数字或 `_` / ASCII letter or `_`, followed by ASCII letters, digits, or `_` |
| `integer` | 一个或多个 ASCII 数字 / one or more ASCII digits |
| `left_paren` | `(` |
| `right_paren` | `)` |
| `plus` | `+` |
| `minus` | `-` |
| `star` | `*` |
| `slash` | `/` |
| `whitespace` | 一个或多个 Kotlin `Char.isWhitespace()` 为 true 的字符 / one or more characters where Kotlin `Char.isWhitespace()` is true |
| `unknown` | 一个无法识别的字符 / one unrecognized character |
| `eof` | 源码末尾的零长度 synthetic token / zero-length synthetic token at the end of the source |

默认省略 whitespace token；工具可以请求发出 trivia。
Whitespace tokens are omitted by default. Tooling may request trivia emission.

Lexer 规则是有序且可扩展的。
Lexer rules are ordered and extensible.

在每个 offset，所有注册规则都会被检查；最长匹配胜出；长度相同时最早注册的规则胜出。
At each offset, all registered rules are checked, the longest match wins, and
the earliest registered rule wins ties.

未知字符会发出 `unknown` token 和 `LEX001` 诊断。
Unknown characters emit an `unknown` token and diagnostic `LEX001`.

## Parser / 解析器

默认 parser 是基于 lexer token 的 Pratt parser。
The default parser is a Pratt parser over lexer tokens.

Parser 配置可以通过 token kind 注册 prefix 和 infix parselet 进行扩展。
Parser configuration is extensible by registering prefix and infix parselets by
token kind.

种子语法支持以下形式。
The seed grammar supports these forms.

| Form / 形式 | Rule / 规则 |
| --- | --- |
| identifier expression / 标识符表达式 | `identifier` |
| integer expression / 整数表达式 | `integer` |
| grouped expression / 分组表达式 | `(` expression `)` |
| prefix expression / 前缀表达式 | `+` expression, `-` expression |
| multiplicative expression / 乘除表达式 | expression `*` expression, expression `/` expression |
| additive expression / 加减表达式 | expression `+` expression, expression `-` expression |

默认优先级从低到高如下。
Default precedence from lowest to highest:

1. 加减 / additive: `+`, `-`
2. 乘除 / multiplicative: `*`, `/`
3. 前缀 / prefix: unary `+`, unary `-`

默认二元运算符是左结合；parser 配置可以注册右结合运算符。
Default binary operators are left-associative. Parser configuration may register
right-associative operators.

## Diagnostics / 诊断

Parser 诊断如下。
Parser diagnostics:

| Code / 代码 | Meaning / 含义 |
| --- | --- |
| `PARSE001` | 需要表达式 prefix，但遇到了其他 token / expected an expression prefix but found another token |
| `PARSE002` | 完整表达式后仍有多余 token / found trailing tokens after a complete expression |
| `PARSE003` | 缺少必需 token，例如 `right_paren` / expected a required token such as `right_paren` |
