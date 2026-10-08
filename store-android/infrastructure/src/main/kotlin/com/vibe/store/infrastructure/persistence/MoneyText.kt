package com.vibe.store.infrastructure.persistence

import androidx.room.TypeConverter
import java.math.BigDecimal

/** Single persistence contract: normalized plain decimal, no exponent, no precision loss. */
internal class MoneyText {
    @TypeConverter fun encode(value: BigDecimal?): String? = value?.let(::canonical)
    @TypeConverter fun decode(value: String?): BigDecimal? = value?.let(::parse)

    companion object {
        private val canonicalPattern = Regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]*[1-9])?")
        fun canonical(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()
        fun parse(value: String): BigDecimal {
            require(canonicalPattern.matches(value) && value != "-0") { "Invalid canonical monetary TEXT" }
            return BigDecimal(value).stripTrailingZeros()
        }

        // SQLite string predicates only: never cast a decimal to REAL or compare TEXT to numbers.
        fun sqlCheck(value: String, signed: Boolean): String {
            val body = "(CASE WHEN substr($value,1,1)='-' THEN substr($value,2) ELSE $value END)"
            val point = "instr($body,'.')"
            val whole = "(CASE WHEN $point=0 THEN $body ELSE substr($body,1,$point-1) END)"
            val fraction = "substr($body,$point+1)"
            val sign = if (signed) "$value!='-0'" else "substr($value,1,1)!='-'"
            return "($value IS NULL OR (typeof($value)='text' AND $sign AND length($whole)>0 " +
                "AND $whole NOT GLOB '*[^0-9]*' AND (length($whole)=1 OR substr($whole,1,1)!='0') " +
                "AND ($point=0 OR (length($fraction)>0 AND $fraction NOT GLOB '*[^0-9]*' AND substr($fraction,-1)!='0'))))"
        }
    }
}

internal object MoneyColumns {
    val tables = linkedMapOf(
        "cash_sessions" to listOf("openingAmount", "closingAmount", "expectedAmount", "difference"),
        "products" to listOf("price"),
        "product_price_history" to listOf("price"),
        "stock_movements" to listOf("unitPrice"),
        "invoices" to listOf("subtotal", "totalAmount", "discount"),
        "invoice_lines" to listOf("unitPrice", "totalLine", "unitCost"),
        "payments" to listOf("amount", "received", "change"),
        "purchases" to listOf("totalAmount"),
        "purchase_items" to listOf("unitCost", "totalLine"),
        "audit_logs" to listOf("cashAmount"),
    )
    fun signed(table: String, column: String): Boolean =
        (table == "cash_sessions" && column in listOf("expectedAmount", "difference")) || table == "audit_logs"
}
