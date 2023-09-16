
import model.OutstandingTransactions
import model.Section104
import model.Transaction
import java.math.BigDecimal
import java.math.RoundingMode

class ProcessSection104Transactions(outstandingTransactions: OutstandingTransactions,
                                    private val section104: Section104 = Section104())
    : ProcessTransaction(outstandingTransactions) {

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
        val allTransactions = (outstandingTransactions.sellTransactions + outstandingTransactions.buyTransactions)
            .sortedBy { it.date }
        outstandingTransactions.buyTransactions.clear()
        outstandingTransactions.sellTransactions.clear()

        for (transaction in allTransactions) {
            if (transaction.type == "Buy") addTransactionToSection104Holding(transaction)
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
            buyTransaction.remainingQuantity = remainingBuyTransaction.remainingQuantity
            buyTransaction.remainingValue = remainingBuyTransaction.remainingValue
        }

        section104.transactionIDs.addAll(buyTransaction.transactionIds)
        section104.quantity += buyTransaction.remainingQuantity
        section104.price += buyTransaction.remainingValue
        val output = "SECTION 104 " + buyTransaction.remainingQuantity + " shares priced at " +
                buyTransaction.remainingValue / buyTransaction.remainingQuantity + " GBP added to the " +
                "Section 104 holding on " + buyTransaction.date +
                " (${buyTransaction.getTransactionIdsMessage()})"
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
        quantityOfOutstandingSoldShares += outstandingTransactions.sellTransactions.sumOf { it.remainingQuantity }
        quantityOfOutstandingSoldShares -= outstandingTransactions.buyTransactions.sumOf { it.remainingQuantity }

        return when {
            // No sold shares outstanding. Can add the full buy transaction to the Section 104 holding
            quantityOfOutstandingSoldShares == 0 -> buyTransaction
            // If there are sufficient outstanding sell shares, then add the full buy transaction
            quantityOfOutstandingSoldShares >= buyTransaction.remainingQuantity -> {
                outstandingTransactions.buyTransactions.add(buyTransaction)
                return null
            }
            // Else add only the required buy transaction shares and transfer the rest to the Section 104 holding
            else -> {
                val valueOfOutstandingShares = buyTransaction.getAverageUnitPrice() *
                        quantityOfOutstandingSoldShares
                val outstandingBuyTransaction = buyTransaction.copy(
                    remainingQuantity = quantityOfOutstandingSoldShares,
                    remainingValue = valueOfOutstandingShares
                )
                outstandingTransactions.buyTransactions.add(outstandingBuyTransaction)
                buyTransaction.remainingQuantity -= quantityOfOutstandingSoldShares
                buyTransaction.remainingValue -= valueOfOutstandingShares
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
        val disposalQuantityToBeMatched = if (sellTransaction.remainingQuantity >= section104.quantity) section104.quantity
        else sellTransaction.remainingQuantity

        val averageDisposalPrice = sellTransaction.remainingValue / sellTransaction.remainingQuantity
        val averagePurchasePrice = section104.price / section104.quantity

        val summary = "SECTION 104 $disposalQuantityToBeMatched shares from sell " +
                sellTransaction.getTransactionIdsMessage() + " dated " + sellTransaction.date + " identified with " +
                "the Section 104 holding. The average price of the sold shares was $averageDisposalPrice GBP, " +
                "and the average price of the shares in the Section 104 holding was $averagePurchasePrice GBP. "

        val totalDisposalPrice = averageDisposalPrice * disposalQuantityToBeMatched
        val totalPurchasePrice = averagePurchasePrice * disposalQuantityToBeMatched
        val profitOrLoss = totalDisposalPrice - totalPurchasePrice
        printTransactionSummary(profitOrLoss, summary)

        // There were insufficient shares in the Section 104 holding to match the full disposal
        if (sellTransaction.remainingQuantity > section104.quantity) {
            sellTransaction.remainingQuantity -= section104.quantity
            sellTransaction.remainingValue -= totalDisposalPrice
            outstandingTransactions.sellTransactions.add(sellTransaction)
        }

        // Remove the matched shares from the Section 104 holding
        section104.quantity -= disposalQuantityToBeMatched
        section104.price -= totalPurchasePrice

        // If all shares in the Section 104 holding have been sold, then reset the Section104 object
        if (section104.quantity == 0) section104.clear()
    }

    /**
     * Prints a summary of a disposal that is matched against shares in the Section 104 holding.
     * The transaction summary includes the profit or loss resulting from the disposal.
     *
     * @param profitOrLoss The profit or loss incurred from the disposal.
     * @param summary The transaction summary (excluding the profit/loss summary)
     */
    private fun printTransactionSummary(profitOrLoss: Double, summary: String) {
        val profitOrLossRounded = BigDecimal(profitOrLoss)
            .setScale(2, RoundingMode.HALF_EVEN).toString()
        val profitOrLossSummary: String
        if (profitOrLoss >= 0) {
            profitOrLossSummary = "Profit = £$profitOrLossRounded."
            outstandingTransactions.totalGains += profitOrLoss
        } else {
            profitOrLossSummary = "Loss = £$profitOrLossRounded."
            outstandingTransactions.totalLoss += profitOrLoss
        }
        println(summary + profitOrLossSummary)
    }

    /**
     * Prints a summary of shares remaining in the Section 104 holding after all suitable disposals
     * have been processed. Any shares remaining in the Section 104 holding will likely need to be
     * carried forward to future tax years and matched with future disposals.
     */
    private fun printSummaryOfSection104() {
        if (section104.quantity != 0) {
            println("\nCARRY FORWARD TO NEXT TAX TEAR There are purchased shares remaining in the Section 104 holding. " +
                    "These shares may need to be carried forward to be matched with future disposals. \n" +
                    "Summary of outstanding Section 104 holding: $section104\n")
        }
    }
}