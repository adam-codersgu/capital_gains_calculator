package model

data class Section104(
    var transactionIDs: MutableList<String> = mutableListOf(),
    var quantity: Int = 0,
    var price: Double = 0.00
)