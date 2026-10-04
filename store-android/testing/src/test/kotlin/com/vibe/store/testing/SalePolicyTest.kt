package com.vibe.store.testing

import com.vibe.store.domain.SalePolicy
import org.junit.Assert.*
import org.junit.Test

class SalePolicyTest {
    @Test fun canonicalOrderMultiplicityAndExactIntent() {
        val a = listOf(2L to 1L, 1L to 2L, 2L to 1L)
        val canonical = SalePolicy.canonical(a, -0.0, null)
        assertEquals(canonical, SalePolicy.canonical(a.reversed(), 0.0, null))
        assertNotEquals(canonical, SalePolicy.canonical(listOf(1L to 2L, 2L to 2L), 0.0, null))
        assertNotEquals(canonical, SalePolicy.canonical(a, 0.0, 0.0))
        assertNotEquals(SalePolicy.canonical(a, 1.001, null), SalePolicy.canonical(a, 1.002, null))
        val decoded = SalePolicy.decode(canonical)
        assertEquals(canonical, SalePolicy.canonical(decoded.lines, decoded.discount, decoded.received))
    }
    @Test fun invalidInputsAreNotCanonicalCommands() {
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0)) {
            assertTrue(runCatching { SalePolicy.canonical(listOf(1L to 1L), bad, null) }.isFailure)
            assertTrue(runCatching { SalePolicy.canonical(listOf(1L to 1L), 0.0, bad) }.isFailure)
        }
        assertTrue(runCatching { SalePolicy.canonical(listOf(1L to 0L), 0.0, null) }.isFailure)
        assertTrue(runCatching { SalePolicy.decode("2|0|O|1:1") }.isFailure)
        assertTrue(runCatching { SalePolicy.key("../bad") }.isFailure)
    }
    @Test fun binary64RoundingDiscountClampAndReceived() {
        val total = SalePolicy.totals(listOf(12.345 to 2L), 4.005, 25.0, true)
        assertEquals(24.69, total.subtotal, 0.0)
        assertEquals(4.01, total.discount, 0.0)
        assertEquals(20.68, total.total, 0.0)
        assertEquals(4.32, total.change, 0.0)
        assertEquals(0.0, SalePolicy.totals(listOf(1.0 to 1L), 99.0, null, true).total, 0.0)
        assertEquals(1.0, SalePolicy.totals(listOf(1.0 to 1L), 99.0, null, false).total, 0.0)
        assertTrue(runCatching { SalePolicy.totals(listOf(1.0 to 1L), 0.0, 0.99, true) }.isFailure)
    }
}
