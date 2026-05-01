# Runtime Spec / 运行时规范

本规范定义 `kklang` 最小运行时的第一版 ABI 和 Kotlin/Native 封装行为。
This spec defines the first ABI and Kotlin/Native wrapper behavior for the
minimal `kklang` runtime.

## Guiding Principle / 指导原则

运行时分为两层：C 层提供稳定 ABI，Kotlin/Native 层提供类型安全封装。
The runtime has two layers: the C layer provides a stable ABI, and the
Kotlin/Native layer provides a type-safe wrapper.

最小运行时只提供机制，不定义高级语言语义。
The minimal runtime provides mechanisms only; it does not define high-level
language semantics.

运行时行为必须先写入 Markdown 规范、Kotlin DSL 规范和测试，然后再实现。
Runtime behavior must be written into the Markdown spec, Kotlin DSL spec, and
tests before implementation.

## C ABI / C ABI

所有 public C 符号使用 `kk_` 前缀。
All public C symbols use the `kk_` prefix.

C ABI 使用显式 status code；除非函数签名没有错误通道，否则无效参数必须返回 `KK_ERR_INVALID_ARGUMENT`。
The C ABI uses explicit status codes; invalid arguments must return
`KK_ERR_INVALID_ARGUMENT` unless the function signature has no error channel.

第一版 status code 如下。
The first status codes are:

| Code / 代码 | Meaning / 含义 |
| --- | --- |
| `KK_OK` | 操作成功 / operation succeeded |
| `KK_ERR_OOM` | 内存分配失败 / memory allocation failed |
| `KK_ERR_INVALID_ARGUMENT` | 参数无效 / invalid argument |

## Runtime Lifetime / 运行时生命周期

`kk_runtime_create` 创建一个 runtime，并通过 out 参数返回所有权。
`kk_runtime_create` creates a runtime and returns ownership through an out
parameter.

如果 out 参数为 null，`kk_runtime_create` 必须返回 `KK_ERR_INVALID_ARGUMENT`。
If the out parameter is null, `kk_runtime_create` must return
`KK_ERR_INVALID_ARGUMENT`.

`kk_runtime_destroy` 销毁 runtime。runtime 参数为 null 时必须返回 `KK_ERR_INVALID_ARGUMENT`。
`kk_runtime_destroy` destroys a runtime. A null runtime argument must return
`KK_ERR_INVALID_ARGUMENT`.

销毁成功后，传入指针不再可用。
After successful destruction, the passed pointer is no longer valid.

销毁 runtime 会释放仍由该 runtime 持有的字符串。
Destroying a runtime releases strings still owned by that runtime.

## String Lifetime / 字符串生命周期

`kk_string_new` 在 runtime 中创建 UTF-8 字符串，并通过 out 参数返回所有权。
`kk_string_new` creates a UTF-8 string in a runtime and returns ownership through
an out parameter.

`kk_string_new` 不验证 UTF-8 内容；它按字节复制以 null 结尾的 C 字符串。
`kk_string_new` does not validate UTF-8 content; it copies bytes from a
null-terminated C string.

`kk_string_size` 返回字符串字节数，不包含末尾的 null 字节。
`kk_string_size` returns the string size in bytes, excluding the trailing null
byte.

`kk_string_data` 返回 runtime-owned 的只读 null-terminated 数据指针。
`kk_string_data` returns a runtime-owned read-only null-terminated data pointer.

`kk_string_release` 释放字符串。runtime 或 string 参数为 null，或 string 不属于该 runtime 时，必须返回 `KK_ERR_INVALID_ARGUMENT`。
`kk_string_release` releases a string. A null runtime or string argument, or a
string not owned by that runtime, must return `KK_ERR_INVALID_ARGUMENT`.

## Value Model / 值模型

第一版值模型只定义 ABI tag，不定义表达式求值语义。
The first value model defines ABI tags only; it does not define expression
evaluation semantics.

| Tag / 标签 | Meaning / 含义 |
| --- | --- |
| `KK_VALUE_UNIT` | Unit 值 / Unit value |
| `KK_VALUE_BOOL` | Boolean 值 / Boolean value |
| `KK_VALUE_INT64` | 64-bit signed integer / 64 位有符号整数 |
| `KK_VALUE_STRING` | Runtime-owned string reference / runtime 拥有的字符串引用 |
| `KK_VALUE_OBJECT_REF` | 预留对象引用 / reserved object reference |

