package cn.llonvne.kklang.frontend

/**
 * 面向人的源码位置，line 和 column 从一开始，offset 从零开始。
 * Human-facing source position with one-based line and column plus zero-based offset.
 */
data class SourcePosition(
    val line: Int,
    val column: Int,
    val offset: Int,
) {
    init {
        require(line >= 1) { "line must be one-based" }
        require(column >= 1) { "column must be one-based" }
        require(offset >= 0) { "offset must be zero or greater" }
    }
}

/**
 * 一份带名字的源码文本，负责源码切片和 offset 到位置的转换。
 * Named source text responsible for slicing source and converting offsets to positions.
 */
class SourceText private constructor(
    val name: String,
    val content: String,
) {
    val length: Int
        get() = content.length

    /**
     * 将 UTF-16 offset 转换为一基 line/column 位置。
     * Converts a UTF-16 offset into a one-based line and column position.
     */
    fun positionAt(offset: Int): SourcePosition {
        require(offset in 0..content.length) { "offset must be inside the source text" }

        var line = 1
        var column = 1
        var cursor = 0
        while (cursor < offset) {
            if (content[cursor] == '\n') {
                line += 1
                column = 1
            } else {
                column += 1
            }
            cursor += 1
        }

        return SourcePosition(line = line, column = column, offset = offset)
    }

    /**
     * 返回半开 offset 区间内的源码文本。
     * Returns source text inside the half-open offset range.
     */
    fun slice(startOffset: Int, endOffset: Int): String {
        validateRange(startOffset, endOffset)
        return content.substring(startOffset, endOffset)
    }

    /**
     * 校验半开 offset 区间是否落在源码文本内。
     * Validates that a half-open offset range is inside the source text.
     */
    private fun validateRange(startOffset: Int, endOffset: Int) {
        require(startOffset >= 0) { "startOffset must be zero or greater" }
        require(endOffset >= startOffset) { "endOffset must be greater than or equal to startOffset" }
        require(endOffset <= content.length) { "endOffset must be inside the source text" }
    }

    /**
     * SourceText 的工厂入口，集中执行实例不变量校验。
     * Factory entry point for SourceText that centralizes instance invariant checks.
     */
    companion object {
        /**
         * 创建一份带非空名字的源码文本。
         * Creates a source text with a non-blank name.
         */
        fun of(name: String, content: String): SourceText {
            require(name.isNotBlank()) { "source name must not be blank" }
            return SourceText(name = name, content = content)
        }
    }
}

/**
 * 源码中的半开 span，startOffset 包含，endOffset 不包含。
 * Half-open span in source text where startOffset is included and endOffset is excluded.
 */
data class SourceSpan(
    val sourceName: String,
    val startOffset: Int,
    val endOffset: Int,
) {
    init {
        require(sourceName.isNotBlank()) { "source name must not be blank" }
        require(startOffset >= 0) { "startOffset must be zero or greater" }
        require(endOffset >= startOffset) { "endOffset must be greater than or equal to startOffset" }
    }

    /**
     * 从所属源码中取出该 span 覆盖的文本。
     * Extracts the text covered by this span from its owning source.
     */
    fun textIn(source: SourceText): String {
        requireSameSource(source)
        return source.slice(startOffset, endOffset)
    }

    /**
     * 返回 span 起点对应的一基源码位置。
     * Returns the one-based source position for the span start.
     */
    fun startPosition(source: SourceText): SourcePosition {
        requireSameSource(source)
        return source.positionAt(startOffset)
    }

    /**
     * 返回 span 终点对应的一基源码位置。
     * Returns the one-based source position for the span end.
     */
    fun endPosition(source: SourceText): SourcePosition {
        requireSameSource(source)
        return source.positionAt(endOffset)
    }

    /**
     * 返回覆盖当前 span 和另一个同源 span 的最小 span。
     * Returns the smallest span covering this span and another span from the same source.
     */
    fun covering(other: SourceSpan): SourceSpan {
        require(sourceName == other.sourceName) { "cannot cover spans from different sources" }
        return SourceSpan(
            sourceName = sourceName,
            startOffset = minOf(startOffset, other.startOffset),
            endOffset = maxOf(endOffset, other.endOffset),
        )
    }

    /**
     * 校验传入源码与 span 的 sourceName 一致。
     * Validates that the given source matches this span's sourceName.
     */
    private fun requireSameSource(source: SourceText) {
        require(source.name == sourceName) { "span belongs to a different source" }
    }
}
