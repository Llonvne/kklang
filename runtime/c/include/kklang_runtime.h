#ifndef KKLANG_RUNTIME_H
#define KKLANG_RUNTIME_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/*
 * 不透明 runtime 句柄，由 C runtime 创建和销毁。
 * Opaque runtime handle created and destroyed by the C runtime.
 */
typedef struct kk_runtime kk_runtime;

/*
 * 不透明字符串句柄，由所属 runtime 管理。
 * Opaque string handle managed by its owning runtime.
 */
typedef struct kk_string kk_string;

/*
 * C ABI 的统一状态码。
 * Shared status codes for the C ABI.
 */
typedef enum kk_status {
    KK_OK = 0,
    KK_ERR_OOM = 1,
    KK_ERR_INVALID_ARGUMENT = 2,
} kk_status;

/*
 * runtime value union 的标签集合。
 * Tag set for the runtime value union.
 */
typedef enum kk_value_tag {
    KK_VALUE_UNIT = 0,
    KK_VALUE_BOOL = 1,
    KK_VALUE_INT64 = 2,
    KK_VALUE_STRING = 3,
    KK_VALUE_OBJECT_REF = 4,
} kk_value_tag;

/*
 * runtime 的最小 tagged value 表示。
 * Minimal tagged value representation for the runtime.
 */
typedef struct kk_value {
    kk_value_tag tag;
    union {
        bool boolean;
        int64_t int64;
        kk_string *string;
        void *object_ref;
    } as;
} kk_value;

/*
 * 创建 runtime，并通过 out 返回所有权。
 * Creates a runtime and returns ownership through out.
 */
kk_status kk_runtime_create(kk_runtime **out);

/*
 * 销毁 runtime，并释放其仍然拥有的字符串。
 * Destroys a runtime and releases strings it still owns.
 */
kk_status kk_runtime_destroy(kk_runtime *runtime);

/*
 * 在 runtime 中创建 UTF-8 字符串，并通过 out 返回所有权。
 * Creates a UTF-8 string in a runtime and returns ownership through out.
 */
kk_status kk_string_new(kk_runtime *runtime, const char *utf8, kk_string **out);

/*
 * 读取字符串的 UTF-8 字节长度。
 * Reads the UTF-8 byte length of a string.
 */
kk_status kk_string_size(const kk_string *string, size_t *out);

/*
 * 读取 runtime 拥有的 UTF-8 字符串数据指针。
 * Reads the runtime-owned UTF-8 string data pointer.
 */
kk_status kk_string_data(const kk_string *string, const char **out);

/*
 * 释放由指定 runtime 拥有的字符串。
 * Releases a string owned by the specified runtime.
 */
kk_status kk_string_release(kk_runtime *runtime, kk_string *string);

/*
 * 构造 Unit runtime value。
 * Constructs a Unit runtime value.
 */
kk_value kk_value_unit(void);

/*
 * 构造 Boolean runtime value。
 * Constructs a Boolean runtime value.
 */
kk_value kk_value_bool(bool value);

/*
 * 构造 Int64 runtime value。
 * Constructs an Int64 runtime value.
 */
kk_value kk_value_int64(int64_t value);

/*
 * 构造 string reference runtime value。
 * Constructs a string-reference runtime value.
 */
kk_value kk_value_string(kk_string *string);

/*
 * 构造保留的 object reference runtime value。
 * Constructs a reserved object-reference runtime value.
 */
kk_value kk_value_object_ref(void *object_ref);

#ifdef __cplusplus
}
#endif

#endif
