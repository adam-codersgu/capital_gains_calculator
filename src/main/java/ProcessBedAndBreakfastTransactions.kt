import model.OutstandingTransactions
import model.Transaction
import java.math.BigDecimal
import java.math.RoundingMode

class ProcessBedAndBreakfastTransactions : ProcessTransaction {

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
    override fun process(outstandingTransactions: OutstandingTransactions): OutstandingTransactions {
        this.outstandingTransactions = outstandingTransactions

        for (sellTransaction in outstandingTransactions.sellTransactions) {
            // Look for any suitable buy transactions that occurred up to 30 days
            // after the disposal.
            val maxDate = sellTransaction.date.plusDays(31)
            val buyTransaction = outstandingTransactions.buyTransactions.find {
                it.date.isAfter(sellTransaction.date) && it.date.isBefore(maxDate)
            }

            // A buy transaction occurred on the same day as the sell transaction
            if (buyTransaction != null) reportBedAndBreakfastTransaction(buyTransaction, sellTransaction)
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
        val output = "BED AND BREAKFAST Sell transaction(s) (IDs " + sellTransaction.transactionIDs +
                ") identified with buy transaction(s) (IDs " + buyTransaction.transactionIDs +
                "). " + sellTransaction.quantity + " shares sold for an average price of $averageSellPrice" +
                "GBP on " + sellTransaction.date + " identified with " + buyTransaction.quantity +
                " shares bought on " + buyTransaction.date + " for an average price of $$averageBuyPrice" +
                " GBP. $profitOrLossMessage"
        println(output)
    }

    private fun calculateProfitLoss(buyTransaction: Transaction, sellTransaction: Transaction): Double {
        // TODO
        return 1.11
    }



    fun processBedBreakfastTransactions(sellTransactions: MutableList<Transaction>, buyTransactions: MutableList<Transaction>) {
        val sellTransactionsToRemove = mutableListOf<Int>()
        for ((i, t) in sellTransactions.withIndex()) {
            val maxDate = t.date.plusDays(31)
            var index = buyTransactions.indexOfFirst {
                it.date.isAfter(t.date) && it.date.isBefore(maxDate)
            }
            do {
                if (index != -1) {
                    val buyTransaction = buyTransactions[index]

                    var pl: String? = null; var message = ""
                    when {
                        t.quantity == buyTransaction.quantity -> {
                            pl = BigDecimal(t.price - buyTransaction.price).setScale(2, RoundingMode.HALF_EVEN).toString()
                            buyTransactions.removeAt(index)
                            sellTransactionsToRemove.add(0, i)
                            index = -1
                        }
                        t.quantity > buyTransaction.quantity -> {
                            // More sold than bought, must process remainder as 30 day or section 104
                            val percentage = buyTransaction.quantity.toDouble() / t.quantity.toDouble()
                            val sellPrice = t.price * percentage
                            t.quantity -= buyTransaction.quantity
                            t.price -= sellPrice
                            sellTransactions[i] = t
                            pl = BigDecimal(sellPrice - buyTransaction.price).setScale(2, RoundingMode.HALF_EVEN).toString()
                            buyTransactions.removeAt(index)
                            index = buyTransactions.indexOfFirst {
                                it.date.isAfter(t.date) && it.date.isBefore(maxDate)
                            }
                        }
                        t.quantity < buyTransaction.quantity -> {
                            // More bought than sold
                            val percentage = t.quantity.toDouble() / buyTransaction.quantity.toDouble()
                            val buyingCost = buyTransaction.price * percentage
                            buyTransactions[index].quantity -= t.quantity
                            buyTransactions[index].price -= buyingCost
                            pl = BigDecimal(t.price - buyingCost).setScale(2, RoundingMode.HALF_EVEN).toString()
                            sellTransactionsToRemove.add(0, i)
                            index = -1
                        }
                    }
                    if (pl != null) {
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
            } while (index != -1)
        }
        for (i in sellTransactionsToRemove) sellTransactions.removeAt(i)
        val allTransactions = sellTransactions + buyTransactions
        processSection104Transactions(allTransactions.sortedBy { it.date }.toMutableList())
    }
}