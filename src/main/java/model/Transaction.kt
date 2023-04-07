package model

import java.time.LocalDate

data class Transaction(
    val transactionIds: MutableList<String>,
    val date: LocalDate,
    val type: String,
    var quantity: Int,
    var amount: Double,
    // TODO: Note the default currency is assumed to be GBP
    var currency: String = "GBP"
) {
    fun getTransactionIdsMessage(): String {
        return if (transactionIds.size == 1) {
            "transaction ID $transactionIds"
        } else "transaction IDs $transactionIds"
    }
}