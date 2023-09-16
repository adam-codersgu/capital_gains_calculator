
import model.OutstandingTransactions
import model.Transaction

class ProcessSameDayTransactions(outstandingTransactions: OutstandingTransactions) : ProcessTransaction(outstandingTransactions) {

    // TODO - Test case would be that buy/sell transactions with different dates are not processed here
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
        for (sellTransaction in outstandingTransactions.sellTransactions.toList()) {
            // Check if a buy transaction occurred on the same day as the sell transaction
            outstandingTransactions.buyTransactions.find {
                it.date == sellTransaction.date
            }?.let { buyTransaction ->
                reportSameDayTransaction(buyTransaction.copy(), sellTransaction.copy())
            }
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
        println("SAME DAY - " + getProportionOfUnitsSoldMessage(buyTransaction, sellTransaction) +
                " units sold on " + sellTransaction.getTransactionDateAndIdsMessage() +
                " were identified with " + getProportionOfUnitsBoughtMessage(buyTransaction, sellTransaction) +
                " units bought on " + buyTransaction.getTransactionDateAndIdsMessage() + ". The average unit" +
                " price of the purchased shares was " + buyTransaction.getAverageUnitPrice() + " GBP and the" +
                " average unit price of the sold shares was " + sellTransaction.getAverageUnitPrice() +
                " GBP. " + super.getProfitOrLossMessage(buyTransaction, sellTransaction))
    }
}