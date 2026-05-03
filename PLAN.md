# kklang Next Stage Plan / kklang 下一阶段计划

本文件保存当前阶段的执行计划，避免语言核心、runtime 和 IDE 工具链推进时失去顺序。
This file records the execution plan for the current stage so language core,
runtime, and IDE tooling work keeps a stable order.

## Goal / 目标

目标是在 IDEA 中支持 `.kk` 单文件的编译、运行和 C runtime 调试，同时语言本身支持 `print` IO 函数、基本类型 `String` / `Int` / `Unit`，并通过 Kotlin/Native + C runtime 编译运行。
The goal is to support compile, run, and C-runtime debugging for single `.kk`
files in IDEA while the language supports the `print` IO function, basic
`String` / `Int` / `Unit` types, and execution through Kotlin/Native + C
runtime.

## Execution Order / 执行顺序

| Order / 顺序 | Status / 状态 | Work / 工作 | Exit Criteria / 完成标准 |
| --- | --- | --- | --- |
| 1 | Done / 已完成 | Define and implement core value model: `Int`, `String`, `Unit` / 定义并实现核心值模型：`Int`、`String`、`Unit` | Markdown spec, Kotlin DSL spec, tests, compiler pipeline, evaluator all agree / Markdown 规范、Kotlin DSL 规范、测试、编译 pipeline 和 evaluator 一致 |
| 2 | Done / 已完成 | Define and implement builtin `print(value)` / 定义并实现内建 `print(value)` | `print` type-checks, lowers to Core IR, evaluates through IO abstraction, returns `Unit` / `print` 可类型检查、lower 到 Core IR、通过 IO 抽象执行，并返回 `Unit` |
| 3 | Done / 已完成 | Materialize `String` / `Int` / `Unit` through Kotlin/Native + C runtime / 通过 Kotlin/Native + C runtime materialize `String` / `Int` / `Unit` | Runtime backend returns type-safe `KkValue` and C tests cover ABI behavior / runtime backend 返回类型安全 `KkValue`，C 测试覆盖 ABI 行为 |
| 4 | Done / 已完成 | Add IDEA single-file run configuration / 增加 IDEA 单文件运行配置 | Current `.kk` file can compile and run from IDEA with diagnostics and stdout / 当前 `.kk` 文件可在 IDEA 中编译运行，并显示 diagnostics 和 stdout |
| 5 | Done / 已完成 | Add IDEA debug path for Native executable and C runtime / 增加 IDEA Native 可执行文件和 C runtime 调试路径 | Debug executor launches LLDB for the Native executable and accepts C runtime breakpoint commands / Debug executor 为 Native executable 启动 LLDB，并接受 C runtime 断点命令 |

## Constraints / 约束

- 不新增未写入规范的语言行为。
  Do not add language behavior that is not recorded in the spec.
- 每个语言行为先更新 Markdown 规范和 Kotlin DSL 规范，再写测试，再写实现。
  For each language behavior, update Markdown and Kotlin DSL specs first, then
  tests, then implementation.
- `Int` 在语言层命名为 `Int`；当前内部表示可以继续使用已有 64-bit 整数路径，直到规范需要更细的整数宽度。
  The language-level name is `Int`; the current internal representation may keep
  using the existing 64-bit integer path until the spec requires finer integer
  widths.
- 第一版 `print` 是内建函数，不引入模块系统、import 或用户自定义函数。
  The first `print` is a builtin function and does not introduce modules,
  imports, or user-defined functions.
- C runtime 负责 value materialization 和 IO 边界；表达式算术语义暂时仍保留在 compiler/Core IR 层。
  The C runtime owns value materialization and IO boundaries; expression
  arithmetic semantics remain in the compiler/Core IR layer for now.

## Current Slice / 当前切片

当前最小语言核心切片已完成；第 4 项已接入 `.kk` 单文件运行配置、当前文件自动生成配置和标准 run line marker；第 5 项已建立 Native `kkrun` debug executable、LLDB 命令、IDEA Run console debug 提示和 Debug executor 启动入口。
The current minimal language-core slice is complete; item 4 now has a `.kk`
single-file run configuration and current-file automatic configuration
creation plus a standard run line marker; item 5 now has the Native `kkrun`
debug executable and LLDB command, plus IDEA Run console debug hints and a Debug
executor launch entry point.

## Progress Log / 进度记录

