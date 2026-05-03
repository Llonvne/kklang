package cn.llonvne.kklang.lsp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import cn.llonvne.kklang.frontend.SourceText
import cn.llonvne.kklang.tooling.highlighting.KkHighlightToken
import cn.llonvne.kklang.tooling.highlighting.KkHighlightTokenCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 覆盖最小 `kklang` LSP server 行为。
 * Covers the minimal `kklang` LSP server behavior.
 */
class KkLspServerTest {
    /**
     * 验证 initialize 返回文本同步和 semantic token capabilities。
     * Verifies that initialize returns text sync and semantic-token capabilities.
     */
    @Test
    fun `initialize returns minimal capabilities`() {
        val responses = KkLspServer().handle(KkLspJson.parseObject("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}"""))

        val result = responses.single()["result"]!!.jsonObject
        val capabilities = result["capabilities"]!!.jsonObject
        assertEquals(1, capabilities["textDocumentSync"]!!.jsonPrimitive.int)
        assertTrue(capabilities["semanticTokensProvider"]!!.jsonObject.containsKey("legend"))
    }

    /**
     * 验证 didOpen 对合法源码发布空 diagnostics。
     * Verifies that didOpen publishes empty diagnostics for valid source.
     */
    @Test
    fun `did open publishes empty diagnostics for valid source`() {
        val response = KkLspServer().handle(didOpen("file:///sample.kk", "val x = 1; x")).single()

        assertEquals("textDocument/publishDiagnostics", response["method"]!!.jsonPrimitiveContent())
        assertEquals(emptyList(), response["params"]!!.jsonObject["diagnostics"]!!.jsonArray.toList())
    }

    /**
     * 验证 didOpen 会保留编译器 diagnostic code。
     * Verifies that didOpen preserves compiler diagnostic codes.
     */
    @Test
    fun `did open publishes compiler diagnostics`() {
        val response = KkLspServer().handle(didOpen("file:///sample.kk", "@")).single()
        val diagnostics = response["params"]!!.jsonObject["diagnostics"]!!.jsonArray
        val diagnostic = diagnostics.single().jsonObject

        assertEquals("LEX001", diagnostic["code"]!!.jsonPrimitiveContent())
        assertEquals("kklang", diagnostic["source"]!!.jsonPrimitiveContent())
        assertEquals(0, diagnostic["range"]!!.jsonObject["start"]!!.jsonObject["line"]!!.jsonPrimitive.int)
        assertEquals(1, diagnostic["range"]!!.jsonObject["end"]!!.jsonObject["character"]!!.jsonPrimitive.int)
    }

    /**
     * 验证 didChange 更新文档并重新发布 diagnostics。
     * Verifies that didChange updates the document and republishes diagnostics.
     */
    @Test
    fun `did change updates diagnostics`() {
        val server = KkLspServer()
        server.handle(didOpen("file:///sample.kk", "@"))

        val response = server.handle(didChange("file:///sample.kk", "1 + 2")).single()

        assertEquals(emptyList(), response["params"]!!.jsonObject["diagnostics"]!!.jsonArray.toList())
    }

    /**
     * 验证 semantic tokens 使用共享高亮分类。
     * Verifies that semantic tokens use shared highlighting classification.
     */
    @Test
    fun `semantic tokens use shared highlighting categories`() {
        val server = KkLspServer()
        server.handle(didOpen("file:///sample.kk", "val x = (1 + 2); \\\"s\\\"\n@"))

        val response = server.handle(
            KkLspJson.parseObject(
                """{"jsonrpc":"2.0","id":2,"method":"textDocument/semanticTokens/full","params":{"textDocument":{"uri":"file:///sample.kk"}}}""",
            ),
        ).single()
        val data = response["result"]!!.jsonObject["data"]!!.jsonArray.map { it.jsonPrimitive.int }
        val tokenTypes = data.chunked(5).map { it[3] }

        assertEquals(listOf(0, 1, 4, 5, 2, 4, 2, 5, 5, 3, 6), tokenTypes)
        assertTrue(data.chunked(5).any { it[0] == 1 })
    }

    /**
     * 验证未打开文档的 semantic token 请求返回空数据。
     * Verifies that semantic-token requests for unopened documents return empty data.
     */
    @Test
    fun `semantic tokens for unopened document are empty`() {
        val response = KkLspServer().handle(
            KkLspJson.parseObject(
                """{"jsonrpc":"2.0","id":3,"method":"textDocument/semanticTokens/full","params":{"textDocument":{"uri":"file:///missing.kk"}}}""",
            ),
        ).single()

        assertEquals(emptyList(), response["result"]!!.jsonObject["data"]!!.jsonArray.toList())
    }

