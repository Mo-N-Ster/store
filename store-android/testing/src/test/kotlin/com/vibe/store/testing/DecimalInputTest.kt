package com.vibe.store.testing

import com.vibe.store.api.DecimalInput
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class DecimalInputTest {
    @Test fun preservesExactInputAndPlainDisplay() {
        val text = "9007199254740993.1234567890123456789"
        assertEquals(BigDecimal(text), DecimalInput.nonnegative(text))
        assertEquals(text, DecimalInput.display(BigDecimal(text)))
        assertEquals("0,000000001", DecimalInput.display(BigDecimal("1E-9"), true))
        assertEquals(BigDecimal("0.12345"), DecimalInput.nonnegative(" 0,12345 "))
        assertEquals("0", DecimalInput.display(BigDecimal("0.000")))
    }
    @Test fun rejectsInvalidInputWithoutCoercion() {
        listOf("", "NaN", "Infinity", "1e2", "1,2.3", "-0.01", "1".repeat(257)).forEach {
            assertNull(DecimalInput.nonnegative(it))
        }
    }
}
