# kklang LSP / kklang LSP

本模块提供 `kklang` 的最小 Language Server Protocol 服务器。
This module provides the minimal Language Server Protocol server for `kklang`.

## Scope / 范围

第一版 LSP 支持 stdio JSON-RPC、`initialize`、`shutdown`、`exit`、`textDocument/didOpen`、`textDocument/didChange` 和 `textDocument/semanticTokens/full`。
The first LSP version supports stdio JSON-RPC, `initialize`, `shutdown`, `exit`,
`textDocument/didOpen`, `textDocument/didChange`, and
`textDocument/semanticTokens/full`.

诊断来自 `compiler:core` 编译管线；semantic tokens 来自共享 `tooling:highlighting` 分类器。
Diagnostics come from the `compiler:core` compiler pipeline; semantic tokens
come from the shared `tooling:highlighting` classifier.

## Build / 构建

```bash
JAVA_HOME=/Users/llonvne/Library/Java/JavaVirtualMachines/openjdk-25.0.2/Contents/Home ./gradlew :tooling:lsp:installDist
```

## Run / 运行

构建后的可执行脚本位于 `tooling/lsp/build/install/lsp/bin/lsp`。
The built executable script is located at `tooling/lsp/build/install/lsp/bin/lsp`.

```bash
tooling/lsp/build/install/lsp/bin/lsp
```

该进程通过 stdin/stdout 与编辑器通信。
The process communicates with editors through stdin/stdout.
