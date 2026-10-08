package com.vibe.store.domain

import java.math.BigDecimal

/** Versioned exact decimal intent. Sorting retains multiplicity, never merges lines. */
object SalePolicy {
    const val VERSION = 2
    const val MAX_QUANTITY = 9_007_199_254_740_991L
    fun amount(value: BigDecimal): BigDecimal {
        require(value.signum() >= 0)
        return ExactMoney.normalize(value)
    }
    private fun exact(value: BigDecimal): String {
        require(value.signum() >= 0)
        return ExactMoney.canonical(value)
    }
    fun canonical(lines: List<Pair<Long, Long>>, discount: BigDecimal, received: BigDecimal?): String {
        require(lines.size in 1..1000)
        require(lines.all { it.first > 0 && it.second in 1..MAX_QUANTITY })
        return "$VERSION|${exact(discount)}|${received?.let { "E:${exact(it)}" } ?: "O"}|" +
            lines.sortedWith(compareBy<Pair<Long, Long>> { it.first }.thenBy { it.second })
                .joinToString(";") { "${it.first}:${it.second}" }
    }
    data class Intent(val lines: List<Pair<Long, Long>>, val discount: BigDecimal, val received: BigDecimal?)
    fun decode(value: String): Intent {
        require(value.length <= 60_000)
        val fields = value.split('|')
        require(fields.size == 4 && fields[0] == VERSION.toString())
        val received = if (fields[2] == "O") null else {
            require(fields[2].startsWith("E:")); decimal(fields[2].substring(2))
        }
        val result = Intent(fields[3].split(';').map {
            val pair = it.split(':'); require(pair.size == 2)
            pair[0].toLong() to pair[1].toLong()
        }, decimal(fields[1]), received)
        require(canonical(result.lines, result.discount, result.received) == value)
        return result
    }
    private fun decimal(text: String): BigDecimal {
        require(text.matches(Regex("[0-9]+(?:\\.[0-9]+)?")))
        return amount(BigDecimal(text))
    }
    fun key(value: String): String = value.also { require(it.matches(Regex("[A-Za-z0-9_-]{1,128}"))) }
    data class Totals(val subtotal: BigDecimal, val discount: BigDecimal, val total: BigDecimal, val received: BigDecimal, val change: BigDecimal)
    fun totals(lines: List<Pair<BigDecimal, Long>>, requested: BigDecimal, received: BigDecimal?, enabled: Boolean): Totals {
        exact(requested); received?.let(::exact)
        require(lines.isNotEmpty())
        val subtotal = lines.fold(BigDecimal.ZERO) { sum, (price, quantity) ->
            amount(price); require(quantity in 1..MAX_QUANTITY)
            ExactMoney.add(sum, ExactMoney.multiply(price, quantity))
        }
        val discount = amount(if (enabled) minOf(requested, subtotal) else BigDecimal.ZERO)
        val total = ExactMoney.subtract(subtotal, discount)
        val paid = amount(received ?: total)
        require(paid >= total)
        return Totals(subtotal, discount, total, paid, ExactMoney.subtract(paid, total))
    }
}
