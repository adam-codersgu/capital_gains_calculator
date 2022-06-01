
import model.OutstandingTransactions
import model.Section104
import model.Transaction
import java.math.BigDecimal
import java.math.RoundingMode

class ProcessSection104Transactions(outstandingTransactions: OutstandingTransactions) : ProcessTransaction(outstandingTransactions) {

    private var section104 = Section104()

    /**
     * Match all disposals with previously purchased stock (the Section 104 holding).
     * Section 104 disposals have third priority (after Same Day and Bed and Breakfast disposals)
     * when matching transactions for an HMRC Capital Gains report.
     *
     * @return An OutstandingTransactions object detailing the total profit, total loss,
     * and lists of outstanding Buy and Sell Transaction objects after all same day and
     * bed and breakfast transactions have been processed.
     */
    override fun process(): OutstandingTransactions {
        // Group all the transactions and sort them in date order
        val allTransactions = (outstandingTransactions.sellTransactions + outstandingTransactions.buyTransactions)
            .sortedBy { it.date }
        // Clear the existing lists of outstanding transactions
        outstandingTransactions.buyTransactions.clear()
        outstandingTransactions.sellTransactions.clear()

        for (transaction in allTransactions) {
            // Add buy transactions to the Section 104 holding
            if (transaction.direction == "Buy") addTransactionToSection104Holding(transaction)
            // Process sell transactions
            else matchDisposalWithSection104Holding(transaction)
        }
        printSummaryOfSection104()

        return outstandingTransactions
    }

    /**
     * Add purchased shares to the Section 104 holding. If there are outstanding sell transactions, then
     * purchased shares may need to be matched with those before being added to the Section 104 holding.
     * Also, print a summary of the shares added to the Section 104 holding to the console.
     *
     * @param buyTransaction The Transaction object associated with the purchases for a given day
     */
    private fun addTransactionToSection104Holding(buyTransaction: Transaction) {
        // Need to check there are not outstanding disposals to be matched
        // Otherwise the purchased shares should be used to close a short position rather before
        // being added to the Section 104 holding
        if (outstandingTransactions.sellTransactions.isNotEmpty()) {
            val remainingBuyTransaction = addPurchasedSharesToOutstandingTransactions(buyTransaction) ?: return
            buyTransaction.quantity = remainingBuyTransaction.quantity
            buyTransaction.price = remainingBuyTransaction.price
        }

        section104.transactionIDs.addAll(buyTransaction.transactionIDs)
        section104.quantity += buyTransaction.quantity
        section104.price += buyTransaction.price
        val output = "SECTION 104 " + buyTransaction.quantity + " shares priced at " +
                buyTransaction.price / buyTransaction.quantity + " GBP added to the " +
                "Section 104 holding on " + buyTransaction.date + " (transaction ID(s) " +
                buyTransaction.transactionIDs + ")"
        println(output)
    }

    /**
     * Determine whether any purchased shares from a buy transaction must be added to the list
     * of outstanding transaction to be matched with outstanding sell transactions via the
     * acquisition following disposal rule. If all outstanding sell transactions are accounted for,
     * then the remaining purchased shares can be added to the Section 104 holding.
     *
     * @param buyTransaction The Transaction object associated with the purchases for a given day
     * @return A Transaction object containing purchased shares that can be added to the Section 104
     * holding. If no purchased shares remain after being matched with outstanding sold shares, then
     * null will be returned.
     */
    private fun addPurchasedSharesToOutstandingTransactions(buyTransaction: Transaction): Transaction? {
        var quantityOfOutstandingSoldShares = 0
        quantityOfOutstandingSoldShares += outstandingTransactions.sellTransactions.sumOf { it.quantity }
        quantityOfOutstandingSoldShares -= outstandingTransactions.buyTransactions.sumOf { it.quantity }

        return when {
            // No sold shares outstanding. Can add the full buy transaction to the Section 104 holding
            quantityOfOutstandingSoldShares == 0 -> buyTransaction
            // If there are sufficient outstanding sell shares, then add the full buy transaction
            quantityOfOutstandingSoldShares >= buyTransaction.quantity -> {
                outstandingTransactions.buyTransactions.add(buyTransaction)
                return null
            }
            // Else add only the required buy transaction shares and transfer the rest to the Section 104 holding
            else -> {
                val averagePurchasePrice = buyTransaction.price / buyTransaction.quantity
                val priceOfOutstandingShares = BigDecimal(averagePurchasePrice * quantityOfOutstandingSoldShares)
                    .setScale(2, RoundingMode.HALF_EVEN).toDouble()
                val outstandingBuyTransaction = buyTransaction.copy(
                    quantity = quantityOfOutstandingSoldShares,
                    price = priceOfOutstandingShares
                )
                outstandingTransactions.buyTransactions.add(outstandingBuyTransaction)
                buyTransaction.quantity -= quantityOfOutstandingSoldShares
                buyTransaction.price -= priceOfOutstandingShares
                buyTransaction
            }
        }
    }

