#include "kklang_runtime.h"

#include <stdlib.h>
#include <string.h>

struct kk_runtime {
    kk_string *strings;
};

struct kk_string {
    kk_runtime *runtime;
    char *data;
    size_t size;
    kk_string *previous;
    kk_string *next;
};

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

kk_status kk_string_size(const kk_string *string, size_t *out) {
    if (string == NULL || out == NULL) {
        return KK_ERR_INVALID_ARGUMENT;
    }

    *out = string->size;
    return KK_OK;
}

kk_status kk_string_data(const kk_string *string, const char **out) {
    if (string == NULL || out == NULL) {
        return KK_ERR_INVALID_ARGUMENT;
    }

    *out = string->data;
    return KK_OK;
}

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

kk_value kk_value_unit(void) {
    kk_value value;
    value.tag = KK_VALUE_UNIT;
    value.as.object_ref = NULL;
    return value;
}

kk_value kk_value_bool(bool boolean) {
    kk_value value;
    value.tag = KK_VALUE_BOOL;
    value.as.boolean = boolean;
    return value;
}

kk_value kk_value_int64(int64_t integer) {
    kk_value value;
    value.tag = KK_VALUE_INT64;
    value.as.int64 = integer;
    return value;
}

kk_value kk_value_string(kk_string *string) {
    kk_value value;
    value.tag = KK_VALUE_STRING;
    value.as.string = string;
    return value;
}

kk_value kk_value_object_ref(void *object_ref) {
    kk_value value;
    value.tag = KK_VALUE_OBJECT_REF;
    value.as.object_ref = object_ref;
    return value;
}

