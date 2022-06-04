
import model.OutstandingTransactions
import model.Transaction
import java.math.BigDecimal
import java.math.RoundingMode

class ProcessTransactions(var buyTransactions: List<Transaction>, var sellTransactions: List<Transaction>) {

    init {
        // Sort the transactions by date
        buyTransactions = buyTransactions.sortedBy { it.date }
        sellTransactions = sellTransactions.sortedBy { it.date }
        var outstandingTransactions = OutstandingTransactions(
            buyTransactions = buyTransactions.toMutableList(),
            sellTransactions = sellTransactions.toMutableList()
        )

        outstandingTransactions = ProcessSameDayTransactions(outstandingTransactions).process()
        outstandingTransactions = ProcessBedAndBreakfastTransactions(outstandingTransactions).process()
        outstandingTransactions = ProcessSection104Transactions(outstandingTransactions).process()
        outstandingTransactions = ProcessAcquisitionFollowingDisposalTransactions(outstandingTransactions).process()
        printReportSummary(outstandingTransactions)
    }

    /**
     * Prints a summary of any unprocessed transactions, and the total gain, loss and profit figures
     * from all the transactions processed by the application.
     *
     * @param outstandingTransactions An OutstandingTransactions object containing lists of
     * outstanding buy and sell transactions and the total profit and loss incurred from
     * all transactions processed thus far,
     */
    private fun printReportSummary(outstandingTransactions: OutstandingTransactions) {
        println()
        if (outstandingTransactions.sellTransactions.isNotEmpty() || outstandingTransactions.buyTransactions.isNotEmpty()) {
            println("WARNING: The following transactions could not be matched. This " +
                    "may be because transactions are missing from the input spreadsheet or " +
                    "the transactions may be matched with disposals/acquisitions in other tax years.")
            if (outstandingTransactions.sellTransactions.isNotEmpty()) println(outstandingTransactions.sellTransactions)
            if (outstandingTransactions.buyTransactions.isNotEmpty()) println(outstandingTransactions.buyTransactions)
            println()
        }

        val gains = BigDecimal(outstandingTransactions.totalGains).setScale(2, RoundingMode.HALF_EVEN).toString()
        val losses = BigDecimal(outstandingTransactions.totalLoss).setScale(2, RoundingMode.HALF_EVEN).toString()
        val profits = BigDecimal(gains.toDouble() + losses.toDouble())
            .setScale(2, RoundingMode.HALF_EVEN).toString()
        println("Total gains (excluding fees): £$gains \n" +
                "Total losses (excluding fees): £$losses \n" +
                "Profit (gains minus losses): £$profits \n" +
                "Processing complete")
    }
}