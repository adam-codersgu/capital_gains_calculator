
import model.OutstandingTransactions
import model.Transaction

class ProcessBedAndBreakfastTransactions(outstandingTransactions: OutstandingTransactions)
    : ProcessTransaction(outstandingTransactions) {

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
            // Look for buy transactions that occurred up to 30 days following the disposal.
            val maxDate = sellTransaction.date.plusDays(31)

            var quantityOfSharesToMatch = sellTransaction.remainingQuantity
            while (quantityOfSharesToMatch > 0) {
                val buyTransaction = outstandingTransactions.buyTransactions.find {
                    it.date.isAfter(sellTransaction.date) && it.date.isBefore(maxDate)
                } ?: break

                reportBedAndBreakfastTransaction(buyTransaction.copy(),
                    getUpToDateSellTransactionByTransactionIds(sellTransaction.transactionIds))
                quantityOfSharesToMatch -= buyTransaction.remainingQuantity
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
        println("BED AND BREAKFAST DISPOSAL - " + getTransactionIdentificationMessage(buyTransaction, sellTransaction))
    }
}