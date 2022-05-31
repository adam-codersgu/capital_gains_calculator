
import exceptions.AcquisitionNotFollowingDisposalException
import model.OutstandingTransactions
import model.Transaction

class ProcessAcquisitionFollowingDisposalTransactions(outstandingTransactions: OutstandingTransactions) : ProcessTransaction(outstandingTransactions) {

    /**
     * Match all purchases that occurred following a disposal.
     * Acquisitions following disposals have the lowest priority in an HMRC Capital Gains report
     * and transactions should only be matched this way if no other disposal rule applied.
     * For example, the acquisitions following disposals rule may apply to the closure
     * of a short position.
     *
     * @return An OutstandingTransactions object detailing the total profit, total loss,
     * and lists of outstanding Buy and Sell Transaction objects after all same day
     * transactions have been processed.
     */
    override fun process(): OutstandingTransactions {
        for (sellTransaction in outstandingTransactions.sellTransactions.toList()) {
            var quantityOfSharesToMatch = sellTransaction.quantity
            while (quantityOfSharesToMatch > 0) {
                val buyTransaction = if (outstandingTransactions.buyTransactions.isEmpty()) null
                else outstandingTransactions.buyTransactions[0]

                if (buyTransaction != null) {
                    // Must retrieve an up-to-date sell Transaction object in case its quantity and price have
                    // been modified during previous iterations.
                    reportAcquisitionFollowingDisposalTransaction(buyTransaction, outstandingTransactions.sellTransactions.find {
                        it.transactionIDs == sellTransaction.transactionIDs
                    } ?: throw Exception("Could not find sell transaction ID " + sellTransaction.transactionIDs))
                    quantityOfSharesToMatch -= buyTransaction.quantity
                } else break
            }
        }
        return outstandingTransactions
    }

    /**
     * Print a summary of the acquisition following a disposal report to the console.
     *
     * @param buyTransaction The Transaction object associated with the purchases for a given day
     * @param sellTransaction The Transaction object associated with the sales for a given day
     * @throws AcquisitionNotFollowingDisposalException If the date of the buy transaction does
     * not exceed the date of the sell transaction.
     */
    private fun reportAcquisitionFollowingDisposalTransaction(buyTransaction: Transaction, sellTransaction: Transaction) {
        // The date of the buy transaction must exceed the date of the sell transaction
        if (buyTransaction.date <= sellTransaction.date) {
            println("Processing halted at buy transaction " + buyTransaction.transactionIDs +
            " and sell transaction " + sellTransaction.transactionIDs)
            throw AcquisitionNotFollowingDisposalException()
        }

        val profitOrLossMessage = super.getProfitOrLoss(buyTransaction, sellTransaction)
        val averageSellPrice = sellTransaction.price / sellTransaction.quantity
        val averageBuyPrice = buyTransaction.price / buyTransaction.quantity
        val output = "ACQUISITION FOLLOWING DISPOSAL Sell transaction(s) (IDs " + sellTransaction.transactionIDs +
                ") identified with buy transaction(s) (IDs " + buyTransaction.transactionIDs +
                "). " + sellTransaction.quantity + " shares sold for an average price of $averageSellPrice" +
                " GBP on " + sellTransaction.date + " identified with " + buyTransaction.quantity +
                " shares bought on " + buyTransaction.date + " for an average price of $averageBuyPrice" +
                " GBP. $profitOrLossMessage"
        println(output)
    }
}