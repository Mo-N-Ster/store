package com.vibe.store.api

import java.math.BigDecimal

/** Exact text conversion at the UI boundary; business validation belongs to the application. */
object DecimalInput {
    private val pattern = Regex("[+-]?(?:[0-9]+(?:[.,][0-9]+)?|[.,][0-9]+)")

    fun nonnegative(text: String): BigDecimal? {
        val raw = text.trim()
        if (raw.length !in 1..256 || !pattern.matches(raw)) return null
        return raw.replace(',', '.').toBigDecimalOrNull()?.takeIf { it.signum() >= 0 }
    }

    fun display(value: BigDecimal, french: Boolean = false): String {
        val plain = value.stripTrailingZeros().toPlainString()
        return if (french) plain.replace('.', ',') else plain
    }
}
