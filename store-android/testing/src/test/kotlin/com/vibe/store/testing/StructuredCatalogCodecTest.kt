package com.vibe.store.testing

import com.vibe.store.domain.CatalogCodecError
import com.vibe.store.domain.CatalogCodecException
import com.vibe.store.domain.CatalogImportValidationStage
import com.vibe.store.domain.StoreDataV1Bounds
import com.vibe.store.domain.StoreDataV1Codec
import com.vibe.store.domain.StructuredCatalogPayload
import com.vibe.store.domain.StructuredCatalogProduct
import com.vibe.store.api.BoundedDocumentInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.util.Base64

class StructuredCatalogCodecTest {
    private val synthetic = StructuredCatalogPayload(listOf(
        StructuredCatalogProduct("Café", "#café", "Épices", "FCFA", BigDecimal("0.12345678901234567890"), 5, 1),
    ))

    @Test fun canonicalRoundTripPreservesExactDecimalUnicodeAndCanonicalOrdering() {
        val encoded = StoreDataV1Codec.encode(synthetic)
        assertTrue(encoded.startsWith(StoreDataV1Bounds.SUBJECT_PREFIX))
        val preview = StoreDataV1Codec.decode(encoded)
        assertEquals("0.12345678901234567890", preview.payload.products.single().price.toPlainString())
        assertEquals("Café", preview.payload.products.single().name)
        assertEquals("FCFA", preview.payload.products.single().description)
        assertFalse(preview.authenticityVerified)
        assertEquals(CatalogImportValidationStage.STRUCTURE_ONLY, preview.validationStage)
        assertEquals(encoded, StoreDataV1Codec.encode(preview.payload))
    }

    @Test fun platformNeutralBase64MatchesRfc4648ForEveryFinalQuantumLength() {
        for (tail in 0..5) {
            val product = StructuredCatalogProduct("n", "", "c", "x".repeat(tail), BigDecimal("1.20"), 0, 0)
            val payload = StructuredCatalogPayload(listOf(product))
            val encoded = StoreDataV1Codec.encode(payload).removePrefix(StoreDataV1Bounds.SUBJECT_PREFIX)
            val json = "{\"kind\":\"stocks\",\"version\":1,\"products\":[{\"name\":\"n\",\"hashtag\":\"\",\"category\":\"c\",\"description\":\"${"x".repeat(tail)}\",\"price\":1.20,\"stockQuantity\":0,\"minStockThreshold\":0}]}"
            assertEquals(Base64.getEncoder().encodeToString(json.toByteArray(StandardCharsets.UTF_8)), encoded)
            assertEquals(payload.products.single().description, StoreDataV1Codec.decode(StoreDataV1Bounds.SUBJECT_PREFIX + encoded).payload.products.single().description)
        }
    }

    @Test fun syntheticStore201PdfSubjectEnvelopeRemainsReadable() {
        val subject = javaClass.getResourceAsStream("/com/vibe/store/testing/synthetic-2.0.1-subject.txt")!!
            .bufferedReader(StandardCharsets.US_ASCII).use { it.readText().trim() }
        val preview = StoreDataV1Codec.decode(subject)
        assertEquals(3, preview.payload.products.size)
        assertEquals("Café torréfié", preview.payload.products[0].name)
        assertEquals(BigDecimal("1250.25"), preview.payload.products[0].price)
        assertEquals(BigDecimal("0.5"), preview.payload.products[1].price)
    }

    @Test fun reorderedLegacyPropertiesAreAcceptedAndReencodedCanonically() {
        val raw = """{"products":[{"description":"d","minStockThreshold":2,"stockQuantity":3,"price":1.2300,"category":"c","hashtag":"h","name":"n"}],"version":1,"kind":"stocks"}"""
        val preview = StoreDataV1Codec.decode(subject(raw))
        assertEquals("1.2300", preview.payload.products.single().price.toPlainString())
        val canonicalJson = String(Base64.getDecoder().decode(StoreDataV1Codec.encode(preview.payload)
            .removePrefix(StoreDataV1Bounds.SUBJECT_PREFIX)), StandardCharsets.UTF_8)
        assertEquals("{\"kind\":\"stocks\",\"version\":1,\"products\":[{\"name\":\"n\",\"hashtag\":\"h\",\"category\":\"c\",\"description\":\"d\",\"price\":1.2300,\"stockQuantity\":3,\"minStockThreshold\":2}]}", canonicalJson)
    }

