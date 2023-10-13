package model

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

data class Transaction(
    val transactionIds: MutableList<String>,
    val date: LocalDate,
    val type: String,
    val initialQuantity: Int,
    var remainingQuantity: Int,
    val initialValue: Double,
    var remainingValue: Double,
    // TODO: Note the default currency is assumed to be GBP
    var currency: String = "GBP"
) {
    fun getTransactionDateAndIdsMessage(): String {
        val pluralIndicator = if (transactionIds.size != 1) "s"
        else ""

        return "$date (transaction ID$pluralIndicator $transactionIds)"
    }

    fun getAverageUnitPrice(): Double {
        return BigDecimal(initialValue / initialQuantity).setScale(9, RoundingMode.HALF_EVEN).toDouble()
    }
}