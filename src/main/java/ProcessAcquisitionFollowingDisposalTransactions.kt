
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
            var quantityOfSharesToMatch = sellTransaction.remainingQuantity
            while (quantityOfSharesToMatch > 0) {
                if (outstandingTransactions.buyTransactions.isEmpty()) break
                else outstandingTransactions.buyTransactions[0].let { buyTransaction ->
                    reportAcquisitionFollowingDisposalTransaction(buyTransaction.copy(),
                        getUpToDateSellTransactionByTransactionIds(sellTransaction.transactionIds))
                    quantityOfSharesToMatch -= buyTransaction.remainingQuantity
                }
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
        if (buyTransaction.date <= sellTransaction.date) {
            throw AcquisitionNotFollowingDisposalException(buyTransaction, sellTransaction)
        }

        println("ACQUISITION FOLLOWING DISPOSAL - " + getTransactionIdentificationMessage(buyTransaction, sellTransaction))
    }
}