    @Test fun malformedBase64MalformedUtf8AndTruncatedJsonFailClosed() {
        assertCode(CatalogCodecError.INVALID_BASE64) { StoreDataV1Codec.decode(StoreDataV1Bounds.SUBJECT_PREFIX + "%%%") }
        assertCode(CatalogCodecError.INVALID_UTF8) {
            StoreDataV1Codec.decode(StoreDataV1Bounds.SUBJECT_PREFIX + Base64.getEncoder().encodeToString(byteArrayOf(0xC3.toByte(), 0x28)))
        }
        assertCode(CatalogCodecError.INVALID_JSON) { StoreDataV1Codec.decode(subject("{\"kind\":")) }
    }

    @Test fun unsupportedVersionsUnknownAndDuplicateFieldsAreRejected() {
        assertCode(CatalogCodecError.UNSUPPORTED_VERSION) { StoreDataV1Codec.decode(subject(validJson().replace("\"version\":1", "\"version\":2"))) }
        assertCode(CatalogCodecError.DUPLICATE_FIELD) { StoreDataV1Codec.decode(subject(validJson().replace("\"kind\":\"stocks\"", "\"kind\":\"stocks\",\"kind\":\"stocks\""))) }
        assertCode(CatalogCodecError.DUPLICATE_FIELD) { StoreDataV1Codec.decode(subject(validJson().replace("\"name\":\"n\"", "\"name\":\"n\",\"name\":\"n\""))) }
        assertCode(CatalogCodecError.UNKNOWN_FIELD) { StoreDataV1Codec.decode(subject(validJson().replace("\"kind\":\"stocks\"", "\"other\":0,\"kind\":\"stocks\""))) }
    }

    @Test fun excessivePayloadAndTruncatedBase64AreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            StoreDataV1Codec.decode(StoreDataV1Bounds.SUBJECT_PREFIX + "A".repeat(StoreDataV1Bounds.MAX_ENCODED_CHARS + 1))
        }
        assertCode(CatalogCodecError.INVALID_BASE64) { StoreDataV1Codec.decode(StoreDataV1Bounds.SUBJECT_PREFIX + "YWJjYQ=") }
    }

    @Test fun productAndStringBoundsAreEnforcedWithoutPromotingThemToBusinessValidation() {
        val productJson = "{\"name\":\"n\",\"hashtag\":\"h\",\"category\":\"c\",\"description\":\"d\",\"price\":1,\"stockQuantity\":0,\"minStockThreshold\":0}"
        val tooMany = "{\"kind\":\"stocks\",\"version\":1,\"products\":[" +
            List(StoreDataV1Bounds.MAX_PRODUCTS + 1) { productJson }.joinToString(",") + "]}"
        assertCode(CatalogCodecError.INPUT_TOO_LARGE) { StoreDataV1Codec.decode(subject(tooMany)) }
        val oversized = StructuredCatalogProduct("x".repeat(StoreDataV1Bounds.MAX_STRING_CHARS + 1), "h", "c", "d", BigDecimal.ONE, 0, 0)
        assertCode(CatalogCodecError.INPUT_TOO_LARGE) {
            StoreDataV1Codec.encode(StructuredCatalogPayload(listOf(oversized)))
        }
        val source = byteArrayOf(1, 2, 3)
        val bounded = BoundedDocumentInput(source, maximumBytes = 3)
        source[0] = 9
        assertEquals(1, bounded.copyBytes()[0].toInt())
        assertThrows(IllegalArgumentException::class.java) { BoundedDocumentInput(byteArrayOf(1, 2, 3, 4), 3) }
    }

    @Test fun validAlterationIsParsedButRemainsExplicitlyUnauthenticated() {
        val edited = validJson().replace("\"price\":1.25", "\"price\":999.99")
        val preview = StoreDataV1Codec.decode(subject(edited))
        assertEquals(BigDecimal("999.99"), preview.payload.products.single().price)
        assertFalse(preview.authenticityVerified)
        assertTrue(preview.validationErrors.isEmpty()) // no business validation is claimed at this stage
    }

    private fun validJson() = """{"kind":"stocks","version":1,"products":[{"name":"n","hashtag":"h","category":"c","description":"d","price":1.25,"stockQuantity":3,"minStockThreshold":2}]}"""
    private fun subject(json: String) = StoreDataV1Bounds.SUBJECT_PREFIX + Base64.getEncoder().encodeToString(json.toByteArray(StandardCharsets.UTF_8))
    private fun assertCode(expected: CatalogCodecError, action: () -> Unit) {
        val error = assertThrows(CatalogCodecException::class.java, action)
        assertEquals(expected, error.code)
    }
}