- 已完成：`String` 字面量、`TypeRef.String`、`ExecutionValue.String`、runtime-backed `KkValue.String`，以及 IDEA/LSP/tooling 的字符串高亮。
  Done: `String` literals, `TypeRef.String`, `ExecutionValue.String`,
  runtime-backed `KkValue.String`, and string highlighting in IDEA/LSP/tooling.
- 已完成：`Unit` 值、内建 `print(value)`、Core IR `IrPrint`、evaluator 输出缓冲、runtime-backed `KkValue.Unit`，以及 Native backend 输出透传。
  Done: the `Unit` value, builtin `print(value)`, Core IR `IrPrint`,
  evaluator output buffering, runtime-backed `KkValue.Unit`, and Native backend
  output propagation.
- 下一步：在 IDEA 插件中提供当前 `.kk` 文件的单文件编译/运行入口，并把 diagnostics 与 stdout 显示给用户。
- 已完成：IDEA 插件注册 `kklang` 单文件运行配置，配置保存 `.kk` 文件路径，运行时复用 `ExecutionEngine`，Run console 显示 stdout、最终值或 diagnostics。
  Done: the IDEA plugin registers a `kklang` single-file run configuration; the
  configuration stores a `.kk` file path, reuses `ExecutionEngine` at run time,
  and shows stdout, the final value, or diagnostics in the Run console.
- 已完成：IDEA 插件从当前 `.kk` PSI 文件 context 自动生成单文件运行配置，避免用户手动填写文件路径。
  Done: the IDEA plugin automatically creates a single-file run configuration
  from the current `.kk` PSI file context, so the user does not need to fill the
  file path manually.
- 已完成：IDEA 插件注册标准 `runLineMarkerContributor`，让当前 `.kk` 文件通过 IDEA current-file/gutter run 入口生成同一个单文件运行配置，不写死入口文件名。
  Done: the IDEA plugin registers the standard `runLineMarkerContributor`, so
  the current `.kk` file creates the same single-file run configuration through
  IDEA current-file/gutter run entry points without hard-coding an entry file
  name.
- 下一步：建立 Native 可执行文件和 C runtime 调试路径，让 C 代码断点成为可重复工作流。
  Next: add the Native executable and C runtime debug path so C-code breakpoints
  become a repeatable workflow.
- 已完成：新增 Native `kkrun` debug executable、`KkNativeSingleFileRunner`、进程级 stdout/stderr/exit code 映射，以及 `printRuntimeSingleFileDebugCommand` 输出 LLDB C 断点命令。
  Done: added the Native `kkrun` debug executable, `KkNativeSingleFileRunner`,
  process-level stdout/stderr/exit-code mapping, and
  `printRuntimeSingleFileDebugCommand` for LLDB C-breakpoint commands.
- 下一步：把 Native debug executable 路径暴露给 IDEA 插件/运行配置，使 IDE 内调试入口不需要手工找 kexe。
  Next: expose the Native debug executable path to the IDEA plugin/run
  configuration so IDE debugging does not require manually locating the kexe.
- 已完成：IDEA 单文件运行配置会在仓库内 `.kk` 文件运行后追加 Native debug executable、Gradle 构建任务、debug 命令任务和 LLDB 命令。
  Done: the IDEA single-file run configuration appends the Native debug
  executable, Gradle build task, debug-command task, and LLDB command after
  running a `.kk` file inside the repository.
- 下一步：把上述命令升级为 IDEA Debug action 或独立 run configuration executor，直接启动可断 C runtime 的调试会话。
  Next: upgrade those commands into an IDEA Debug action or dedicated run
  configuration executor that directly starts a C-runtime-breakpoint-capable
  debug session.
- 已完成：现有 `kklang` 单文件运行配置在 Debug executor 下会先执行 Native debug executable 构建，再启动 `lldb -- kkrun.kexe <当前 .kk 文件>`；Debug console 显示 C runtime 断点提示，并把用户输入写入 LLDB stdin。
  Done: the existing `kklang` single-file run configuration now builds the
  Native debug executable under the Debug executor and then starts
  `lldb -- kkrun.kexe <current .kk file>`; the Debug console shows the C runtime
  breakpoint hint and writes user input to LLDB stdin.
- 下一步：进入语言核心后续能力规划，例如函数定义或更完整的类型检查错误恢复。
  Next: move into the next language-core capability, such as function
  definitions or fuller type-checking error recovery.
