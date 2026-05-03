# Tooling Spec / 工具链规范

本规范定义 `kklang` 的第一版 `.kk` 开发工具行为。
This spec defines the first `.kk` developer tooling behavior for `kklang`.

## Guiding Principle / 指导原则

`.kk` 工具链是语言产品的一部分，必须随语言规范一起演进。
The `.kk` tooling is part of the language product and must evolve with the
language spec.

LSP、IDEA 插件和其他编辑器支持不得各自重新定义语法；它们必须复用编译器前端或共享 tooling 模块。
The LSP, IDEA plugin, and other editor integrations must not redefine syntax on
their own; they must reuse the compiler frontend or shared tooling modules.

每次新增或修改 token、语法、诊断或语义分类时，同一变更必须更新工具链规范、测试和实现。
Every token, syntax, diagnostic, or semantic-category change must update the
tooling spec, tests, and implementation in the same change.

## Current Scope / 当前范围

第一版工具链要求 `.kk` 文件识别、语法高亮、LSP 诊断、LSP semantic tokens、IDEA 最小 PSI、IDEA 诊断标注和 IDEA 单文件运行配置。
The first tooling version requires `.kk` file recognition, syntax highlighting,
LSP diagnostics, LSP semantic tokens, IDEA minimal PSI, IDEA diagnostic
annotations, and an IDEA single-file run configuration.

第一版工具链不定义补全、跳转、重命名、格式化或代码动作。
The first tooling version does not define completion, navigation, rename,
formatting, or code actions.

## File Type / 文件类型

`kklang` 源文件扩展名是 `.kk`。
The `kklang` source file extension is `.kk`.

IDEA 插件必须把 `.kk` 注册为 `kklang` 文件类型。
The IDEA plugin must register `.kk` as the `kklang` file type.

## Highlight Categories / 高亮分类

共享高亮分类必须覆盖当前默认 lexer 的可见 token。
Shared highlighting categories must cover the visible tokens emitted by the
current default lexer.

| Category / 分类 | Token kinds / Token 类型 |
| --- | --- |
| `keyword` | `val` |
| `identifier` | `identifier` |
| `integer` | `integer` |
| `string` | `string` |
| `operator` | `plus`, `minus`, `star`, `slash`, `equals` |
| `delimiter` | `left_paren`, `right_paren`, `semicolon` |
| `whitespace` | `whitespace` |
| `unknown` | `unknown` |
| `eof` | `eof` |

默认编辑器高亮不发出 `whitespace` 和 `eof`；工具测试可以请求包含 trivia。
Default editor highlighting does not emit `whitespace` or `eof`; tooling tests
may request trivia inclusion.

未知字符必须保留为 `unknown` 分类，以便编辑器能显示错误位置。
Unknown characters must remain classified as `unknown` so editors can show the
error location.

## LSP / Language Server Protocol

LSP 服务器使用 stdio JSON-RPC 消息。
The LSP server uses stdio JSON-RPC messages.

第一版 LSP capabilities 必须声明 `textDocumentSync`、`textDocument/publishDiagnostics` 和 `textDocument/semanticTokens/full` 所需能力。
The first LSP capabilities must declare the features needed for
`textDocumentSync`, `textDocument/publishDiagnostics`, and
`textDocument/semanticTokens/full`.

DSL feature 名称固定为 `stdio-json-rpc`、`textDocumentSync`、`publishDiagnostics` 和 `semanticTokensFull`。
The fixed DSL feature names are `stdio-json-rpc`, `textDocumentSync`,
`publishDiagnostics`, and `semanticTokensFull`.

`textDocument/didOpen` 和 `textDocument/didChange` 必须重新运行编译管线，并发布当前 diagnostics。
`textDocument/didOpen` and `textDocument/didChange` must rerun the compiler
pipeline and publish current diagnostics.

诊断必须保留编译器 diagnostic code、message、source span 和 `kklang` source 名称。
Diagnostics must preserve the compiler diagnostic code, message, source span,
and `kklang` source name.

`textDocument/semanticTokens/full` 必须使用共享高亮分类生成 semantic token 数据。
`textDocument/semanticTokens/full` must use shared highlighting classification
to produce semantic token data.

## IDEA Plugin / IDEA 插件

IDEA 插件必须提供 `.kk` 文件类型、syntax highlighter、最小 PSI shell 和 diagnostic annotator。
The IDEA plugin must provide the `.kk` file type, a syntax highlighter, a
minimal PSI shell, and a diagnostic annotator.

DSL feature 名称固定为 `kk-file-type`、`syntax-highlighter`、`minimal-psi`、`diagnostic-annotator`、`single-file-run-configuration`、`current-file-run-configuration-producer` 和 `installable-plugin-zip`。
The fixed DSL feature names are `kk-file-type`, `syntax-highlighter`,
`minimal-psi`, `diagnostic-annotator`, `single-file-run-configuration`,
`current-file-run-configuration-producer`, and `installable-plugin-zip`.

IDEA syntax highlighter 必须复用共享高亮分类，不得复制 lexer 规则。
The IDEA syntax highlighter must reuse shared highlighting classification and
must not copy lexer rules.

IDEA 最小 PSI shell 只用于让 IDEA daemon 能对 `.kk` 文件运行 annotator，不定义语言语法或语义。
The IDEA minimal PSI shell exists only so the IDEA daemon can run annotators for
`.kk` files; it does not define language syntax or semantics.

IDEA diagnostic annotator 必须复用 `compiler:core` 编译管线，并把 compiler diagnostic code、message 和 source span 映射为 IDEA error annotation。
The IDEA diagnostic annotator must reuse the `compiler:core` compiler pipeline
and map compiler diagnostic codes, messages, and source spans to IDEA error
annotations.

IDEA 单文件运行配置必须保存一个 `.kk` 文件路径，运行时复用 `compiler:core` 的 `ExecutionEngine` 编译执行该文件，并在 Run console 中显示 stdout、最终表达式值或 diagnostics。
The IDEA single-file run configuration must store one `.kk` file path, reuse the
`compiler:core` `ExecutionEngine` to compile and execute that file, and show
stdout, the final expression value, or diagnostics in the Run console.

IDEA 插件必须能从当前 `.kk` 文件 context 自动生成单文件运行配置，并把该文件路径写入配置。
The IDEA plugin must be able to automatically create a single-file run
configuration from the current `.kk` file context and write that file path into
the configuration.

IDEA 插件构建必须生成可通过 “Install Plugin from Disk” 安装的 zip。
The IDEA plugin build must produce a zip installable through “Install Plugin
from Disk”.
