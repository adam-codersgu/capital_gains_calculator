
import model.OutstandingTransactions
import model.Transaction

class ProcessBedAndBreakfastTransactions(outstandingTransactions: OutstandingTransactions) : ProcessTransaction(outstandingTransactions) {

    /**
     * Match all same day asset purchases and disposals.
     * Same day disposals have top priority when matching transactions for an HMRC
     * Capital Gains report.
     *
     * @return An OutstandingTransactions object detailing the total profit, total loss,
     * and lists of outstanding Buy and Sell Transaction objects after all same day
     * transactions have been processed.
     */
    override fun process(): OutstandingTransactions {
        for (sellTransaction in outstandingTransactions.sellTransactions.toList()) {
            // Look for any suitable buy transactions that occurred up to 30 days
            // after the disposal.
            val maxDate = sellTransaction.date.plusDays(31)
            val buyTransaction = outstandingTransactions.buyTransactions.find {
                it.date.isAfter(sellTransaction.date) && it.date.isBefore(maxDate)
            }

            // A buy transaction occurred on the same day as the sell transaction
            if (buyTransaction != null) reportBedAndBreakfastTransaction(buyTransaction, sellTransaction)
        }
        return outstandingTransactions
    }

    /**
     * Print a summary of the bed and breakfast disposal report to the console.
     *
     * @param buyTransaction The Transaction object associated with the purchases for a given day
     * @param sellTransaction The Transaction object associated with the sales for a given day
     */
    private fun reportBedAndBreakfastTransaction(buyTransaction: Transaction, sellTransaction: Transaction) {
        val profitOrLossMessage = super.getProfitOrLoss(buyTransaction, sellTransaction)
        val averageSellPrice = sellTransaction.price / sellTransaction.quantity
        val averageBuyPrice = buyTransaction.price / buyTransaction.quantity
        val output = "BED AND BREAKFAST Sell transaction(s) (IDs " + sellTransaction.transactionIDs +
                ") identified with buy transaction(s) (IDs " + buyTransaction.transactionIDs +
                "). " + sellTransaction.quantity + " shares sold for an average price of $averageSellPrice" +
                " GBP on " + sellTransaction.date + " identified with " + buyTransaction.quantity +
                " shares bought on " + buyTransaction.date + " for an average price of $averageBuyPrice" +
                " GBP. $profitOrLossMessage"
        println(output)
    }
}