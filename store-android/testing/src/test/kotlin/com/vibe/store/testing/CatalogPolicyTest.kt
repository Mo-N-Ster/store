package com.vibe.store.testing

import com.vibe.store.domain.*
import org.junit.Assert.*
import org.junit.Test

class CatalogPolicyTest {
    @Test fun integerNonNegativeQuantitiesOnly() {
        assertEquals(0L, CatalogPolicy.quantity(0.0))
        assertEquals(125L, CatalogPolicy.quantity(125.0))
        listOf(-1.0, 0.1, Double.NaN, Double.POSITIVE_INFINITY, 9_007_199_254_740_992.0).forEach {
            assertThrows(IllegalArgumentException::class.java) { CatalogPolicy.quantity(it) }
        }
    }
    @Test fun pricesAreValidatedNeverRounded() {
        for (text in listOf("12.3456", "2.675", "0", "1.001")) {
            val price = java.math.BigDecimal(text)
            assertEquals(price, CatalogPolicy.price(price))
        }
        assertThrows(IllegalArgumentException::class.java) { CatalogPolicy.price(java.math.BigDecimal("-1")) }
    }
    @Test fun strictHistoryDiscriminatorAndJustification() {
        assertEquals("purchases", CatalogPolicy.historyType("purchases"))
        listOf(null, "", "personnel", "sales", "PURCHASES", " purchases", 1, emptyMap<String, String>()).forEach {
            assertThrows(IllegalArgumentException::class.java) { CatalogPolicy.historyType(it) }
        }
        assertEquals("counted", CatalogPolicy.adjustmentReason(" counted "))
        assertThrows(IllegalArgumentException::class.java) { CatalogPolicy.adjustmentReason("  ") }
    }
    @Test fun signaturesAndPrivateReferencePolicy() {
        assertEquals(ImageKind.JPEG, MediaSignature.detect(byteArrayOf(-1, -40, -1)))
        assertEquals(ImageKind.PNG, MediaSignature.detect(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)))
        assertEquals(ImageKind.WEBP, MediaSignature.detect("RIFF0000WEBP".toByteArray()))
        assertNull(MediaSignature.detect("photo.jpg".toByteArray()))
        assertNull(MediaSignature.detect(byteArrayOf(-1, -40)))
        assertTrue(MediaSignature.validReference("01234567-0123-0123-0123-0123456789ab.png"))
        listOf("../photo.jpg", "/photo.jpg", "content://photo", "x.png", "01234567-0123-0123-0123-0123456789ab.png/..").forEach {
            assertFalse(MediaSignature.validReference(it))
        }
        assertEquals(5 * 1024 * 1024, MediaPolicy.PRODUCT.maximumBytes)
        assertEquals(512 * 1024, MediaPolicy.PROFILE.maximumBytes)
    }
}
