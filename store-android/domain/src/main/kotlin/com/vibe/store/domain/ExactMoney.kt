package com.vibe.store.domain

import java.math.BigDecimal

/**
 * Exact decimal arithmetic for monetary values.
 *
 * Contract:
 * - never converts through Double/Float;
 * - never rounds or truncates;
 * - accepts comma or point as decimal input separator;
 * - canonical representation uses a point and no exponent;
 * - arithmetic is exact for addition, subtraction, multiplication
 *   and percentage division by 100.
 */
object ExactMoney {
    private const val MAX_INPUT_LENGTH = 256

    private val decimalPattern =
        Regex("[+-]?(?:[0-9]+(?:[.,][0-9]+)?|[.,][0-9]+)")

    fun parse(text: String): BigDecimal {
        val raw = text.trim()

        require(raw.isNotEmpty())
        require(raw.length <= MAX_INPUT_LENGTH)
        require(raw.matches(decimalPattern))
        require(!(raw.contains(',') && raw.contains('.')))

        return normalize(
            BigDecimal(raw.replace(',', '.')),
        )
    }

    fun parseNonNegative(text: String): BigDecimal =
        parse(text).also {
            require(it.signum() >= 0)
        }

    fun normalize(value: BigDecimal): BigDecimal {
        if (value.signum() == 0) {
            return BigDecimal.ZERO
        }

        return value.stripTrailingZeros()
    }

    fun canonical(value: BigDecimal): String =
        normalize(value).toPlainString()

    fun same(
        left: BigDecimal,
        right: BigDecimal,
    ): Boolean =
        left.compareTo(right) == 0

    fun add(
        left: BigDecimal,
        right: BigDecimal,
    ): BigDecimal =
        normalize(left.add(right))

    fun subtract(
        left: BigDecimal,
        right: BigDecimal,
    ): BigDecimal =
        normalize(left.subtract(right))

    fun multiply(
        value: BigDecimal,
        quantity: Long,
    ): BigDecimal =
        normalize(
            value.multiply(BigDecimal.valueOf(quantity)),
        )

    fun multiply(
        left: BigDecimal,
        right: BigDecimal,
    ): BigDecimal =
        normalize(left.multiply(right))

    fun percentage(
        base: BigDecimal,
        percent: BigDecimal,
    ): BigDecimal {
        require(percent.signum() >= 0)

        return normalize(
            base.multiply(percent)
                .movePointLeft(2),
        )
    }
}
