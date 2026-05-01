package cn.llonvne.kklang.lsp

import cn.llonvne.kklang.compiler.CompilationInput
import cn.llonvne.kklang.compiler.CompilationResult
import cn.llonvne.kklang.compiler.CompilerPipeline
import cn.llonvne.kklang.frontend.SourceSpan
import cn.llonvne.kklang.frontend.SourceText
import cn.llonvne.kklang.frontend.diagnostics.Diagnostic
import cn.llonvne.kklang.tooling.highlighting.KkSyntaxClassifier
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * `kklang` 的最小 LSP server，提供同步、diagnostics 和 semantic tokens。
 * Minimal `kklang` LSP server providing synchronization, diagnostics, and semantic tokens.
 */
class KkLspServer(
    private val compilerPipeline: CompilerPipeline = CompilerPipeline(),
    private val syntaxClassifier: KkSyntaxClassifier = KkSyntaxClassifier(),
) {
    private val documents = mutableMapOf<String, String>()

    /**
     * 处理一条 JSON-RPC 消息并返回需要写出的响应或 notification。
     * Handles one JSON-RPC message and returns responses or notifications to write.
     */
    fun handle(message: JsonObject): List<JsonObject> {
        val method = message["method"]?.jsonPrimitiveContent()
        return when (method) {
            "initialize" -> listOf(response(message["id"], initializeResult()))
            "shutdown" -> listOf(response(message["id"], JsonNull))
            "initialized",
            "exit",
            -> emptyList()
            "textDocument/didOpen" -> listOf(handleDidOpen(message))
            "textDocument/didChange" -> listOf(handleDidChange(message))
            "textDocument/semanticTokens/full" -> listOf(response(message["id"], semanticTokens(message)))
            else -> unknownMethod(message["id"])
        }
    }

    /**
     * 构造 initialize result。
     * Builds the initialize result.
     */
    private fun initializeResult(): JsonObject =
        obj(
            "capabilities" to obj(
                "textDocumentSync" to JsonPrimitive(1),
                "semanticTokensProvider" to obj(
                    "legend" to obj(
                        "tokenTypes" to JsonArray(KkLspSemanticTokens.tokenTypes.map(::jsonString)),
                        "tokenModifiers" to JsonArray(emptyList<JsonElement>()),
                    ),
                    "full" to JsonPrimitive(true),
                ),
            ),
        )

    /**
     * 处理 didOpen 并发布 diagnostics。
     * Handles didOpen and publishes diagnostics.
     */
    private fun handleDidOpen(message: JsonObject): JsonObject {
        val textDocument = message["params"]!!.jsonObject["textDocument"]!!.jsonObject
        val uri = textDocument["uri"]!!.jsonPrimitiveContent()
        val text = textDocument["text"]!!.jsonPrimitiveContent()
        documents[uri] = text
        return publishDiagnostics(uri, text)
    }

    /**
     * 处理 didChange 并发布 diagnostics。
     * Handles didChange and publishes diagnostics.
     */
    private fun handleDidChange(message: JsonObject): JsonObject {
        val params = message["params"]!!.jsonObject
        val uri = params["textDocument"]!!.jsonObject["uri"]!!.jsonPrimitiveContent()
        val text = params["contentChanges"]!!.jsonArray.first().jsonObject["text"]!!.jsonPrimitiveContent()
        documents[uri] = text
        return publishDiagnostics(uri, text)
    }

    /**
     * 编译文档并构造 publishDiagnostics notification。
     * Compiles a document and builds a publishDiagnostics notification.
     */
    private fun publishDiagnostics(uri: String, text: String): JsonObject {
        val source = SourceText.of(uri, text)
        val diagnostics = when (val result = compilerPipeline.compile(CompilationInput(source))) {
            is CompilationResult.Success -> emptyList<JsonElement>()
            is CompilationResult.Failure -> result.diagnostics.map { diagnosticToJson(source, it) }
        }
        return notification(
            "textDocument/publishDiagnostics",
            obj(
                "uri" to jsonString(uri),
                "diagnostics" to JsonArray(diagnostics),
            ),
        )
    }

    /**
     * 将 compiler diagnostic 转换为 LSP diagnostic。
     * Converts a compiler diagnostic to an LSP diagnostic.
     */
    private fun diagnosticToJson(source: SourceText, diagnostic: Diagnostic): JsonObject =
        obj(
            "range" to rangeToJson(source, diagnostic.span),
            "severity" to JsonPrimitive(1),
            "code" to jsonString(diagnostic.code),
            "source" to jsonString("kklang"),
            "message" to jsonString(diagnostic.message),
        )

    /**
     * 将 compiler span 转换为 LSP range。
     * Converts a compiler span to an LSP range.
     */
    private fun rangeToJson(source: SourceText, span: SourceSpan): JsonObject {
        val start = span.startPosition(source)
        val end = span.endPosition(source)
        return obj(
            "start" to obj(
                "line" to JsonPrimitive(start.line - 1),
                "character" to JsonPrimitive(start.column - 1),
            ),
            "end" to obj(
                "line" to JsonPrimitive(end.line - 1),
                "character" to JsonPrimitive(end.column - 1),
            ),
        )
    }

    /**
     * 构造 semanticTokens/full result。
     * Builds a semanticTokens/full result.
     */
    private fun semanticTokens(message: JsonObject): JsonObject {
        val uri = message["params"]!!.jsonObject["textDocument"]!!.jsonObject["uri"]!!.jsonPrimitiveContent()
        val text = documents[uri] ?: return obj("data" to JsonArray(emptyList<JsonElement>()))
        val source = SourceText.of(uri, text)
        return obj("data" to KkLspSemanticTokens.encode(source, syntaxClassifier.classify(source)))
    }

    /**
     * 构造 method-not-found 响应或忽略未知 notification。
     * Builds a method-not-found response or ignores an unknown notification.
     */
    private fun unknownMethod(id: JsonElement?): List<JsonObject> =
        if (id == null) {
            emptyList()
        } else {
            listOf(
                obj(
                    "jsonrpc" to jsonString("2.0"),
                    "id" to id,
                    "error" to obj(
                        "code" to JsonPrimitive(-32601),
                        "message" to jsonString("method not found"),
                    ),
                ),
            )
        }

    /**
     * 构造 JSON-RPC response。
     * Builds a JSON-RPC response.
     */
    private fun response(id: JsonElement?, result: JsonElement): JsonObject =
        obj(
            "jsonrpc" to jsonString("2.0"),
            "id" to (id ?: JsonNull),
            "result" to result,
        )

    /**
     * 构造 JSON-RPC notification。
     * Builds a JSON-RPC notification.
     */
    private fun notification(method: String, params: JsonObject): JsonObject =
        obj(
            "jsonrpc" to jsonString("2.0"),
            "method" to jsonString(method),
            "params" to params,
        )

}

/**
 * 构造 JSON object。
 * Builds a JSON object.
 */
fun obj(vararg entries: Pair<String, JsonElement>): JsonObject =
    JsonObject(mapOf(*entries))
