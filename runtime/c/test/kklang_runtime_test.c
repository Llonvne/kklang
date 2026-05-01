#include "kklang_runtime.h"

#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

static int fail_next_calloc = 0;
static int fail_next_malloc = 0;

/*
 * 测试用 calloc wrapper，在标志位打开时模拟一次 OOM。
 * Test calloc wrapper that simulates one OOM when the flag is set.
 */
void *kk_test_calloc(size_t count, size_t size) {
    if (fail_next_calloc != 0) {
        fail_next_calloc = 0;
        return NULL;
    }
    return calloc(count, size);
}

/*
 * 测试用 malloc wrapper，在标志位打开时模拟一次 OOM。
 * Test malloc wrapper that simulates one OOM when the flag is set.
 */
void *kk_test_malloc(size_t size) {
    if (fail_next_malloc != 0) {
        fail_next_malloc = 0;
        return NULL;
    }
    return malloc(size);
}

/*
 * 让下一次 calloc 调用失败。
 * Makes the next calloc call fail.
 */
static void fail_calloc_once(void) {
    fail_next_calloc = 1;
}

/*
 * 让下一次 malloc 调用失败。
 * Makes the next malloc call fail.
 */
static void fail_malloc_once(void) {
    fail_next_malloc = 1;
}

/*
 * 测试 runtime 创建、非法参数和销毁生命周期。
 * Tests runtime creation, invalid arguments, and destroy lifetime.
 */
static void test_runtime_lifetime(void) {
    kk_runtime *runtime = NULL;

    assert(kk_runtime_create(NULL) == KK_ERR_INVALID_ARGUMENT);
    assert(kk_runtime_create(&runtime) == KK_OK);
    assert(runtime != NULL);
    assert(kk_runtime_destroy(NULL) == KK_ERR_INVALID_ARGUMENT);
    assert(kk_runtime_destroy(runtime) == KK_OK);
}

/*
 * 测试 runtime 和 string 分配失败路径。
 * Tests runtime and string allocation failure paths.
 */
static void test_out_of_memory(void) {
    kk_runtime *runtime = NULL;
    kk_string *text = NULL;

    fail_calloc_once();
    assert(kk_runtime_create(&runtime) == KK_ERR_OOM);
    assert(runtime == NULL);

    assert(kk_runtime_create(&runtime) == KK_OK);
    fail_calloc_once();
    assert(kk_string_new(runtime, "x", &text) == KK_ERR_OOM);
    assert(text == NULL);

    fail_malloc_once();
    assert(kk_string_new(runtime, "x", &text) == KK_ERR_OOM);
    assert(text == NULL);
    assert(kk_runtime_destroy(runtime) == KK_OK);
}

/*
 * 测试字符串创建、读取、释放和跨 runtime 拒绝规则。
 * Tests string creation, reading, release, and cross-runtime rejection rules.
 */
static void test_string_lifetime(void) {
    kk_runtime *runtime = NULL;
    kk_runtime *other_runtime = NULL;
    kk_string *empty = NULL;
    kk_string *text = NULL;
    size_t size = 999;
    const char *data = NULL;

    assert(kk_runtime_create(&runtime) == KK_OK);
    assert(kk_runtime_create(&other_runtime) == KK_OK);
    assert(kk_string_new(NULL, "x", &text) == KK_ERR_INVALID_ARGUMENT);
    assert(kk_string_new(runtime, NULL, &text) == KK_ERR_INVALID_ARGUMENT);
    assert(kk_string_new(runtime, "x", NULL) == KK_ERR_INVALID_ARGUMENT);

    assert(kk_string_new(runtime, "", &empty) == KK_OK);
    assert(kk_string_size(NULL, &size) == KK_ERR_INVALID_ARGUMENT);
    assert(kk_string_size(empty, NULL) == KK_ERR_INVALID_ARGUMENT);
    assert(kk_string_size(empty, &size) == KK_OK);
    assert(size == 0);
    assert(kk_string_data(NULL, &data) == KK_ERR_INVALID_ARGUMENT);
    assert(kk_string_data(empty, NULL) == KK_ERR_INVALID_ARGUMENT);
    assert(kk_string_data(empty, &data) == KK_OK);
    assert(strcmp(data, "") == 0);
    assert(kk_string_release(NULL, empty) == KK_ERR_INVALID_ARGUMENT);
    assert(kk_string_release(runtime, empty) == KK_OK);

    assert(kk_string_new(runtime, "hello", &text) == KK_OK);
    assert(kk_string_size(text, &size) == KK_OK);
    assert(size == 5);
    assert(kk_string_data(text, &data) == KK_OK);
    assert(strcmp(data, "hello") == 0);
    assert(kk_string_release(runtime, NULL) == KK_ERR_INVALID_ARGUMENT);
    assert(kk_string_release(other_runtime, text) == KK_ERR_INVALID_ARGUMENT);
    assert(kk_string_release(runtime, text) == KK_OK);
    assert(kk_runtime_destroy(other_runtime) == KK_OK);
    assert(kk_string_new(runtime, "owned by runtime", &text) == KK_OK);
    assert(kk_runtime_destroy(runtime) == KK_OK);
}

/*
 * 测试从 runtime 字符串链表中间释放节点时前后链接正确更新。
 * Tests that releasing a middle node from the runtime string list updates neighboring links correctly.
 */
static void test_string_release_from_middle_of_runtime_list(void) {
    kk_runtime *runtime = NULL;
    kk_string *first = NULL;
    kk_string *second = NULL;
    kk_string *third = NULL;

    assert(kk_runtime_create(&runtime) == KK_OK);
    assert(kk_string_new(runtime, "first", &first) == KK_OK);
    assert(kk_string_new(runtime, "second", &second) == KK_OK);
    assert(kk_string_new(runtime, "third", &third) == KK_OK);

    assert(kk_string_release(runtime, second) == KK_OK);
    assert(kk_string_release(runtime, third) == KK_OK);
    assert(kk_string_release(runtime, first) == KK_OK);
    assert(kk_runtime_destroy(runtime) == KK_OK);
}

/*
 * 测试所有当前 runtime value tag 构造函数。
 * Tests every current runtime value tag constructor.
 */
static void test_value_tags(void) {
    kk_value unit = kk_value_unit();
    kk_value boolean = kk_value_bool(true);
    kk_value integer = kk_value_int64(INT64_C(-42));
    kk_value string = kk_value_string((kk_string *)0x1);
    kk_value object_ref = kk_value_object_ref((void *)0x2);

    assert(unit.tag == KK_VALUE_UNIT);
    assert(boolean.tag == KK_VALUE_BOOL);
    assert(boolean.as.boolean == true);
    assert(integer.tag == KK_VALUE_INT64);
    assert(integer.as.int64 == INT64_C(-42));
    assert(string.tag == KK_VALUE_STRING);
    assert(string.as.string == (kk_string *)0x1);
    assert(object_ref.tag == KK_VALUE_OBJECT_REF);
    assert(object_ref.as.object_ref == (void *)0x2);
}

/*
 * 运行 C runtime 的最小测试套件。
 * Runs the minimal C runtime test suite.
 */
int main(void) {
    test_runtime_lifetime();
    test_out_of_memory();
    test_string_lifetime();
    test_string_release_from_middle_of_runtime_list();
    test_value_tags();
    return 0;
}
