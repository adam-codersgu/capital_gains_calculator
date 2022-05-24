import model.Transaction
import java.math.BigDecimal
import java.math.RoundingMode

class ProcessSameDayTransactions {

    private val outstandingBuyTransactions = mutableListOf<Transaction>()
    private val outstandingSellTransactions = mutableListOf<Transaction>()
    private var totalProfit = 0.00
    private var totalLoss = 0.00

    /**
     * Match all same day asset purchases and disposals.
     * Same day disposals have top priority when matching transactions for an HMRC
     * Capital Gains report.
     *
     * @param buyTransactions A list containing all purchase transactions ordered by date
     * @param sellTransactions A list containing all sale transactions ordered by date
     */
    fun process(buyTransactions: List<Transaction>, sellTransactions: List<Transaction>) {
        outstandingBuyTransactions.addAll(buyTransactions)
        for (sellTransaction in sellTransactions) {
            val buyTransaction = outstandingBuyTransactions.find { it.date == sellTransaction.date }
            // A buy transaction occurred on the same day as the sell transaction
            if (buyTransaction != null) reportSameDayTransaction(buyTransaction, sellTransaction)
            // No buy transaction found, hence not a same day disposal
            else outstandingSellTransactions.add(sellTransaction)
        }
    }

    /**
     * Print a summary of the same day disposal report to the console.
     *
     * @param buyTransaction The Transaction object associated with the purchases for a given day
     * @param sellTransaction The Transaction object associated with the sales for a given day
     */
    private fun reportSameDayTransaction(buyTransaction: Transaction, sellTransaction: Transaction) {
        val profitOrLoss = calculateProfitLoss(buyTransaction, sellTransaction)
        val profitOrLossMessage: String
        if (profitOrLoss >= 0) {
            profitOrLossMessage = "Profit = £$profitOrLoss."
            totalProfit += profitOrLoss
        } else {
            profitOrLossMessage = "Loss = £$profitOrLoss."
            totalLoss += profitOrLoss
        }

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

    /**
     * Calculate the profit or loss incurred from the sale of an asset due to the same day disposal
     * rule.
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
                outstandingBuyTransactions.remove(buyTransaction)
            }
            // Quantity sold is greater than the quantity purchased
            sellTransaction.quantity > buyTransaction.quantity -> {
                val percentageOfSoldSharesRemaining = buyTransaction.quantity.toDouble() /
                        sellTransaction.quantity.toDouble()
                val valueOfSoldShares = sellTransaction.price * percentageOfSoldSharesRemaining
                val remainingSoldSharesTransaction = sellTransaction.copy(
                    quantity = sellTransaction.quantity - buyTransaction.quantity,
                    price = sellTransaction.price - valueOfSoldShares
                )
                outstandingSellTransactions.add(remainingSoldSharesTransaction)
                profitOrLoss = BigDecimal(valueOfSoldShares -
                        buyTransaction.price).setScale(2, RoundingMode.HALF_EVEN).toDouble()
                outstandingBuyTransactions.remove(buyTransaction)
            }
            // Quantity purchased is greater than the quantity sold
            else -> {
                val percentageOfPurchasedSharesRemaining = sellTransaction.quantity.toDouble() /
                        buyTransaction.quantity.toDouble()
                val valueOfPurchasedShares = buyTransaction.price * percentageOfPurchasedSharesRemaining
                val index = outstandingBuyTransactions.indexOf(buyTransaction)
                outstandingBuyTransactions[index].quantity -= sellTransaction.quantity
                outstandingBuyTransactions[index].price -= valueOfPurchasedShares
                profitOrLoss = BigDecimal(sellTransaction.price -
                        valueOfPurchasedShares).setScale(2, RoundingMode.HALF_EVEN).toDouble()
            }
        }
        return profitOrLoss
    }

    /**
     *
     * TODO: COULD RETURN OUTPUT IN JSON FORMAT INCLUDING RUNNING GAINS AND LOSSES?
     *
     */
    /* fun processSameDayTransactions2(sellTransactions: MutableList<Transaction>, buyTransactions: MutableList<Transaction>) {

        processBedBreakfastTransactions(sellTransactions, buyTransactions)
    } */
}