package com.vibe.store.domain

/** Versioned binary64 intent. Sorting retains multiplicity, never merges lines. */
object SalePolicy {
    const val VERSION = 1
    const val MAX_QUANTITY = 9_007_199_254_740_991L
    fun amount(value: Double): Double {
        require(value.isFinite() && value >= 0)
        return SourceMoneyParity.roundTwo(value).also { require(it.isFinite()) }
    }
    private fun exact(value: Double): String {
        require(value.isFinite() && value >= 0)
        return java.lang.Double.toHexString(if (value == 0.0) 0.0 else value)
    }
    fun canonical(lines: List<Pair<Long, Long>>, discount: Double, received: Double?): String {
        require(lines.size in 1..1000)
        require(lines.all { it.first > 0 && it.second in 1..MAX_QUANTITY })
        return "1|${exact(discount)}|${received?.let { "E:${exact(it)}" } ?: "O"}|" +
            lines.sortedWith(compareBy<Pair<Long, Long>> { it.first }.thenBy { it.second })
                .joinToString(";") { "${it.first}:${it.second}" }
    }
    data class Intent(val lines: List<Pair<Long, Long>>, val discount: Double, val received: Double?)
    fun decode(value: String): Intent {
        require(value.length <= 60_000)
        val fields = value.split('|')
        require(fields.size == 4 && fields[0] == "1")
        val received = if (fields[2] == "O") null else {
            require(fields[2].startsWith("E:")); java.lang.Double.valueOf(fields[2].substring(2))
        }
        val result = Intent(fields[3].split(';').map {
            val pair = it.split(':'); require(pair.size == 2)
            pair[0].toLong() to pair[1].toLong()
        }, java.lang.Double.valueOf(fields[1]), received)
        require(canonical(result.lines, result.discount, result.received) == value)
        return result
    }
    fun key(value: String): String = value.also { require(it.matches(Regex("[A-Za-z0-9_-]{1,128}"))) }
    data class Totals(val subtotal: Double, val discount: Double, val total: Double, val received: Double, val change: Double)
    fun totals(lines: List<Pair<Double, Long>>, requested: Double, received: Double?, enabled: Boolean): Totals {
        exact(requested); received?.let(::exact)
        require(lines.isNotEmpty())
        val subtotal = amount(lines.sumOf { (price, quantity) ->
            require(price.isFinite() && price >= 0 && quantity in 1..MAX_QUANTITY)
            price * quantity
        })
        val discount = amount(if (enabled) minOf(requested, subtotal) else 0.0)
        val total = amount(subtotal - discount)
        val paid = amount(received ?: total)
        require(paid >= total)
        return Totals(subtotal, discount, total, paid, amount(paid - total))
    }
}
