package com.vibe.store.testing

import com.vibe.store.domain.ExactMoney
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class ExactMoneyTest {
    private fun assertDecimal(
        expected: String,
        actual: BigDecimal,
    ) {
        assertEquals(
            0,
            BigDecimal(expected).compareTo(actual),
        )
    }

    private fun refused(block: () -> Unit) {
        try {
            block()
            fail("Expected exact-money input rejection")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun commaAndPointAreEquivalentWithoutPrecisionLoss() {
        assertDecimal(
            "12.3456",
            ExactMoney.parse("12,3456"),
        )

        assertDecimal(
            "12.3456",
            ExactMoney.parse("12.3456"),
        )

        assertEquals(
            "12.3456",
            ExactMoney.canonical(
                ExactMoney.parse("12,3456000"),
            ),
        )
    }

    @Test
    fun decimalAdditionIsExact() {
        val result = ExactMoney.add(
            ExactMoney.parse("0.1"),
            ExactMoney.parse("0.2"),
        )

        assertDecimal("0.3", result)
        assertEquals("0.3", ExactMoney.canonical(result))
    }

    @Test
    fun multiplicationNeverRoundsToCents() {
        val result = ExactMoney.multiply(
            ExactMoney.parse("1.23456789"),
            3L,
        )

        assertDecimal("3.70370367", result)
    }

    @Test
    fun monetaryDifferencePreservesFraction() {
        val result = ExactMoney.subtract(
            ExactMoney.parse("10.005"),
            ExactMoney.parse("10"),
        )

        assertDecimal("0.005", result)
    }

    @Test
    fun valuesAreNotRoundedToTwoDecimals() {
        assertDecimal(
            "2.675",
            ExactMoney.parse("2.675"),
        )

        assertDecimal(
            "12.34567890123456789",
            ExactMoney.parse(
                "12.34567890123456789",
            ),
        )
    }

    @Test
    fun percentageCalculationIsExact() {
        val result = ExactMoney.percentage(
            ExactMoney.parse("12.3456"),
            ExactMoney.parse("7.5"),
        )

        assertDecimal("0.92592", result)
    }

    @Test
    fun nonNegativeMoneyRejectsNegativeValues() {
        refused {
            ExactMoney.parseNonNegative("-0.0001")
        }

        assertDecimal(
            "0.0001",
            ExactMoney.parseNonNegative("0,0001"),
        )
    }

    @Test
    fun ambiguousOrFloatingSyntaxIsRejected() {
        listOf(
            "",
            "NaN",
            "Infinity",
            "1e3",
            "1E3",
            "1,2.3",
            "--1",
        ).forEach { value ->
            refused {
                ExactMoney.parse(value)
            }
        }
    }
}
