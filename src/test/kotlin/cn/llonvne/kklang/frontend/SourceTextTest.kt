package cn.llonvne.kklang.frontend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SourceTextTest {
    @Test
    fun `source positions are one based while offsets are zero based`() {
        val source = SourceText.of("sample.kk", "ab\nc")

        assertEquals(SourcePosition(line = 1, column = 1, offset = 0), source.positionAt(0))
        assertEquals(SourcePosition(line = 1, column = 3, offset = 2), source.positionAt(2))
        assertEquals(SourcePosition(line = 2, column = 1, offset = 3), source.positionAt(3))
        assertEquals(SourcePosition(line = 2, column = 2, offset = 4), source.positionAt(4))
    }

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

    @Test
    fun `covering spans require the same source`() {
        val left = SourceSpan(sourceName = "sample.kk", startOffset = 1, endOffset = 3)
        val right = SourceSpan(sourceName = "sample.kk", startOffset = 3, endOffset = 5)
        val other = SourceSpan(sourceName = "other.kk", startOffset = 3, endOffset = 5)

        assertEquals(SourceSpan(sourceName = "sample.kk", startOffset = 1, endOffset = 5), left.covering(right))
        assertFailsWith<IllegalArgumentException> { left.covering(other) }
    }
}
