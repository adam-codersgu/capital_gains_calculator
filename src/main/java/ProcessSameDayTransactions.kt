
import model.OutstandingTransactions
import model.Transaction

class ProcessSameDayTransactions(outstandingTransactions: OutstandingTransactions) : ProcessTransaction(outstandingTransactions) {

    /**
     * Match all same day asset purchases and disposals.
     * Same day disposals have top priority when matching transactions for an HMRC
     * Capital Gains report.
     *
     * @return An OutstandingTransactions object detailing the total profit, total loss,
     * and lists of outstanding Buy and Sell Transaction objects after all same day
     * transactions have been processed.
     */
    override
    fun process(): OutstandingTransactions {
        for (sellTransaction in outstandingTransactions.sellTransactions) {
            val buyTransaction = outstandingTransactions.buyTransactions.find {
                it.date == sellTransaction.date
            }
            // A buy transaction occurred on the same day as the sell transaction
            if (buyTransaction != null) reportSameDayTransaction(buyTransaction, sellTransaction)
        }
        return outstandingTransactions
    }

    /**
     * Print a summary of the same day disposal report to the console.
     *
     * @param buyTransaction The Transaction object associated with the purchases for a given day
     * @param sellTransaction The Transaction object associated with the sales for a given day
     */
    private fun reportSameDayTransaction(buyTransaction: Transaction, sellTransaction: Transaction) {
        val profitOrLossMessage = super.getProfitOrLoss(buyTransaction, sellTransaction)
        val averageSellPrice = sellTransaction.price / sellTransaction.quantity
        val averageBuyPrice = buyTransaction.price / buyTransaction.quantity
        val output = "SAME DAY Sell transaction(s) (IDs " + sellTransaction.transactionIDs +
                ") identified with buy transaction(s) (IDs " + buyTransaction.transactionIDs +
                ") on " + buyTransaction.date + ". " + buyTransaction.quantity +
                " shares were bought for an average price of $averageBuyPrice GBP and " +
                sellTransaction.quantity + " shares were sold for an average price of $averageSellPrice " +
                "GBP. $profitOrLossMessage"
        println(output)
    }
}