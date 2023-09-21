package model

import java.time.LocalDate

data class Transaction(
    val transactionIds: MutableList<String>,
    val date: LocalDate,
    val type: String,
    val initialQuantity: Int,
    var remainingQuantity: Int,
    val initialValue: Double,
    // TODO - Experiment with setting these remaining values to = initial value, and see if that works
    var remainingValue: Double,
    // TODO: Note the default currency is assumed to be GBP
    var currency: String = "GBP"
) {
    fun getTransactionDateAndIdsMessage(): String {
        val pluralIndicator = if (transactionIds.size == 1) "s"
        else ""

        return "$date (transaction ID$pluralIndicator $transactionIds) "
    }

    fun getAverageUnitPrice(): Double {
        return initialValue / initialQuantity
    }
}