package model

import java.time.LocalDate

data class FXTransaction(
    val transactionIds: MutableList<String>,
    val date: LocalDate,
    val type: String,
    var amount: Double,
    val currency: String
)