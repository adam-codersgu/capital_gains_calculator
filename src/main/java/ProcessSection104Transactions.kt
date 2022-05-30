
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
            else {
                // If the Section 104 holding is not empty, then attempt to match the disposal
                if (section104.quantity > 0) matchDisposalWithSection104Holding(transaction)
                // Else add the disposal to the OutstandingTransactions object because it cannot be
                // matched with the Section 104 holding.
                else addDisposalToOutstandingTransactions(transaction)
            }
        }
        printSummaryOfSection104()

        return outstandingTransactions
    }

    // TODO - TEST ME
    /**
     * Add purchased shares to the Section 104 holding. Also, print a summary of the
     * shares added to the Section 104 holding to the console.
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
        val message = "SECTION 104 " + buyTransaction.quantity + " shares priced at " +
                buyTransaction.price / buyTransaction.quantity + " GBP added to the " +
                "Section 104 holding on " + buyTransaction.date + " (transaction ID(s) " +
                buyTransaction.transactionIDs + ")"
        println(message)
    }

    // TODO - TEST ME
    // RETURNS NULL IF ALL PURCHASED SHARES ADDED TO OUTSTANDING TRANSACTION
    private fun addPurchasedSharesToOutstandingTransactions(buyTransaction: Transaction): Transaction? {
        var quantityOfOutstandingSoldShares = 0
        for (transaction in outstandingTransactions.sellTransactions) {
            quantityOfOutstandingSoldShares += transaction.quantity
        }
        for (transaction in outstandingTransactions.buyTransactions) {
            quantityOfOutstandingSoldShares -= transaction.quantity
        }
        // If there are sufficient outstanding sell shares, then add the full buy transaction
        if (quantityOfOutstandingSoldShares >= buyTransaction.quantity) {
            outstandingTransactions.buyTransactions.add(buyTransaction)
            return null
        }
        // Else add only the required buy transaction shares and transfer the rest to the Section 104 holding
        else {
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
            return buyTransaction
        }
    }

    // TODO - TEST ME
    private fun matchDisposalWithSection104Holding(sellTransaction: Transaction) {
        // Cannot match more shares than exist in the Section 104 holding
        val disposalQuantityToBeMatched = if (sellTransaction.quantity >= section104.quantity) section104.quantity
        else sellTransaction.quantity

        val averageDisposalPrice = sellTransaction.price / sellTransaction.quantity
        val averagePurchasePrice = section104.price / section104.quantity
        var message = "SECTION 104 $disposalQuantityToBeMatched shares from sell transaction (ID(s) " +
                sellTransaction.transactionIDs + ") dated " + sellTransaction.date + " identified with " +
                "the Section 104 holding. The average price of the sold shares was $averageDisposalPrice GBP, " +
                "and the average price of the shares in the Section 104 holding was $averagePurchasePrice GBP. "

        val totalDisposalPrice = averageDisposalPrice * disposalQuantityToBeMatched
        val totalPurchasePrice = averagePurchasePrice * disposalQuantityToBeMatched
        val profitOrLoss = BigDecimal(totalDisposalPrice - totalPurchasePrice)
            .setScale(2, RoundingMode.HALF_EVEN).toDouble()

        // There were insufficient shares in the Section 104 holding to match the full disposal
        if (sellTransaction.quantity >= section104.quantity) {
            addDisposalToOutstandingTransactions(sellTransaction)
        }

        // Remove the matched shares from the Section 104 holding
        section104.quantity -= sellTransaction.quantity
        section104.price -= totalPurchasePrice

        // If all shares in the Section 104 holding have been sold, then reset the Section104 object
        if (section104.quantity == 0) section104 = Section104()

        if (profitOrLoss >= 0) {
            message += "Profit = £$profitOrLoss."
            outstandingTransactions.totalProfit += profitOrLoss
        } else {
            message += "Loss = £$profitOrLoss."
            outstandingTransactions.totalLoss += profitOrLoss
        }
        println(message)
    }

    // TODO - TEST ME
    private fun addDisposalToOutstandingTransactions(sellTransaction: Transaction) {
        // If some disposed shares have been matched, then remove those shares from the transaction
        if (section104.quantity != 0) {
            val averageDisposalPrice = sellTransaction.price / sellTransaction.quantity
            val totalDisposalPrice = BigDecimal(averageDisposalPrice * section104.quantity)
                .setScale(2, RoundingMode.HALF_EVEN).toDouble()
            sellTransaction.quantity -= section104.quantity
            sellTransaction.price -= totalDisposalPrice
        }
        outstandingTransactions.sellTransactions.add(sellTransaction)
    }

    // TODO - TEST ME
    private fun printSummaryOfSection104() {
        if (section104.quantity != 0) {
            println("There are purchased shares remaining in the Section 104 holding. " +
                    "These shares will likely need to be carried forward to future tax years " +
                    "and matched with future disposals. \n " +
                    "Summary of outstanding Section 104 holding: $section104")
        }
    }
}