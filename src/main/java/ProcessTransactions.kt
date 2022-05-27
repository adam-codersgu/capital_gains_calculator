import model.OutstandingTransactions
import model.Transaction

class ProcessTransactions(var buyTransactions: List<Transaction>, var sellTransactions: List<Transaction>) {

    init {
        // Sort the transactions by date
        buyTransactions = buyTransactions.sortedBy { it.date }
        sellTransactions = sellTransactions.sortedBy { it.date }
        var outstandingTransactions = OutstandingTransactions(
            buyTransactions = buyTransactions.toMutableList(),
            sellTransactions = sellTransactions.toMutableList()
        )

        outstandingTransactions = ProcessSameDayTransactions().process(outstandingTransactions)
        outstandingTransactions = ProcessBedAndBreakfastTransactions().process(outstandingTransactions)
    }
}