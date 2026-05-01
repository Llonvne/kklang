package cn.llonvne.kklang.frontend.diagnostics

import cn.llonvne.kklang.frontend.SourceSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 覆盖 Diagnostic 的基础校验规则。
 * Covers basic validation rules for Diagnostic.
 */
class DiagnosticTest {
    /**
     * 验证 diagnostic code 和 message 不能为空。
     * Verifies that diagnostic code and message cannot be blank.
     */
    @Test
    fun `diagnostics validate code and message`() {
        val span = SourceSpan("sample.kk", 0, 1)
        val diagnostic = Diagnostic(code = "T001", message = "test diagnostic", span = span)

        assertEquals("T001", diagnostic.code)
        assertFailsWith<IllegalArgumentException> { Diagnostic(code = "", message = "test diagnostic", span = span) }
        assertFailsWith<IllegalArgumentException> { Diagnostic(code = "T001", message = "", span = span) }
    }
}
