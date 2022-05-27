import model.OutstandingTransactions

interface ProcessTransaction {
    /**
     * Process a given transaction.
     *
     * @param outstandingTransactions An OutstandingTransactions object containing the total profit,
     * total loss, and outstanding buy and sell transactions when the class is initialised
     * @return An OutstandingTransactions object detailing the total profit, total loss,
     * and lists of outstanding Buy and Sell Transaction objects after all transactions managed by the
     * class have been processed.
     */
    fun process(outstandingTransactions: OutstandingTransactions) : OutstandingTransactions
}