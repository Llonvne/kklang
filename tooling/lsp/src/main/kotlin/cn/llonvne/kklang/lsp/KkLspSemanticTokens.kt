package cn.llonvne.kklang.lsp

import cn.llonvne.kklang.frontend.SourceText
import cn.llonvne.kklang.tooling.highlighting.KkHighlightToken
import cn.llonvne.kklang.tooling.highlighting.KkHighlightTokenCategory
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * LSP semantic token legend 和相对编码工具。
 * LSP semantic-token legend and relative encoding utilities.
 */
internal object KkLspSemanticTokens {
    val tokenTypes: List<String> = listOf("keyword", "variable", "number", "string", "operator", "delimiter", "unknown")

    /**
     * 将共享高亮 token 编码为 LSP semantic token data。
     * Encodes shared highlighting tokens into LSP semantic-token data.
     */
    fun encode(source: SourceText, tokens: List<KkHighlightToken>): JsonArray {
        val encoded = mutableListOf<JsonElement>()
        var previous = SemanticCursor(line = 0, character = 0)
        for (token in tokens) {
            val typeIndex = typeIndex(token.category) ?: continue
            val start = source.positionAt(token.startOffset)
            val current = SemanticCursor(line = start.line - 1, character = start.column - 1)
            encoded += JsonPrimitive(current.line - previous.line)
            encoded += JsonPrimitive(if (current.line == previous.line) current.character - previous.character else current.character)
            encoded += JsonPrimitive(token.endOffset - token.startOffset)
            encoded += JsonPrimitive(typeIndex)
            encoded += JsonPrimitive(0)
            previous = current
        }
        return JsonArray(encoded)
    }

    /**
     * 将共享高亮分类映射为 LSP token type index。
     * Maps a shared highlight category to an LSP token type index.
     */
    fun typeIndex(category: KkHighlightTokenCategory): Int? =
        when (category) {
            KkHighlightTokenCategory.Keyword -> 0
            KkHighlightTokenCategory.Identifier -> 1
            KkHighlightTokenCategory.Integer -> 2
            KkHighlightTokenCategory.String -> 3
            KkHighlightTokenCategory.Operator -> 4
            KkHighlightTokenCategory.Delimiter -> 5
            KkHighlightTokenCategory.Unknown -> 6
            KkHighlightTokenCategory.Whitespace,
            KkHighlightTokenCategory.EndOfFile,
            -> null
        }

    /**
     * 记录 semantic token 相对编码的前一个位置。
     * Records the previous position used by relative semantic-token encoding.
     */
    private data class SemanticCursor(val line: Int, val character: Int)
}
