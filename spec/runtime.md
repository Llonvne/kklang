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

`close()` 是幂等的。第一次调用释放底层 C 资源，后续调用不再调用 C ABI。
`close()` is idempotent. The first call releases the underlying C resource, and
later calls do not call the C ABI again.

关闭 `KkRuntime` 会把它创建且尚未关闭的 `KkString` 标记为 closed。
Closing `KkRuntime` marks any still-open `KkString` created by it as closed.

关闭后的 runtime 或 string 再被使用，必须抛出 `IllegalStateException`。
Using a closed runtime or string must throw `IllegalStateException`.
