package model

data class OutstandingTransactions(
    var totalProfit: Double = 0.00,
    var totalLoss: Double = 0.00,
    val buyTransactions: MutableList<Transaction> = mutableListOf(),
    val sellTransactions: MutableList<Transaction> = mutableListOf()
)
