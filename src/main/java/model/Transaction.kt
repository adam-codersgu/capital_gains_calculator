package model

import java.time.LocalDate

data class Transaction(
    val transactionIDs: MutableList<String>,
    val date: LocalDate,
    val direction: String,
    var quantity: Int,
    var price: Double
)