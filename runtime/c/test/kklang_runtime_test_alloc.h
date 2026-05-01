#ifndef KKLANG_RUNTIME_TEST_ALLOC_H
#define KKLANG_RUNTIME_TEST_ALLOC_H

#include <stddef.h>

/*
 * 测试用 calloc hook，支持注入下一次 calloc 失败。
 * Test calloc hook that supports injecting a failure for the next calloc call.
 */
void *kk_test_calloc(size_t count, size_t size);

/*
 * 测试用 malloc hook，支持注入下一次 malloc 失败。
 * Test malloc hook that supports injecting a failure for the next malloc call.
 */
void *kk_test_malloc(size_t size);

#endif
