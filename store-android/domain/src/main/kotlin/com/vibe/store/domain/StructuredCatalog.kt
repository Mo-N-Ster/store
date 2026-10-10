package com.vibe.store.domain

import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Collections

/** Pure, unauthenticated STORE_DATA_V1 catalog payload. Parsing does not authorize import. */
class StructuredCatalogPayload(products: List<StructuredCatalogProduct>) {
    val products: List<StructuredCatalogProduct> = Collections.unmodifiableList(ArrayList(products))
    init { require(products.size <= StoreDataV1Bounds.MAX_PRODUCTS) }
}

class StructuredCatalogProduct(
    val name: String,
    val hashtag: String,
    val category: String,
    val description: String,
    val price: BigDecimal,
    val stockQuantity: Long,
    val minStockThreshold: Long,
)

enum class CatalogImportValidationStage { STRUCTURE_ONLY }
class CatalogImportPreview(
    val payload: StructuredCatalogPayload,
    validationErrors: List<CatalogImportValidationError> = emptyList(),
    val validationStage: CatalogImportValidationStage = CatalogImportValidationStage.STRUCTURE_ONLY,
    val authenticityVerified: Boolean = false,
) {
    val validationErrors: List<CatalogImportValidationError> =
        Collections.unmodifiableList(ArrayList(validationErrors))
    init { require(!authenticityVerified) { "STORE_DATA_V1 has no authenticity proof" } }
}

class CatalogImportValidationError(val field: String, val code: String)
enum class CatalogCodecError {
    INVALID_PREFIX, INVALID_BASE64, INPUT_TOO_LARGE, INVALID_UTF8, INVALID_JSON,
    DUPLICATE_FIELD, UNKNOWN_FIELD, UNSUPPORTED_VERSION, INVALID_STRUCTURE,
}
class CatalogCodecException(val code: CatalogCodecError) : IllegalArgumentException(code.name)

/** These are provisional parser resource caps derived from SP-02, not business limits. */
object StoreDataV1Bounds {
    const val MAX_PAYLOAD_BYTES: Int = 2_000_000
    const val MAX_ENCODED_CHARS: Int = 2_666_668
    const val MAX_PRODUCTS: Int = 10_000
    const val MAX_STRING_CHARS: Int = 64_000
    const val MAX_NUMBER_CHARS: Int = 256
    const val SUBJECT_PREFIX: String = "STORE_DATA_V1:"
}

/** Deterministic structural codec. Valid structure is still untrusted business input. */
object StoreDataV1Codec {
    private const val BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private val rootKeys = setOf("kind", "version", "products")
    private val productKeys = listOf("name", "hashtag", "category", "description", "price", "stockQuantity", "minStockThreshold")

    fun encode(payload: StructuredCatalogPayload): String {
        if (payload.products.size > StoreDataV1Bounds.MAX_PRODUCTS) fail(CatalogCodecError.INPUT_TOO_LARGE)
        val out = StringBuilder()
        out.append("{\"kind\":\"stocks\",\"version\":1,\"products\":[")
        payload.products.forEachIndexed { index, product ->
            if (index > 0) out.append(',')
            out.append('{')
            appendStringField(out, "name", product.name, separator = false)
            appendStringField(out, "hashtag", product.hashtag)
            appendStringField(out, "category", product.category)
            appendStringField(out, "description", product.description)
            val priceLength = plainDecimalLength(product.price)
            if (priceLength > StoreDataV1Bounds.MAX_NUMBER_CHARS) fail(CatalogCodecError.INPUT_TOO_LARGE)
            out.append(",\"price\":").append(product.price.toPlainString())
            out.append(",\"stockQuantity\":").append(product.stockQuantity)
            out.append(",\"minStockThreshold\":").append(product.minStockThreshold)
            out.append('}')
            if (out.length > StoreDataV1Bounds.MAX_PAYLOAD_BYTES) fail(CatalogCodecError.INPUT_TOO_LARGE)
        }
        out.append("]}")
        val bytes = strictUtf8(out.toString())
        if (bytes.size > StoreDataV1Bounds.MAX_PAYLOAD_BYTES) fail(CatalogCodecError.INPUT_TOO_LARGE)
        return StoreDataV1Bounds.SUBJECT_PREFIX + encodeBase64(bytes)
    }