    /**
     * Match a sell transaction with shares in the Section 104 holding. Also, calculate the profit
     * or loss incurred from the disposal.
     *
     * @param sellTransaction The Transaction object associated with the disposals for a given day
     */
    private fun matchDisposalWithSection104Holding(sellTransaction: Transaction) {
        // If the Section 104 holding is empty then add the disposal to the OutstandingTransactions object.
        if (section104.quantity == 0) {
            outstandingTransactions.sellTransactions.add(sellTransaction)
            return
        }

        // Cannot match more shares than exist in the Section 104 holding
        val disposalQuantityToBeMatched = if (sellTransaction.quantity >= section104.quantity) section104.quantity
        else sellTransaction.quantity

        val averageDisposalPrice = sellTransaction.price / sellTransaction.quantity
        val averagePurchasePrice = section104.price / section104.quantity

        val summary = "SECTION 104 $disposalQuantityToBeMatched shares from sell transaction (ID(s) " +
                sellTransaction.transactionIDs + ") dated " + sellTransaction.date + " identified with " +
                "the Section 104 holding. The average price of the sold shares was $averageDisposalPrice GBP, " +
                "and the average price of the shares in the Section 104 holding was $averagePurchasePrice GBP. "

        val totalDisposalPrice = averageDisposalPrice * disposalQuantityToBeMatched
        val totalPurchasePrice = averagePurchasePrice * disposalQuantityToBeMatched
        val profitOrLoss = BigDecimal(totalDisposalPrice - totalPurchasePrice)
            .setScale(2, RoundingMode.HALF_EVEN).toDouble()
        printTransactionSummary(profitOrLoss, summary)

        // There were insufficient shares in the Section 104 holding to match the full disposal
        if (sellTransaction.quantity > section104.quantity) {
            sellTransaction.quantity -= section104.quantity
            sellTransaction.price -= totalDisposalPrice
            outstandingTransactions.sellTransactions.add(sellTransaction)
        }

        // Remove the matched shares from the Section 104 holding
        section104.quantity -= disposalQuantityToBeMatched
        section104.price -= totalPurchasePrice

        // If all shares in the Section 104 holding have been sold, then reset the Section104 object
        if (section104.quantity == 0) section104 = Section104()
    }

    // TODO - TEST ME
    private fun printTransactionSummary(profitOrLoss: Double, summary: String) {
        val profitOrLossSummary: String
        if (profitOrLoss >= 0) {
            profitOrLossSummary = "Profit = £$profitOrLoss."
            outstandingTransactions.totalProfit += profitOrLoss
        } else {
            profitOrLossSummary = "Loss = £$profitOrLoss."
            outstandingTransactions.totalLoss += profitOrLoss
        }
        println(summary + profitOrLossSummary)
    }

    // TODO - TEST ME
    private fun printSummaryOfSection104() {
        if (section104.quantity != 0) {
            println("\nCARRY FORWARD TO NEXT TAX TEAR There are purchased shares remaining in the Section 104 holding. " +
                    "These shares may need to be carried forward to be matched with future disposals. \n" +
                    "Summary of outstanding Section 104 holding: $section104\n")
        }
    }
}