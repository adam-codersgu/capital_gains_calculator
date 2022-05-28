import model.OutstandingTransactions
import model.Transaction
import java.math.BigDecimal
import java.math.RoundingMode

abstract class ProcessTransaction(outstandingTransactions: OutstandingTransactions) {

    val outstandingTransactions: OutstandingTransactions

    init {
        this.outstandingTransactions = outstandingTransactions
    }

    /**
     * Process a given transaction.
     *
     * @return An OutstandingTransactions object detailing the total profit, total loss,
     * and lists of outstanding Buy and Sell Transaction objects after all transactions managed by the
     * class have been processed.
     */
    abstract fun process() : OutstandingTransactions

    /**
     * Returns a summary of the profit or loss resulting from a given transaction.
     *
     * @param buyTransaction The Transaction object associated with the purchases for a given day
     * @param sellTransaction The Transaction object associated with the sales for a given day
     * @return A String describing the profit or loss incurred from the transactions
     */
    fun getProfitOrLoss(buyTransaction: Transaction, sellTransaction: Transaction): String {
        val profitOrLoss = calculateProfitLoss(buyTransaction, sellTransaction)
        return if (profitOrLoss >= 0) {
            outstandingTransactions.totalProfit += profitOrLoss
            "Profit = £$profitOrLoss."
        } else {
            outstandingTransactions.totalLoss += profitOrLoss
            "Loss = £$profitOrLoss."
        }
    }

    /**
     * Calculate the profit or loss incurred from the sale of an asset.
     *
     * @param buyTransaction The Transaction object associated with the purchases for a given day
     * @param sellTransaction The Transaction object associated with the sales for a given day
     * @return A Double representing the profit or loss incurred from the transactions
     */
    private fun calculateProfitLoss(buyTransaction: Transaction, sellTransaction: Transaction): Double {
        val profitOrLoss: Double
        when {
            // Quantity purchased and sold are equal
            sellTransaction.quantity == buyTransaction.quantity -> {
                profitOrLoss = BigDecimal(sellTransaction.price -
                        buyTransaction.price).setScale(2, RoundingMode.HALF_EVEN).toDouble()
            }
            // Quantity sold is greater than the quantity purchased
            sellTransaction.quantity > buyTransaction.quantity -> {
                val percentageOfSoldSharesRemaining = buyTransaction.quantity.toDouble() /
                        sellTransaction.quantity.toDouble()
                val valueOfSoldShares = sellTransaction.price * percentageOfSoldSharesRemaining
                profitOrLoss = BigDecimal(valueOfSoldShares -
                        buyTransaction.price).setScale(2, RoundingMode.HALF_EVEN).toDouble()
            }
            // Quantity purchased is greater than the quantity sold
            else -> {
                val percentageOfPurchasedSharesRemaining = sellTransaction.quantity.toDouble() /
                        buyTransaction.quantity.toDouble()
                val valueOfPurchasedShares = buyTransaction.price * percentageOfPurchasedSharesRemaining
                profitOrLoss = BigDecimal(sellTransaction.price -
                        valueOfPurchasedShares).setScale(2, RoundingMode.HALF_EVEN).toDouble()
            }
        }
        updateOutstandingTransactions(buyTransaction, sellTransaction)
        return profitOrLoss
    }

    /**
     * Update the lists of outstanding transactions to reflect the purchases and disposals
     * that have been matched and no longer require processing.
     *
     * @param buyTransaction The Transaction object associated with the purchases for a given day
     * @param sellTransaction The Transaction object associated with the sales for a given day
     */
    private fun updateOutstandingTransactions(buyTransaction: Transaction, sellTransaction: Transaction) {
        when {
            // Quantity purchased and sold are equal
            sellTransaction.quantity == buyTransaction.quantity -> {
                outstandingTransactions.buyTransactions.remove(buyTransaction)
                outstandingTransactions.sellTransactions.remove(sellTransaction)
            }
            // Quantity sold is greater than the quantity purchased
            sellTransaction.quantity > buyTransaction.quantity -> {
                val percentageOfSoldSharesRemaining = buyTransaction.quantity.toDouble() /
                        sellTransaction.quantity.toDouble()
                val valueOfSoldShares = sellTransaction.price * percentageOfSoldSharesRemaining
                val index = outstandingTransactions.sellTransactions.indexOf(sellTransaction)
                outstandingTransactions.sellTransactions[index].quantity -= buyTransaction.quantity
                outstandingTransactions.sellTransactions[index].price -= valueOfSoldShares
                outstandingTransactions.buyTransactions.remove(buyTransaction)
            }
            // Quantity purchased is greater than the quantity sold
            else -> {
                val percentageOfPurchasedSharesRemaining = sellTransaction.quantity.toDouble() /
                        buyTransaction.quantity.toDouble()
                val valueOfPurchasedShares = buyTransaction.price * percentageOfPurchasedSharesRemaining
                val index = outstandingTransactions.buyTransactions.indexOf(buyTransaction)
                outstandingTransactions.buyTransactions[index].quantity -= sellTransaction.quantity
                outstandingTransactions.buyTransactions[index].price -= valueOfPurchasedShares
                outstandingTransactions.sellTransactions.remove(sellTransaction)
            }
        }
    }
}