    fun decode(subject: String): CatalogImportPreview {
        if (subject.length > StoreDataV1Bounds.SUBJECT_PREFIX.length + StoreDataV1Bounds.MAX_ENCODED_CHARS) {
            fail(CatalogCodecError.INPUT_TOO_LARGE)
        }
        if (!subject.startsWith(StoreDataV1Bounds.SUBJECT_PREFIX)) fail(CatalogCodecError.INVALID_PREFIX)
        val encoded = subject.substring(StoreDataV1Bounds.SUBJECT_PREFIX.length)
        if (encoded.length > StoreDataV1Bounds.MAX_ENCODED_CHARS) fail(CatalogCodecError.INPUT_TOO_LARGE)
        if (encoded.isEmpty() || encoded.any { it !in 'A'..'Z' && it !in 'a'..'z' && it !in '0'..'9' && it != '+' && it != '/' && it != '=' }) {
            fail(CatalogCodecError.INVALID_BASE64)
        }
        val bytes = decodeBase64(encoded)
        if (bytes.size > StoreDataV1Bounds.MAX_PAYLOAD_BYTES) fail(CatalogCodecError.INPUT_TOO_LARGE)
        if (encodeBase64(bytes) != encoded) fail(CatalogCodecError.INVALID_BASE64)
        val json = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) { fail(CatalogCodecError.INVALID_UTF8) }
        val payload = try { JsonParser(json).parsePayload() } catch (e: CatalogCodecException) { throw e }
        return CatalogImportPreview(payload)
    }

    private fun appendStringField(out: StringBuilder, key: String, value: String, separator: Boolean = true) {
        if (value.length > StoreDataV1Bounds.MAX_STRING_CHARS) fail(CatalogCodecError.INPUT_TOO_LARGE)
        if (separator) out.append(',')
        out.append(quote(key)).append(':').append(quote(value))
    }

    private fun quote(value: String): String {
        val out = StringBuilder(value.length + 2).append('"')
        value.forEach { c ->
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                in '\u0000'..'\u001F' -> out.append("\\u%04x".format(c.code))
                else -> out.append(c)
            }
        }
        return out.append('"').toString()
    }

    private fun strictUtf8(text: String): ByteArray = try {
        val buffer = StandardCharsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(java.nio.CharBuffer.wrap(text))
        ByteArray(buffer.remaining()).also { buffer.get(it) }
    } catch (_: Exception) { fail(CatalogCodecError.INVALID_UTF8) }

    private fun plainDecimalLength(value: BigDecimal): Long {
        val precision = value.precision().toLong()
        val scale = value.scale().toLong()
        val body = if (scale <= 0) precision - scale else maxOf(precision, scale + 1) + 1
        return body + if (value.signum() < 0) 1 else 0
    }

    /** Platform-neutral RFC 4648 codec; java.util.Base64 is unavailable on Android API 23–25. */
    private fun encodeBase64(bytes: ByteArray): String {
        val output = StringBuilder(((bytes.size + 2L) / 3L * 4L).toInt())
        var index = 0
        while (index < bytes.size) {
            val remaining = bytes.size - index
            val first = bytes[index].toInt() and 0xff
            val second = if (remaining > 1) bytes[index + 1].toInt() and 0xff else 0
            val third = if (remaining > 2) bytes[index + 2].toInt() and 0xff else 0
            output.append(BASE64_ALPHABET[first ushr 2])
            output.append(BASE64_ALPHABET[((first and 0x03) shl 4) or (second ushr 4)])
            output.append(if (remaining > 1) BASE64_ALPHABET[((second and 0x0f) shl 2) or (third ushr 6)] else '=')
            output.append(if (remaining > 2) BASE64_ALPHABET[third and 0x3f] else '=')
            index += 3
        }
        return output.toString()
    }

    private fun decodeBase64(encoded: String): ByteArray {
        if (encoded.isEmpty() || encoded.length % 4 != 0) fail(CatalogCodecError.INVALID_BASE64)
        val padding = when {
            encoded.endsWith("==") -> 2
            encoded.endsWith("=") -> 1
            else -> 0
        }
        val output = ByteArray(encoded.length / 4 * 3 - padding)
        var outputIndex = 0
        var index = 0
        while (index < encoded.length) {
            val last = index + 4 == encoded.length
            val c0 = base64Value(encoded[index])
            val c1 = base64Value(encoded[index + 1])
            val c2 = if (encoded[index + 2] == '=' && last) -2 else base64Value(encoded[index + 2])
            val c3 = if (encoded[index + 3] == '=' && last) -2 else base64Value(encoded[index + 3])
            if (c0 < 0 || c1 < 0 || c2 == -1 || c3 == -1 || c2 == -2 && c3 != -2) {
                fail(CatalogCodecError.INVALID_BASE64)
            }
            if (!last && (c2 < 0 || c3 < 0)) fail(CatalogCodecError.INVALID_BASE64)
            if (c2 == -2 && (c1 and 0x0f) != 0) fail(CatalogCodecError.INVALID_BASE64)
            if (c3 == -2 && c2 >= 0 && (c2 and 0x03) != 0) fail(CatalogCodecError.INVALID_BASE64)
            val triple = (c0 shl 18) or (c1 shl 12) or ((if (c2 < 0) 0 else c2) shl 6) or (if (c3 < 0) 0 else c3)
            if (outputIndex < output.size) output[outputIndex++] = (triple ushr 16).toByte()
            if (outputIndex < output.size) output[outputIndex++] = (triple ushr 8).toByte()
            if (outputIndex < output.size) output[outputIndex++] = triple.toByte()
            index += 4
        }
        return output
    }

    private fun base64Value(character: Char): Int = BASE64_ALPHABET.indexOf(character)

    private fun fail(code: CatalogCodecError): Nothing = throw CatalogCodecException(code)

    private class JsonParser(private val input: String) {
        private var at = 0
        fun parsePayload(): StructuredCatalogPayload {
            ws(); expect('{')
            val root = LinkedHashMap<String, Any>()
            ws()
            if (!take('}')) while (true) {
                val key = string()
                // Map.putIfAbsent is a Java 8 API absent from Android API 23.
                if (root.containsKey(key)) fail(CatalogCodecError.DUPLICATE_FIELD)
                root[key] = Unit
                ws(); expect(':'); ws()
                val value: Any = when (key) {
                    "kind" -> string()
                    "version" -> integer()
                    "products" -> products()
                    else -> fail(CatalogCodecError.UNKNOWN_FIELD)
                }
                root[key] = value
                ws(); if (take('}')) break else { expect(','); ws() }
            }
            ws(); if (at != input.length) fail(CatalogCodecError.INVALID_JSON)
            if (root.keys != rootKeys || root["kind"] != "stocks") fail(CatalogCodecError.INVALID_STRUCTURE)
            if (root["version"] != 1L) fail(CatalogCodecError.UNSUPPORTED_VERSION)
            @Suppress("UNCHECKED_CAST")
            return StructuredCatalogPayload(root["products"] as? List<StructuredCatalogProduct>
                ?: fail(CatalogCodecError.INVALID_STRUCTURE))
        }

        private fun products(): List<StructuredCatalogProduct> {
            expect('['); ws()
            val result = ArrayList<StructuredCatalogProduct>()
            if (take(']')) return result
            while (true) {
                if (result.size >= StoreDataV1Bounds.MAX_PRODUCTS) fail(CatalogCodecError.INPUT_TOO_LARGE)
                result += product(); ws()
                if (take(']')) return result
                expect(','); ws()
            }
        }

        private fun product(): StructuredCatalogProduct {
            expect('{'); ws()
            val values = LinkedHashMap<String, Any>()
            if (!take('}')) while (true) {
                val key = string()
                if (values.containsKey(key)) fail(CatalogCodecError.DUPLICATE_FIELD)
                if (key !in productKeys) fail(CatalogCodecError.UNKNOWN_FIELD)
                ws(); expect(':'); ws()
                values[key] = when (key) {
                    "name", "hashtag", "category", "description" -> string()
                    "price" -> decimal()
                    "stockQuantity", "minStockThreshold" -> integer()
                    else -> fail(CatalogCodecError.INVALID_STRUCTURE)
                }
                ws(); if (take('}')) break else { expect(','); ws() }
            }
            if (values.keys != productKeys.toSet()) fail(CatalogCodecError.INVALID_STRUCTURE)
            return StructuredCatalogProduct(
                values.getValue("name") as String,
                values.getValue("hashtag") as String,
                values.getValue("category") as String,
                values.getValue("description") as String,
                values.getValue("price") as BigDecimal,
                values.getValue("stockQuantity") as Long,
                values.getValue("minStockThreshold") as Long,
            )
        }

        private fun string(): String {
            expect('"'); val out = StringBuilder()
            while (at < input.length) {
                val c = input[at++]
                when {
                    c == '"' -> {
                        if (out.length > StoreDataV1Bounds.MAX_STRING_CHARS) fail(CatalogCodecError.INPUT_TOO_LARGE)
                        return out.toString()
                    }
                    c == '\\' -> {
                        if (at >= input.length) fail(CatalogCodecError.INVALID_JSON)
                        when (val escaped = input[at++]) {
                            '"', '\\', '/' -> out.append(escaped)
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                val first = hexChar()
                                if (first.isHighSurrogate()) {
                                    if (at + 1 >= input.length || input[at++] != '\\' || input[at++] != 'u') fail(CatalogCodecError.INVALID_JSON)
                                    val second = hexChar()
                                    if (!second.isLowSurrogate()) fail(CatalogCodecError.INVALID_JSON)
                                    out.append(first).append(second)
                                } else {
                                    if (first.isLowSurrogate()) fail(CatalogCodecError.INVALID_JSON)
                                    out.append(first)
                                }
                            }
                            else -> fail(CatalogCodecError.INVALID_JSON)
                        }
                    }
                    c.code < 0x20 -> fail(CatalogCodecError.INVALID_JSON)
                    c.isHighSurrogate() -> {
                        if (at >= input.length || !input[at].isLowSurrogate()) fail(CatalogCodecError.INVALID_JSON)
                        out.append(c).append(input[at++])
                    }
                    c.isLowSurrogate() -> fail(CatalogCodecError.INVALID_JSON)
                    else -> out.append(c)
                }
                if (out.length > StoreDataV1Bounds.MAX_STRING_CHARS) fail(CatalogCodecError.INPUT_TOO_LARGE)
            }
            fail(CatalogCodecError.INVALID_JSON)
        }

        private fun decimal(): BigDecimal {
            val token = numberToken()
            if (token.contains('e', true)) fail(CatalogCodecError.INVALID_STRUCTURE)
            return try { BigDecimal(token) } catch (_: NumberFormatException) { fail(CatalogCodecError.INVALID_STRUCTURE) }
        }

        private fun integer(): Long {
            val token = numberToken()
            if (token.contains('.') || token.contains('e', true)) fail(CatalogCodecError.INVALID_STRUCTURE)
            return token.toLongOrNull() ?: fail(CatalogCodecError.INVALID_STRUCTURE)
        }

        private fun numberToken(): String {
            val start = at
            take('-')
            if (take('0')) {
                if (at < input.length && input[at].isDigit()) fail(CatalogCodecError.INVALID_JSON)
            } else {
                if (at >= input.length || input[at] !in '1'..'9') fail(CatalogCodecError.INVALID_JSON)
                while (at < input.length && input[at] in '0'..'9') at++
            }
            if (take('.')) {
                if (at >= input.length || input[at] !in '0'..'9') fail(CatalogCodecError.INVALID_JSON)
                while (at < input.length && input[at] in '0'..'9') at++
            }
            if (at < input.length && input[at] in "eE") {
                at++; if (at < input.length && input[at] in "+-") at++
                if (at >= input.length || input[at] !in '0'..'9') fail(CatalogCodecError.INVALID_JSON)
                while (at < input.length && input[at] in '0'..'9') at++
            }
            if (at - start > StoreDataV1Bounds.MAX_NUMBER_CHARS) fail(CatalogCodecError.INPUT_TOO_LARGE)
            return input.substring(start, at)
        }

        private fun hexChar(): Char {
            if (at + 4 > input.length) fail(CatalogCodecError.INVALID_JSON)
            val value = input.substring(at, at + 4).toIntOrNull(16) ?: fail(CatalogCodecError.INVALID_JSON)
            at += 4
            return value.toChar()
        }
        private fun ws() { while (at < input.length && input[at] in " \t\r\n") at++ }
        private fun expect(c: Char) { if (!take(c)) fail(CatalogCodecError.INVALID_JSON) }
        private fun take(c: Char): Boolean = if (at < input.length && input[at] == c) { at++; true } else false
    }
}
