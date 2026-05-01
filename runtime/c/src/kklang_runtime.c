#include "kklang_runtime.h"

#include <stdlib.h>
#include <string.h>

/*
 * runtime 内部结构，当前只维护归属字符串链表头。
 * Internal runtime structure that currently stores only the owned string list head.
 */
struct kk_runtime {
    kk_string *strings;
};

/*
 * 字符串内部结构，保存 UTF-8 数据和 runtime 拥有链表链接。
 * Internal string structure storing UTF-8 data and runtime ownership-list links.
 */
struct kk_string {
    kk_runtime *runtime;
    char *data;
    size_t size;
    kk_string *previous;
    kk_string *next;
};

/*
 * 创建 runtime，并在失败时保持 out 为 NULL。
 * Creates a runtime and leaves out as NULL on failure.
 */
kk_status kk_runtime_create(kk_runtime **out) {
    if (out == NULL) {
        return KK_ERR_INVALID_ARGUMENT;
    }

    *out = NULL;
    kk_runtime *runtime = (kk_runtime *)calloc(1, sizeof(kk_runtime));
    if (runtime == NULL) {
        return KK_ERR_OOM;
    }

    *out = runtime;
    return KK_OK;
}

/*
 * 销毁 runtime，并释放其仍然拥有的所有字符串。
 * Destroys a runtime and releases every string it still owns.
 */
kk_status kk_runtime_destroy(kk_runtime *runtime) {
    if (runtime == NULL) {
        return KK_ERR_INVALID_ARGUMENT;
    }

    kk_string *current = runtime->strings;
    while (current != NULL) {
        kk_string *next = current->next;
        free(current->data);
        free(current);
        current = next;
    }

    free(runtime);
    return KK_OK;
}

/*
 * 复制 UTF-8 输入并把新字符串挂到 runtime 拥有链表头部。
 * Copies UTF-8 input and links the new string at the head of the runtime-owned list.
 */
kk_status kk_string_new(kk_runtime *runtime, const char *utf8, kk_string **out) {
    if (runtime == NULL || utf8 == NULL || out == NULL) {
        return KK_ERR_INVALID_ARGUMENT;
    }

    *out = NULL;
    size_t size = strlen(utf8);
    kk_string *string = (kk_string *)calloc(1, sizeof(kk_string));
    if (string == NULL) {
        return KK_ERR_OOM;
    }

    char *data = (char *)malloc(size + 1);
    if (data == NULL) {
        free(string);
        return KK_ERR_OOM;
    }

    memcpy(data, utf8, size + 1);
    string->runtime = runtime;
    string->data = data;
    string->size = size;
    string->next = runtime->strings;
    if (runtime->strings != NULL) {
        runtime->strings->previous = string;
    }
    runtime->strings = string;

    *out = string;
    return KK_OK;
}

/*
 * 读取字符串 UTF-8 字节长度。
 * Reads the UTF-8 byte length of a string.
 */
kk_status kk_string_size(const kk_string *string, size_t *out) {
    if (string == NULL || out == NULL) {
        return KK_ERR_INVALID_ARGUMENT;
    }

    *out = string->size;
    return KK_OK;
}

/*
 * 读取 runtime 拥有的 NUL 结尾 UTF-8 数据指针。
 * Reads the runtime-owned NUL-terminated UTF-8 data pointer.
 */
kk_status kk_string_data(const kk_string *string, const char **out) {
    if (string == NULL || out == NULL) {
        return KK_ERR_INVALID_ARGUMENT;
    }

    *out = string->data;
    return KK_OK;
}

/*
 * 从 runtime 拥有链表中摘除并释放指定字符串。
 * Unlinks and releases the specified string from the runtime-owned list.
 */
kk_status kk_string_release(kk_runtime *runtime, kk_string *string) {
    if (runtime == NULL || string == NULL || string->runtime != runtime) {
        return KK_ERR_INVALID_ARGUMENT;
    }

    if (string->previous != NULL) {
        string->previous->next = string->next;
    } else {
        runtime->strings = string->next;
    }

    if (string->next != NULL) {
        string->next->previous = string->previous;
    }

    free(string->data);
    free(string);
    return KK_OK;
}

/*
 * 构造 Unit value，并清空 union 的 object_ref 字段。
 * Constructs a Unit value and clears the union's object_ref field.
 */
kk_value kk_value_unit(void) {
    kk_value value;
    value.tag = KK_VALUE_UNIT;
    value.as.object_ref = NULL;
    return value;
}

/*
 * 构造 Boolean value。
 * Constructs a Boolean value.
 */
kk_value kk_value_bool(bool boolean) {
    kk_value value;
    value.tag = KK_VALUE_BOOL;
    value.as.boolean = boolean;
    return value;
}

/*
 * 构造 Int64 value。
 * Constructs an Int64 value.
 */
kk_value kk_value_int64(int64_t integer) {
    kk_value value;
    value.tag = KK_VALUE_INT64;
    value.as.int64 = integer;
    return value;
}

/*
 * 构造 string reference value，不接管字符串所有权。
 * Constructs a string-reference value without taking string ownership.
 */
kk_value kk_value_string(kk_string *string) {
    kk_value value;
    value.tag = KK_VALUE_STRING;
    value.as.string = string;
    return value;
}

/*
 * 构造保留的 object reference value。
 * Constructs a reserved object-reference value.
 */
kk_value kk_value_object_ref(void *object_ref) {
    kk_value value;
    value.tag = KK_VALUE_OBJECT_REF;
    value.as.object_ref = object_ref;
    return value;
}
