package model

import java.time.LocalDate

data class DividendTransaction(
    val date: LocalDate,
    val type: String,
    val amount: Double,
    var currency: String
)