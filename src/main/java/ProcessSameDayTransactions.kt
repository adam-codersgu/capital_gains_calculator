import model.Transaction
import java.math.BigDecimal
import java.math.RoundingMode

class ProcessSameDayTransactions {

    private val outstandingBuyTransactions = mutableListOf<Transaction>()
    private val outstandingSellTransactions = mutableListOf<Transaction>()

    /**
     * Match all same day asset purchases and disposals.
     * Same day disposals have top priority when matching transactions for an HMRC
     * Capital Gains report.
     *
     * @param buyTransactions A list containing all purchase transactions ordered by date
     * @param sellTransactions A list containing all sale transactions ordered by date
     */
    fun process(buyTransactions: List<Transaction>, sellTransactions: List<Transaction>) {
        for (sellTransaction in sellTransactions) {
            val buyTransaction = buyTransactions.find { it.date == sellTransaction.date }
            // A buy transaction occurred on the same day as the sell transaction
            if (buyTransaction != null) matchSameDayTransaction(buyTransaction, sellTransaction)
            // No buy transaction found, hence not a same day disposal
            else outstandingSellTransactions.add(sellTransaction)
        }
    }

    private fun matchSameDayTransaction(buyTransaction: Transaction, sellTransaction: Transaction) {
        // TODO: Implement
    }

    /**
     *
     * TODO: COULD RETURN OUTPUT IN JSON FORMAT INCLUDING RUNNING GAINS AND LOSSES?
     *
     */
    fun processSameDayTransactions2(sellTransactions: MutableList<Transaction>, buyTransactions: MutableList<Transaction>) {
        val sellTransactionsToRemove = mutableListOf<Int>()
        for ((i, t) in sellTransactions.withIndex()) {
            val index = buyTransactions.indexOfFirst {
                it.date == t.date
            }
            if (index != -1) {
                val buyTransaction = buyTransactions[index]
                val sellAvgPrice = t.price / t.quantity
                val buyAvgPrice = buyTransaction.price / buyTransaction.quantity
                var message = "SAME DAY Sell transaction(s) (IDs " + t.transactionIDs + ") identified with buy transaction(s) (IDs " + buyTransaction.transactionIDs +
                        ") on " + buyTransaction.date + ". " + buyTransaction.quantity + " shares were bought for an average price of $buyAvgPrice GBP and " + t.quantity +
                        " shares were sold for an average price of $sellAvgPrice GBP. "
                var pl: String?
                when {
                    t.quantity == buyTransaction.quantity -> {
                        pl = BigDecimal(t.price - buyTransaction.price).setScale(2, RoundingMode.HALF_EVEN).toString()
                        buyTransactions.removeAt(index)
                        sellTransactionsToRemove.add(0, i)
                    }
                    t.quantity > buyTransaction.quantity -> {
                        // More sold than bought, must process remainder as 30 day or section 104
                        val percentage = buyTransaction.quantity.toDouble() / t.quantity.toDouble()
                        val sameDaySellPrice = t.price * percentage
                        sellTransactions[i].quantity -= buyTransaction.quantity
                        sellTransactions[i].price -= sameDaySellPrice
                        pl = BigDecimal(sameDaySellPrice - buyTransaction.price).setScale(2, RoundingMode.HALF_EVEN).toString()
                        buyTransactions.removeAt(index)
                    }
                    // t.quantity < buyTransaction.quantity
                    else -> {
                        // More bought than sold
                        val percentage = t.quantity.toDouble() / buyTransaction.quantity.toDouble()
                        val buyingCost = buyTransaction.price * percentage
                        buyTransactions[index].quantity -= t.quantity
                        buyTransactions[index].price -= buyingCost
                        pl = BigDecimal(t.price - buyingCost).setScale(2, RoundingMode.HALF_EVEN).toString()
                        sellTransactionsToRemove.add(0, i)
                    }
                }
                pl.let {
                    if (pl.toDouble() >= 0) {
                        message += "Profit = £$pl."
                        profit += pl.toDouble()
                    } else {
                        message += "Loss = £$pl."
                        loss += pl.toDouble()
                    }
                }
                println(message)
            }
        }
        for (i in sellTransactionsToRemove) sellTransactions.removeAt(i)
        processBedBreakfastTransactions(sellTransactions, buyTransactions)
    }
}