    /**
     * 验证 shutdown、initialized、exit 和未知方法分支。
     * Verifies shutdown, initialized, exit, and unknown-method branches.
     */
    @Test
    fun `server handles lifecycle and unknown methods`() {
        val server = KkLspServer()

        assertEquals(emptyList(), server.handle(KkLspJson.parseObject("""{"jsonrpc":"2.0","method":"initialized","params":{}}""")))
        assertEquals(emptyList(), server.handle(KkLspJson.parseObject("""{"jsonrpc":"2.0","method":"exit","params":{}}""")))

        val shutdown = server.handle(KkLspJson.parseObject("""{"jsonrpc":"2.0","id":4,"method":"shutdown"}""")).single()
        assertTrue(shutdown.containsKey("result"))

        val shutdownWithoutId = server.handle(KkLspJson.parseObject("""{"jsonrpc":"2.0","method":"shutdown"}""")).single()
        assertTrue(shutdownWithoutId.containsKey("id"))

        val unknownRequest = server.handle(KkLspJson.parseObject("""{"jsonrpc":"2.0","id":5,"method":"unknown"}""")).single()
        assertEquals(-32601, unknownRequest["error"]!!.jsonObject["code"]!!.jsonPrimitive.int)

        val unknownNotification = server.handle(KkLspJson.parseObject("""{"jsonrpc":"2.0","method":"unknown"}"""))
        assertEquals(emptyList(), unknownNotification)

        val missingMethodRequest = server.handle(KkLspJson.parseObject("""{"jsonrpc":"2.0","id":6}""")).single()
        assertEquals(-32601, missingMethodRequest["error"]!!.jsonObject["code"]!!.jsonPrimitive.int)
    }

    /**
     * 验证 semantic token 工具覆盖所有分类和 skip 分支。
     * Verifies that semantic-token utilities cover every category and skip branch.
     */
    @Test
    fun `semantic token utility covers categories and skipped trivia`() {
        assertEquals(
            listOf("keyword", "variable", "number", "string", "operator", "delimiter", "unknown"),
            KkLspSemanticTokens.tokenTypes,
        )
        assertEquals(0, KkLspSemanticTokens.typeIndex(KkHighlightTokenCategory.Keyword))
        assertEquals(1, KkLspSemanticTokens.typeIndex(KkHighlightTokenCategory.Identifier))
        assertEquals(2, KkLspSemanticTokens.typeIndex(KkHighlightTokenCategory.Integer))
        assertEquals(3, KkLspSemanticTokens.typeIndex(KkHighlightTokenCategory.String))
        assertEquals(4, KkLspSemanticTokens.typeIndex(KkHighlightTokenCategory.Operator))
        assertEquals(5, KkLspSemanticTokens.typeIndex(KkHighlightTokenCategory.Delimiter))
        assertEquals(6, KkLspSemanticTokens.typeIndex(KkHighlightTokenCategory.Unknown))
        assertEquals(null, KkLspSemanticTokens.typeIndex(KkHighlightTokenCategory.Whitespace))
        assertEquals(null, KkLspSemanticTokens.typeIndex(KkHighlightTokenCategory.EndOfFile))

        val source = SourceText.of("sample.kk", "x \n y")
        val encoded = KkLspSemanticTokens.encode(
            source,
            listOf(
                KkHighlightToken(KkHighlightTokenCategory.Identifier, "x", 0, 1),
                KkHighlightToken(KkHighlightTokenCategory.Whitespace, " \n ", 1, 4),
                KkHighlightToken(KkHighlightTokenCategory.Identifier, "y", 4, 5),
                KkHighlightToken(KkHighlightTokenCategory.EndOfFile, "", 5, 5),
            ),
        ).map { it.jsonPrimitive.int }

        assertEquals(listOf(0, 0, 1, 1, 0, 1, 1, 1, 1, 0), encoded)
    }

    /**
     * 构造 didOpen notification。
     * Builds a didOpen notification.
     */
    private fun didOpen(uri: String, text: String): JsonObject =
        KkLspJson.parseObject(
            """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$uri","languageId":"kklang","version":1,"text":"$text"}}}""",
        )

    /**
     * 构造 didChange notification。
     * Builds a didChange notification.
     */
    private fun didChange(uri: String, text: String): JsonObject =
        KkLspJson.parseObject(
            """{"jsonrpc":"2.0","method":"textDocument/didChange","params":{"textDocument":{"uri":"$uri","version":2},"contentChanges":[{"text":"$text"}]}}""",
        )
}
