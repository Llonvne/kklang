package cn.llonvne.kklang.frontend

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

class SourceText private constructor(
    val name: String,
    val content: String,
) {
    val length: Int
        get() = content.length

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

    fun slice(startOffset: Int, endOffset: Int): String {
        validateRange(startOffset, endOffset)
        return content.substring(startOffset, endOffset)
    }

    private fun validateRange(startOffset: Int, endOffset: Int) {
        require(startOffset >= 0) { "startOffset must be zero or greater" }
        require(endOffset >= startOffset) { "endOffset must be greater than or equal to startOffset" }
        require(endOffset <= content.length) { "endOffset must be inside the source text" }
    }

    companion object {
        fun of(name: String, content: String): SourceText {
            require(name.isNotBlank()) { "source name must not be blank" }
            return SourceText(name = name, content = content)
        }
    }
}

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

    fun textIn(source: SourceText): String {
        requireSameSource(source)
        return source.slice(startOffset, endOffset)
    }

    fun startPosition(source: SourceText): SourcePosition {
        requireSameSource(source)
        return source.positionAt(startOffset)
    }

    fun endPosition(source: SourceText): SourcePosition {
        requireSameSource(source)
        return source.positionAt(endOffset)
    }

    fun covering(other: SourceSpan): SourceSpan {
        require(sourceName == other.sourceName) { "cannot cover spans from different sources" }
        return SourceSpan(
            sourceName = sourceName,
            startOffset = minOf(startOffset, other.startOffset),
            endOffset = maxOf(endOffset, other.endOffset),
        )
    }

    private fun requireSameSource(source: SourceText) {
        require(source.name == sourceName) { "span belongs to a different source" }
    }
}

