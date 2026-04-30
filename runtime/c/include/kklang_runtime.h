#ifndef KKLANG_RUNTIME_H
#define KKLANG_RUNTIME_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct kk_runtime kk_runtime;
typedef struct kk_string kk_string;

typedef enum kk_status {
    KK_OK = 0,
    KK_ERR_OOM = 1,
    KK_ERR_INVALID_ARGUMENT = 2,
} kk_status;

typedef enum kk_value_tag {
    KK_VALUE_UNIT = 0,
    KK_VALUE_BOOL = 1,
    KK_VALUE_INT64 = 2,
    KK_VALUE_STRING = 3,
    KK_VALUE_OBJECT_REF = 4,
} kk_value_tag;

typedef struct kk_value {
    kk_value_tag tag;
    union {
        bool boolean;
        int64_t int64;
        kk_string *string;
        void *object_ref;
    } as;
} kk_value;

kk_status kk_runtime_create(kk_runtime **out);
kk_status kk_runtime_destroy(kk_runtime *runtime);

kk_status kk_string_new(kk_runtime *runtime, const char *utf8, kk_string **out);
kk_status kk_string_size(const kk_string *string, size_t *out);
kk_status kk_string_data(const kk_string *string, const char **out);
kk_status kk_string_release(kk_runtime *runtime, kk_string *string);

kk_value kk_value_unit(void);
kk_value kk_value_bool(bool value);
kk_value kk_value_int64(int64_t value);
kk_value kk_value_string(kk_string *string);
kk_value kk_value_object_ref(void *object_ref);

#ifdef __cplusplus
}
#endif

#endif
