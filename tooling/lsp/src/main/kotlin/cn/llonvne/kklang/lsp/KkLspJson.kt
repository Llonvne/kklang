package cn.llonvne.kklang.lsp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * LSP JSON 工具，集中配置 JSON parser。
 * LSP JSON utilities that centralize JSON parser configuration.
 */
object KkLspJson {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    /**
     * 将字符串解析为 JSON object。
     * Parses a string into a JSON object.
     */
    fun parseObject(text: String): JsonObject =
        json.parseToJsonElement(text).jsonObject

    /**
     * 将 JSON element 编码为字符串。
     * Encodes a JSON element into a string.
     */
    fun encode(element: JsonElement): String =
        element.toString()
}

/**
 * 读取 JSON primitive 的字符串内容。
 * Reads string content from a JSON primitive.
 */
fun JsonElement.jsonPrimitiveContent(): String =
    jsonPrimitive.content

/**
 * 创建 JSON string primitive。
 * Creates a JSON string primitive.
 */
fun jsonString(value: String): JsonPrimitive =
    JsonPrimitive(value)
