package cn.llonvne.kklang.frontend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 覆盖 SourceText、SourcePosition 和 SourceSpan 的位置/范围规则。
 * Covers SourceText, SourcePosition, and SourceSpan position and range rules.
 */
class SourceTextTest {
    /**
     * 验证 line/column 从一开始而 offset 从零开始。
     * Verifies that line and column are one-based while offset is zero-based.
     */
    @Test
    fun `source positions are one based while offsets are zero based`() {
        val source = SourceText.of("sample.kk", "ab\nc")

        assertEquals(SourcePosition(line = 1, column = 1, offset = 0), source.positionAt(0))
        assertEquals(SourcePosition(line = 1, column = 3, offset = 2), source.positionAt(2))
        assertEquals(SourcePosition(line = 2, column = 1, offset = 3), source.positionAt(3))
        assertEquals(SourcePosition(line = 2, column = 2, offset = 4), source.positionAt(4))
    }

    /**
     * 验证源码名字、位置和切片范围的非法输入会被拒绝。
     * Verifies that invalid source names, positions, and slice ranges are rejected.
     */
    @Test
    fun `source validates names and offsets`() {
        assertFailsWith<IllegalArgumentException> { SourceText.of("", "x") }
        assertFailsWith<IllegalArgumentException> { SourcePosition(line = 0, column = 1, offset = 0) }
        assertFailsWith<IllegalArgumentException> { SourcePosition(line = 1, column = 0, offset = 0) }
        assertFailsWith<IllegalArgumentException> { SourcePosition(line = 1, column = 1, offset = -1) }

        val source = SourceText.of("sample.kk", "x")
        assertFailsWith<IllegalArgumentException> { source.positionAt(-1) }
        assertFailsWith<IllegalArgumentException> { source.positionAt(2) }
        assertFailsWith<IllegalArgumentException> { source.slice(-1, 1) }
        assertFailsWith<IllegalArgumentException> { source.slice(1, 0) }
        assertFailsWith<IllegalArgumentException> { source.slice(0, 2) }
    }

    /**
     * 验证 span 文本和位置只能用于同名源码。
     * Verifies that span text and positions can only be used with the source of the same name.
     */
    @Test
    fun `span slices and positions are tied to the same source`() {
        val source = SourceText.of("sample.kk", "alpha")
        val other = SourceText.of("other.kk", "alpha")
        val span = SourceSpan(sourceName = "sample.kk", startOffset = 1, endOffset = 4)

        assertEquals("lph", span.textIn(source))
        assertEquals(SourcePosition(line = 1, column = 2, offset = 1), span.startPosition(source))
        assertEquals(SourcePosition(line = 1, column = 5, offset = 4), span.endPosition(source))
        assertFailsWith<IllegalArgumentException> { span.textIn(other) }
        assertFailsWith<IllegalArgumentException> {
            SourceSpan(sourceName = "", startOffset = 0, endOffset = 1)
        }
        assertFailsWith<IllegalArgumentException> {
            SourceSpan(sourceName = "sample.kk", startOffset = -1, endOffset = 1)
        }
        assertFailsWith<IllegalArgumentException> {
            SourceSpan(sourceName = "sample.kk", startOffset = 2, endOffset = 1)
        }
    }

    /**
     * 验证 span covering 只接受同源 span。
     * Verifies that span covering only accepts spans from the same source.
     */
    @Test
    fun `covering spans require the same source`() {
        val left = SourceSpan(sourceName = "sample.kk", startOffset = 1, endOffset = 3)
        val right = SourceSpan(sourceName = "sample.kk", startOffset = 3, endOffset = 5)
        val other = SourceSpan(sourceName = "other.kk", startOffset = 3, endOffset = 5)

        assertEquals(SourceSpan(sourceName = "sample.kk", startOffset = 1, endOffset = 5), left.covering(right))
        assertFailsWith<IllegalArgumentException> { left.covering(other) }
    }
}
