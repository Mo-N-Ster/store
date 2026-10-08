package com.vibe.store.testing

import com.vibe.store.domain.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class SalePolicyTest {
    private fun d(text: String) = BigDecimal(text)
    @Test fun canonicalOrderMultiplicityScaleAndPrecision() {
        val lines = listOf(2L to 1L, 1L to 2L, 2L to 1L)
        val canonical = SalePolicy.canonical(lines, d("0.00"), null)
        assertEquals(2, SalePolicy.VERSION)
        assertEquals(canonical, SalePolicy.canonical(lines.reversed(), d("0"), null))
        assertNotEquals(canonical, SalePolicy.canonical(listOf(1L to 2L, 2L to 2L), d("0"), null))
        assertNotEquals(canonical, SalePolicy.canonical(lines, d("0"), d("0")))
        val discount = d("1.23456789012345678901234567890123456789")
        val received = d("123456789012345678901234567890.000000000000000000001")
        val encoded = SalePolicy.canonical(lines, discount, received)
        val decoded = SalePolicy.decode(encoded)
        assertTrue(ExactMoney.same(discount, decoded.discount))
        assertTrue(ExactMoney.same(received, decoded.received!!))
        assertEquals(encoded, SalePolicy.canonical(decoded.lines, decoded.discount, decoded.received))
        // Canonical decoding has no UI separator or exponent syntax, or MONEY-01A input length limit.
        val wide = d("0." + "1".repeat(300))
        assertTrue(ExactMoney.same(wide, SalePolicy.decode(SalePolicy.canonical(lines, wide, null)).discount))
    }
    @Test fun invalidInputsAreNotCanonicalCommands() {
        assertThrows(IllegalArgumentException::class.java) { SalePolicy.canonical(listOf(1L to 1L), d("-1"), null) }
        assertThrows(IllegalArgumentException::class.java) { SalePolicy.amount(d("-0.01")) }
        assertThrows(IllegalArgumentException::class.java) { SalePolicy.canonical(listOf(1L to 0L), d("0"), null) }
        for (text in listOf("1|0|O|1:1", "2|0,1|O|1:1", "2|1e2|O|1:1", "2|0.00|O|1:1"))
            assertThrows(IllegalArgumentException::class.java) { SalePolicy.decode(text) }
        assertThrows(IllegalArgumentException::class.java) { SalePolicy.key("../bad") }
    }
    @Test fun exactSubtotalDiscountClampPaidAndChange() {
        val total = SalePolicy.totals(listOf(d("12.3456") to 2L), d("4.005"), d("25"), true)
        assertEquals(d("24.6912"), total.subtotal)
        assertEquals(d("4.005"), total.discount)
        assertEquals(d("20.6862"), total.total)
        assertEquals(d("4.3138"), total.change)
        assertEquals(d("3.70370367"), SalePolicy.totals(listOf(d("1.23456789") to 3L), d("0"), null, true).subtotal)
        assertEquals(d("0.3"), SalePolicy.totals(listOf(d("0.1") to 1L, d("0.2") to 1L), d("0"), null, true).subtotal)
        assertEquals(d("0.005"), ExactMoney.subtract(d("10.005"), d("10")))
        assertEquals(d("0.30864"), ExactMoney.percentage(d("12.3456"), d("2.5")))
        assertTrue(ExactMoney.same(d("0"), SalePolicy.totals(listOf(d("1") to 1L), d("99"), null, true).total))
        assertTrue(ExactMoney.same(d("1"), SalePolicy.totals(listOf(d("1") to 1L), d("99"), null, false).total))
        assertThrows(IllegalArgumentException::class.java) { SalePolicy.totals(listOf(d("1") to 1L), d("0"), d("0.99"), true) }
    }
}
