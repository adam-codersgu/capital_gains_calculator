
import model.OutstandingTransactions
import model.Transaction

class ProcessBedAndBreakfastTransactions(outstandingTransactions: OutstandingTransactions) : ProcessTransaction(outstandingTransactions) {

    /**
     * Match all purchases that occurred up to 30 days following a disposal (Bed and Breakfast rule).
     * Bed and Breakfast disposals have second priority (after Same Day disposals) when matching
     * transactions for an HMRC Capital Gains report.
     *
     * @return An OutstandingTransactions object detailing the total profit, total loss,
     * and lists of outstanding Buy and Sell Transaction objects after all Bed and Breakfast
     * transactions have been processed.
     */
    override fun process(): OutstandingTransactions {
        for (sellTransaction in outstandingTransactions.sellTransactions.toList()) {
            // Look for any suitable buy transactions that occurred up to 30 days
            // after the disposal.
            val maxDate = sellTransaction.date.plusDays(31)

            var quantityOfSharesToMatch = sellTransaction.quantity
            while (quantityOfSharesToMatch > 0) {
                val buyTransaction = outstandingTransactions.buyTransactions.find {
                    it.date.isAfter(sellTransaction.date) && it.date.isBefore(maxDate)
                }

                if (buyTransaction != null) {
                    // Must retrieve an up-to-date sell Transaction object in case its quantity and price have
                    // been modified during previous iterations.
                    reportBedAndBreakfastTransaction(buyTransaction, outstandingTransactions.sellTransactions.find {
                        it.transactionIDs == sellTransaction.transactionIDs
                    } ?: throw Exception("Could not find sell transaction ID " + sellTransaction.transactionIDs))
                    quantityOfSharesToMatch -= buyTransaction.quantity
                } else break
            }
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