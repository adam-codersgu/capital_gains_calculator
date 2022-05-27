import model.OutstandingTransactions
import model.Transaction
import java.math.BigDecimal
import java.math.RoundingMode

class ProcessSameDayTransactions : ProcessTransaction {

    private lateinit var outstandingTransactions: OutstandingTransactions

    /**
     * Match all same day asset purchases and disposals.
     * Same day disposals have top priority when matching transactions for an HMRC
     * Capital Gains report.
     *
     * @param outstandingTransactions An OutstandingTransactions object containing the total profit,
     * total loss, and outstanding buy and sell transactions when the class is initialised
     * @return An OutstandingTransactions object detailing the total profit, total loss,
     * and lists of outstanding Buy and Sell Transaction objects after all same day
     * transactions have been processed.
     */
    override
    fun process(outstandingTransactions: OutstandingTransactions): OutstandingTransactions {
        this.outstandingTransactions = outstandingTransactions

        for (sellTransaction in outstandingTransactions.sellTransactions) {
            val buyTransaction = outstandingTransactions.buyTransactions.find {
                it.date == sellTransaction.date
            }
            // A buy transaction occurred on the same day as the sell transaction
            if (buyTransaction != null) reportSameDayTransaction(buyTransaction, sellTransaction)
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
        val profitOrLoss = calculateProfitLoss(buyTransaction, sellTransaction)
        val profitOrLossMessage: String
        if (profitOrLoss >= 0) {
            profitOrLossMessage = "Profit = £$profitOrLoss."
            outstandingTransactions.totalProfit += profitOrLoss
        } else {
            profitOrLossMessage = "Loss = £$profitOrLoss."
            outstandingTransactions.totalLoss += profitOrLoss
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
                profitOrLoss = BigDecimal(valueOfSoldShares -
                        buyTransaction.price).setScale(2, RoundingMode.HALF_EVEN).toDouble()
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
                profitOrLoss = BigDecimal(sellTransaction.price -
                        valueOfPurchasedShares).setScale(2, RoundingMode.HALF_EVEN).toDouble()
            }
        }
        return profitOrLoss
    }
}