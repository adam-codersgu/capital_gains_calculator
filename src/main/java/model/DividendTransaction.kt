package model

import java.time.LocalDate

data class DividendTransaction(
    val date: LocalDate,
    val type: String,
    val amount: Double,
    // TODO: Note the default currency is assumed to be GBP
    var currency: String = "GBP"
)