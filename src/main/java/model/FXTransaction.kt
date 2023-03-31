package model

import java.time.LocalDate

data class FXTransaction(
    val transactionId: String?,
    val date: LocalDate,
    val type: String,
    var amount: Double,
    val currency: String
)