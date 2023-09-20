
import model.OutstandingTransactions
import model.Transaction
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.min

abstract class ProcessTransaction(val outstandingTransactions: OutstandingTransactions) {

    /**
     * Process a given transaction.
     *
     * @return An OutstandingTransactions object detailing the total profit, total loss,
     * and lists of outstanding Buy and Sell Transaction objects after all transactions managed by the
     * class have been processed.
     */
    abstract fun process() : OutstandingTransactions

    protected fun getTransactionIdentificationMessage(buyTransaction: Transaction, sellTransaction: Transaction): String {
        return getProportionOfUnitsSoldMessage(buyTransaction, sellTransaction) +
                " units sold on " + sellTransaction.getTransactionDateAndIdsMessage() +
                " were identified with " + getProportionOfUnitsBoughtMessage(buyTransaction, sellTransaction) +
                " units bought on " + buyTransaction.getTransactionDateAndIdsMessage() + ". The average unit" +
                " price of the purchased shares was " + buyTransaction.getAverageUnitPrice() + " GBP and the" +
                " average unit price of the sold shares was " + sellTransaction.getAverageUnitPrice() +
                " GBP. " + getProfitOrLossMessage(buyTransaction, sellTransaction)
    }

    /**
     * Returns a summary of the profit or loss resulting from a given transaction.
     *
     * @param buyTransaction The Transaction object associated with the purchases for a given day
     * @param sellTransaction The Transaction object associated with the sales for a given day
     * @return A String describing the profit or loss incurred from the transactions
     */
    private fun getProfitOrLossMessage(buyTransaction: Transaction, sellTransaction: Transaction): String {
        val profitOrLoss = calculateProfitLoss(buyTransaction, sellTransaction)
        updateOutstandingTransactions(buyTransaction, sellTransaction)
        return if (profitOrLoss >= 0) {
            outstandingTransactions.totalGains += profitOrLoss
            "Profit = £${roundAmountToTwoDecimalPlaces(profitOrLoss)}"
        } else {
            outstandingTransactions.totalLoss += profitOrLoss
            "Loss = £${roundAmountToTwoDecimalPlaces(profitOrLoss)}"
        }
    }

    protected fun roundAmountToTwoDecimalPlaces(amount: Double): String {
        return BigDecimal(amount).setScale(2, RoundingMode.HALF_EVEN).toString()
    }

    /**
     * Calculate the profit or loss incurred from the sale of an asset.
     *
     * @param buyTransaction The Transaction object associated with the purchases for a given day
     * @param sellTransaction The Transaction object associated with the sales for a given day
     * @return A Double representing the profit or loss incurred from the transactions
     */
    private fun calculateProfitLoss(buyTransaction: Transaction, sellTransaction: Transaction): Double {
        return if (sellTransaction.remainingQuantity == buyTransaction.remainingQuantity) {
            sellTransaction.remainingValue - buyTransaction.remainingValue
        } else {
            val quantitySold = min(buyTransaction.remainingQuantity, sellTransaction.remainingQuantity)
            (sellTransaction.getAverageUnitPrice() - buyTransaction.getAverageUnitPrice()) *
                    quantitySold
        }
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
            sellTransaction.remainingQuantity == buyTransaction.remainingQuantity -> {
                outstandingTransactions.buyTransactions.remove(buyTransaction)
                outstandingTransactions.sellTransactions.remove(sellTransaction)
            }
            sellTransaction.remainingQuantity > buyTransaction.remainingQuantity -> {
                val valueOfSoldShares = sellTransaction.getAverageUnitPrice() * buyTransaction.remainingQuantity
                val index = outstandingTransactions.sellTransactions.indexOf(sellTransaction)
                outstandingTransactions.sellTransactions[index].remainingQuantity -= buyTransaction.remainingQuantity
                outstandingTransactions.sellTransactions[index].remainingValue -= valueOfSoldShares
                outstandingTransactions.buyTransactions.remove(buyTransaction)
            }
            // Quantity purchased is greater than the quantity sold
            else -> {
                val valueOfPurchasedShares = buyTransaction.getAverageUnitPrice() * sellTransaction.remainingQuantity
                val index = outstandingTransactions.buyTransactions.indexOf(buyTransaction)
                outstandingTransactions.buyTransactions[index].remainingQuantity -= sellTransaction.remainingQuantity
                outstandingTransactions.buyTransactions[index].remainingValue -= valueOfPurchasedShares
                outstandingTransactions.sellTransactions.remove(sellTransaction)
            }
        }
    }

    private fun getProportionOfUnitsSoldMessage(buyTransaction: Transaction, sellTransaction: Transaction) : String {
        return if (buyTransaction.initialQuantity < sellTransaction.initialQuantity || sellTransaction.initialQuantity != sellTransaction.remainingQuantity) {
            "${buyTransaction.remainingQuantity} of the ${sellTransaction.initialQuantity}"
        } else {
            "All ${sellTransaction.initialQuantity}"
        }
    }

    private fun getProportionOfUnitsBoughtMessage(buyTransaction: Transaction, sellTransaction: Transaction) : String {
        return if (sellTransaction.initialQuantity < buyTransaction.initialQuantity || buyTransaction.initialQuantity != buyTransaction.remainingQuantity) {
            "${sellTransaction.remainingQuantity} of the ${buyTransaction.initialQuantity}"
        } else {
            "all ${buyTransaction.initialQuantity}"
        }
    }
}