import model.Transaction

class ProcessTransactions(var buyTransactions: List<Transaction>, var sellTransactions: List<Transaction>) {

    init {
        // Sort the transactions by date
        buyTransactions = buyTransactions.sortedBy { it.date }
        sellTransactions = sellTransactions.sortedBy { it.date }

        val outstandingTransactions = ProcessSameDayTransactions().process(buyTransactions, sellTransactions)
    }
}