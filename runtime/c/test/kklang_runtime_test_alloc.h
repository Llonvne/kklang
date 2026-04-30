#ifndef KKLANG_RUNTIME_TEST_ALLOC_H
#define KKLANG_RUNTIME_TEST_ALLOC_H

#include <stddef.h>

void *kk_test_calloc(size_t count, size_t size);
void *kk_test_malloc(size_t size);

#endif
