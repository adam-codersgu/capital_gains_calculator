
import model.OutstandingTransactions
import model.Section104
import model.Transaction
import kotlin.math.min

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

        val proportionOfUnitsMessage = if (buyTransaction.initialQuantity != buyTransaction.remainingQuantity) {
            "${buyTransaction.remainingQuantity} of the ${buyTransaction.initialQuantity}"
        } else "All ${buyTransaction.initialQuantity}"

        println("SECTION 104 ADDITION - $proportionOfUnitsMessage units purchased on " +
                "${buyTransaction.getTransactionDateAndIdsMessage()}were added to the Section 104 holding. " +
                "The average unit price of the purchased shares was ${buyTransaction.getAverageUnitPrice()} GBP.")
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
        // If the Section 104 holding is empty then add the disposal to the outstanding transactions list.
        if (section104.quantity == 0) {
            outstandingTransactions.sellTransactions.add(sellTransaction)
            return
        }

        val summary = "SECTION 104 DISPOSAL - " + getProportionOfUnitsSoldMessage(section104.quantity, sellTransaction) +
                getProportionOfUnitsBoughtMessage(section104, sellTransaction) + " units in the Section 104 holding. " +
                "The average unit price of the Section 104 holding was " + section104.getAverageUnitPrice() +
                " GBP " + getUnitPriceOfSoldSharesMessage(sellTransaction)

        val disposalQuantity = min(sellTransaction.remainingQuantity, section104.quantity)
        val profitOrLoss = (sellTransaction.getAverageUnitPrice() - section104.getAverageUnitPrice()) *
                disposalQuantity

        if (sellTransaction.remainingQuantity > section104.quantity) {
            sellTransaction.remainingQuantity -= disposalQuantity
            sellTransaction.remainingValue -= (sellTransaction.getAverageUnitPrice() * disposalQuantity)
            outstandingTransactions.sellTransactions.add(sellTransaction)
        }

        if (profitOrLoss >= 0) {
            println(summary + "Profit = £${roundAmountToTwoDecimalPlaces(profitOrLoss)}.")
            outstandingTransactions.totalGains += profitOrLoss
        } else {
            println(summary + "Loss = £${roundAmountToTwoDecimalPlaces(profitOrLoss)}.")
            outstandingTransactions.totalLoss += profitOrLoss
        }

        if (section104.quantity - disposalQuantity == 0) section104.clear()
        else {
            section104.price -= (section104.getAverageUnitPrice() * disposalQuantity)
            section104.quantity -= disposalQuantity
        }
    }

    private fun getProportionOfUnitsBoughtMessage(section104: Section104, sellTransaction: Transaction) : String {
        return if (sellTransaction.remainingQuantity < section104.quantity) {
            "${sellTransaction.remainingQuantity} of the ${section104.quantity}"
        } else {
            "all ${section104.quantity}"
        }
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