## Kotlin/Native Wrapper / Kotlin/Native 封装

Kotlin/Native 层不得向上层暴露裸 C 指针。
The Kotlin/Native layer must not expose raw C pointers to higher layers.

`KkRuntime.create()` 返回一个 typed result；成功时包含可关闭的 `KkRuntime`。
`KkRuntime.create()` returns a typed result; on success it contains a closable
`KkRuntime`.

`KkRuntime.string(text)` 创建可关闭的 `KkString`。
`KkRuntime.string(text)` creates a closable `KkString`.

`KkValue` 是 runtime-backed value 的类型安全 Kotlin/Native 表示。
`KkValue` is the type-safe Kotlin/Native representation of a runtime-backed
value.

当前 `KkValue.Int64` 必须通过 C ABI `kk_value_int64` materialize。
The current `KkValue.Int64` must be materialized through the C ABI
`kk_value_int64`.

`close()` 是幂等的。第一次调用释放底层 C 资源，后续调用不再调用 C ABI。
`close()` is idempotent. The first call releases the underlying C resource, and
later calls do not call the C ABI again.

关闭 `KkRuntime` 会把它创建且尚未关闭的 `KkString` 标记为 closed。
Closing `KkRuntime` marks any still-open `KkString` created by it as closed.

关闭后的 runtime 或 string 再被使用，必须抛出 `IllegalStateException`。
Using a closed runtime or string must throw `IllegalStateException`.

## Native Runtime Backend / Native 运行时后端

`KkRuntimeExecutionEngine` 是第一版 Kotlin/Native runtime backend。
`KkRuntimeExecutionEngine` is the first Kotlin/Native runtime backend.

backend 先调用 compiler pipeline，再复用当前 Core IR evaluator，最后把成功值 materialize 为 `KkValue`。
The backend first calls the compiler pipeline, then reuses the current Core IR
evaluator, and finally materializes successful values as `KkValue`.

如果 compiler pipeline 失败，`KkRuntimeExecutionResult.Failure` 必须直接返回 compiler diagnostics。
If the compiler pipeline fails, `KkRuntimeExecutionResult.Failure` must directly
return compiler diagnostics.

如果 Core IR evaluator 失败，`KkRuntimeExecutionResult.Failure` 必须直接返回 evaluator diagnostics。
If the Core IR evaluator fails, `KkRuntimeExecutionResult.Failure` must directly
return evaluator diagnostics.

如果求值成功并产生 `ExecutionValue.Int64`，backend 必须返回 `KkRuntimeExecutionResult.Success(KkValue.Int64)`。
If evaluation succeeds and produces `ExecutionValue.Int64`, the backend must
return `KkRuntimeExecutionResult.Success(KkValue.Int64)`.

Native runtime backend 不定义新的语言语义，也不把算术语义下沉到 C runtime。
The Native runtime backend does not define new language semantics and does not
move arithmetic semantics into the C runtime.

## Runtime Debuggability / 运行时可调试性

Kotlin/Native host test 链接的普通 C runtime archive 必须保留
`C debug symbols for Kotlin Native host tests`。
The normal C runtime archive linked into Kotlin/Native host tests must keep
`C debug symbols for Kotlin Native host tests`.

普通 C runtime object 必须使用 `-O0` 和 `-g` 编译，使 LLDB 可以在
`kklang_runtime.c` 的 C 函数上设置源码断点。
The normal C runtime object must be compiled with `-O0` and `-g`, so LLDB can
bind source breakpoints in C functions from `kklang_runtime.c`.

coverage 专用 C object 继续独立编译，并继续携带 coverage instrumentation。
The coverage-only C object remains separately compiled and keeps coverage
instrumentation.

`printRuntimeHostDebugCommand` 必须打印 Kotlin/Native host test executable、C
runtime source、C runtime debug object 和最小 LLDB 命令。
`printRuntimeHostDebugCommand` must print the Kotlin/Native host test
executable, C runtime source, C runtime debug object, and the minimal LLDB
commands.

在设置 C 源码断点前，调试命令必须先通过 `target modules add` 加载 C runtime
debug object。
Before setting C source breakpoints, the debug commands must first load the C
runtime debug object through `target modules add`.
