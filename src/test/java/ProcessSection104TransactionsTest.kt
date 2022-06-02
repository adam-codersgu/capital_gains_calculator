
import model.OutstandingTransactions
import model.Section104
import model.Transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProcessSection104TransactionsTest {

    var outstandingTransactions = OutstandingTransactions()
    var section104 = Section104()

    /**
     * Test Transaction object scenarios:
     *  BUY_TRANSACTION_1 and SELL_TRANSACTION_1 are one day apart, same size, same price
     *  BUY_TRANSACTION_2 and SELL_TRANSACTION_2 are different days, different prices (profitable).
     *  BUY_TRANSACTION_3 and SELL_TRANSACTION_3 are different days, different prices (loss). The
     *      quantity of sold shares is greater than the quantity of purchased shares.
     */
    companion object {
        private val BUY_TRANSACTION_1 = Transaction(
            mutableListOf("placeholder_id"),
            LocalDate.now(),
            "Buy",
            10,
            100.00
        )
        private val BUY_TRANSACTION_2 = BUY_TRANSACTION_1.copy(
            date = LocalDate.now().minusDays(2),
            quantity = 25,
            price = 467.20
        )
        private val BUY_TRANSACTION_3 = BUY_TRANSACTION_1.copy(
            date = LocalDate.now().minusDays(3),
            quantity = 15,
            price = 120.10
        )

        private val SELL_TRANSACTION_1 = BUY_TRANSACTION_1.copy(
            date = LocalDate.now().minusDays(1),
            direction = "Sell"
        )
        private val SELL_TRANSACTION_2 = BUY_TRANSACTION_2.copy(
            direction = "Sell",
            date = LocalDate.now().minusDays(3),
            price = 485.23
        )
        private val SELL_TRANSACTION_3 = BUY_TRANSACTION_3.copy(
            direction = "Sell",
            date = LocalDate.now().minusDays(5),
            quantity = 20,
            price = 143.04
        )
    }

    /**
     * Reset the Section 104 holding and list of outstanding transaction objects after each test.
     */
    @AfterEach
    fun teardown() {
        section104 = Section104()
        outstandingTransactions.buyTransactions.clear()
        outstandingTransactions.sellTransactions.clear()
    }

    /**
     * Match all disposals with previously purchased stock (the Section 104 holding).
     * Section 104 disposals have third priority (after Same Day and Bed and Breakfast disposals)
     * when matching transactions for an HMRC Capital Gains report.
     */
    @Test
    fun processTest() {
        // Populate the lists of buy and sell Transaction objects
        outstandingTransactions.buyTransactions.addAll(listOf(BUY_TRANSACTION_1, BUY_TRANSACTION_2, BUY_TRANSACTION_3))
        outstandingTransactions.sellTransactions.addAll(listOf(SELL_TRANSACTION_1, SELL_TRANSACTION_2, SELL_TRANSACTION_3))

        // Group all the transactions and sort them in date order
        val allTransactions = (outstandingTransactions.sellTransactions + outstandingTransactions.buyTransactions)
            .sortedBy { it.date }
        for ((index, transaction) in allTransactions.withIndex()) {
            if (index < allTransactions.size - 1) assertTrue(transaction.date <= allTransactions[index + 1].date)
        }

        // Clear the existing lists of outstanding transactions
        outstandingTransactions.buyTransactions.clear()
        outstandingTransactions.sellTransactions.clear()
        assertEquals(outstandingTransactions.buyTransactions.size, 0)
        assertEquals(outstandingTransactions.sellTransactions.size, 0)

        for (transaction in allTransactions) {
            // Add buy transactions to the Section 104 holding
            if (transaction.direction == "Buy") assertEquals(transaction.direction, "Buy")
            // Process sell transactions
            else assertEquals(transaction.direction, "Sell")
        }
    }

    /**
     * Add purchased shares to the Section 104 holding. If there are outstanding sell transactions, then
     * purchased shares may need to be matched with those before being added to the Section 104 holding.
     *
     * @param scenario The testing scenario number, which is used to select the appropriate test data.
     *  Scenario 1 - The list of outstanding sell transactions is empty.
     *  Scenario 2 - The list of outstanding sell transactions contains items.
     */
    @ParameterizedTest
    @ValueSource(ints = [1, 2])
    fun addTransactionToSection104HoldingTest(scenario: Int) {
        val buyTransaction = BUY_TRANSACTION_1.copy()
        if (scenario == 2) {
            outstandingTransactions.sellTransactions.addAll(listOf(SELL_TRANSACTION_1, SELL_TRANSACTION_2, SELL_TRANSACTION_3))
        }

        // Need to check there are not outstanding disposals to be matched
        // Otherwise the purchased shares should be used to close a short position rather before
        // being added to the Section 104 holding
        if (outstandingTransactions.sellTransactions.isNotEmpty()) {
            assertNotEquals(outstandingTransactions.sellTransactions.size, 0)

            // Mock the addPurchasedSharesToOutstandingTransactions method by returning
            // a Transaction with 5 fewer shares and a price 10 units lower
            val remainingBuyTransaction = buyTransaction.copy(
                quantity = buyTransaction.quantity - 5,
                price = buyTransaction.price - 10
            )

            buyTransaction.quantity = remainingBuyTransaction.quantity
            assertEquals(buyTransaction.quantity, remainingBuyTransaction.quantity)
            buyTransaction.price = remainingBuyTransaction.price
            assertEquals(buyTransaction.price, remainingBuyTransaction.price)
        }

        section104.transactionIDs.addAll(buyTransaction.transactionIDs)
        assertEquals(section104.transactionIDs, buyTransaction.transactionIDs)
        section104.quantity += buyTransaction.quantity
        assertEquals(section104.quantity, buyTransaction.quantity)
        section104.price += buyTransaction.price
        assertEquals(section104.price, buyTransaction.price)
    }

    /**
     * Determine whether any purchased shares from a buy transaction must be added to the list
     * of outstanding transaction to be matched with outstanding sell transactions via the
     * acquisition following disposal rule. If all outstanding sell transactions are accounted for,
     * then the remaining purchased shares can be added to the Section 104 holding.
     *
     * @param scenario The testing scenario number, which is used to select the appropriate test data.
     *  Scenario 1 - The lists of outstanding buy and sell transactions are empty.
     *  Scenario 2 - The list of outstanding sell transactions contains the same amount of shares as the
     *      supplied buy transaction. The list of outstanding buy transactions is empty.
     *  Scenario 3 - The list of outstanding sell transactions contains fewer shares than the supplied
     *      buy transaction. The list of outstanding buy transactions is empty.
     *  Scenario 4 - The lists of outstanding sell and buy transactions contain an equal number of shares.
     *  Scenario 5 - The list of outstanding sell transactions contains a greater quantity of shares than
     *      the list of outstanding buy transactions.
     */
    @ParameterizedTest
    @ValueSource(ints = [1, 2, 3, 4, 5])
    fun addPurchasedSharesToOutstandingTransactionsTest(scenario: Int) {
        val buyTransaction = BUY_TRANSACTION_1.copy()

        when (scenario) {
            2 -> outstandingTransactions.sellTransactions.addAll(listOf(SELL_TRANSACTION_1))
            3 -> {
                buyTransaction.quantity += buyTransaction.quantity
                buyTransaction.price += buyTransaction.price
                outstandingTransactions.sellTransactions.addAll(listOf(SELL_TRANSACTION_1))
            }
            4 -> {
                outstandingTransactions.buyTransactions.addAll(listOf(BUY_TRANSACTION_2))
                outstandingTransactions.sellTransactions.addAll(listOf(SELL_TRANSACTION_2))
            }
            5 -> {
                outstandingTransactions.buyTransactions.addAll(listOf(BUY_TRANSACTION_3))
                outstandingTransactions.sellTransactions.addAll(listOf(SELL_TRANSACTION_3))
            }
        }

        var quantityOfOutstandingSoldShares = outstandingTransactions.sellTransactions.sumOf { it.quantity }

        var testQuantityOfOutstandingSoldShares = 0
        for (transaction in outstandingTransactions.sellTransactions) {
            testQuantityOfOutstandingSoldShares += transaction.quantity
        }
        assertEquals(quantityOfOutstandingSoldShares, testQuantityOfOutstandingSoldShares)

        quantityOfOutstandingSoldShares -= outstandingTransactions.buyTransactions.sumOf { it.quantity }
        for (transaction in outstandingTransactions.buyTransactions) {
            testQuantityOfOutstandingSoldShares -= transaction.quantity
        }
        assertEquals(quantityOfOutstandingSoldShares, testQuantityOfOutstandingSoldShares)

        when {
            // No sold shares outstanding. Can add the full buy transaction to the Section 104 holding
            quantityOfOutstandingSoldShares == 0 -> assertEquals(testQuantityOfOutstandingSoldShares, 0)
            // If there are sufficient outstanding sell shares, then add the full buy transaction
            quantityOfOutstandingSoldShares >= buyTransaction.quantity -> {
                assertTrue(outstandingTransactions.buyTransactions.add(buyTransaction))
            }
            // Else add only the required buy transaction shares and transfer the rest to the Section 104 holding
            else -> {
                val originalBuyTransaction = buyTransaction.copy()

                val averagePurchasePrice = buyTransaction.price / buyTransaction.quantity
                val priceOfOutstandingShares = BigDecimal(averagePurchasePrice * quantityOfOutstandingSoldShares)
                    .setScale(2, RoundingMode.HALF_EVEN).toDouble()
                val outstandingBuyTransaction = buyTransaction.copy(
                    quantity = quantityOfOutstandingSoldShares,
                    price = priceOfOutstandingShares
                )
                assertTrue(outstandingTransactions.buyTransactions.add(outstandingBuyTransaction))
                buyTransaction.quantity -= quantityOfOutstandingSoldShares
                assertEquals(originalBuyTransaction.quantity, buyTransaction.quantity +
                        outstandingBuyTransaction.quantity)
                buyTransaction.price -= priceOfOutstandingShares
                assertEquals(originalBuyTransaction.price, buyTransaction.price +
                        outstandingBuyTransaction.price)
            }
        }
    }

    /**
     * Match a sell transaction with shares in the Section 104 holding. Also, calculate the profit
     * or loss incurred from the disposal.
     *
     * @param scenario The testing scenario number, which is used to select the appropriate test data.
     *  Scenario 1 - The Section 104 holding is empty.
     *  Scenario 2 - The Section 104 holding contains an equal number of shares and price as the
     *      sell transaction.
     *  Scenario 3 - The Section 104 holding contains a greater number of shares and greater price than
     *      the sell transaction.
     */
    @ParameterizedTest
    @ValueSource(ints = [1, 2, 3])
    fun matchDisposalWithSection104HoldingTest(scenario: Int) {
        val sellTransaction = SELL_TRANSACTION_1.copy()

        when (scenario) {
            2 -> section104 = Section104(
                transactionIDs = BUY_TRANSACTION_1.transactionIDs,
                quantity = BUY_TRANSACTION_1.quantity,
                price = BUY_TRANSACTION_1.price
            )
            3 -> section104 = Section104(
                transactionIDs = BUY_TRANSACTION_2.transactionIDs,
                quantity = BUY_TRANSACTION_2.quantity,
                price = BUY_TRANSACTION_2.price
            )
        }

        // If the Section 104 holding is empty then add the disposal to the OutstandingTransactions object.
        if (section104.quantity == 0) {
            assertTrue(outstandingTransactions.sellTransactions.add(sellTransaction))
            return
        } else assertTrue(section104.quantity > 0)

        // Cannot match more shares than exist in the Section 104 holding
        val disposalQuantityToBeMatched = if (sellTransaction.quantity >= section104.quantity) section104.quantity
        else sellTransaction.quantity

        if (sellTransaction.quantity >= section104.quantity) assertEquals(section104.quantity, disposalQuantityToBeMatched)
        else assertEquals(sellTransaction.quantity, disposalQuantityToBeMatched)

        val averageDisposalPrice = sellTransaction.price / sellTransaction.quantity
        val averagePurchasePrice = section104.price / section104.quantity

        val totalDisposalPrice = averageDisposalPrice * disposalQuantityToBeMatched
        val totalPurchasePrice = averagePurchasePrice * disposalQuantityToBeMatched
        val profitOrLoss = totalDisposalPrice - totalPurchasePrice

        assertEquals(
            ((sellTransaction.price / sellTransaction.quantity) * disposalQuantityToBeMatched)
                    - ((section104.price / section104.quantity) * disposalQuantityToBeMatched),
            profitOrLoss
        )

        // There were insufficient shares in the Section 104 holding to match the full disposal
        if (sellTransaction.quantity > section104.quantity) {
            val originalSellTransaction = sellTransaction.copy()
            sellTransaction.quantity -= section104.quantity
            assertEquals(originalSellTransaction.quantity, section104.quantity + sellTransaction.quantity)
            sellTransaction.price -= totalDisposalPrice
            assertEquals(originalSellTransaction.price, totalDisposalPrice + sellTransaction.price)
            assertTrue(outstandingTransactions.sellTransactions.add(sellTransaction))
        }

        val originalSection104 = section104.copy()
        // Remove the matched shares from the Section 104 holding
        section104.quantity -= disposalQuantityToBeMatched
        assertEquals(originalSection104.quantity, section104.quantity + disposalQuantityToBeMatched)
        section104.price -= totalPurchasePrice
        assertEquals(originalSection104.price, section104.price + totalPurchasePrice)
    }

    /**
     * Assess the ability of the matchDisposalWithSection104Holding method to generate an appropriately
     * formatted transaction summary for disposals matched with shares in the Section 104 holding.
     *
     * Acceptance criteria:
     *  The transaction summary should be formatted as shown below:
     *      SECTION 104 {disposalQuantityToBeMatched} shares from sell transaction (ID(s)
            {sellTransaction.transactionIDs} dated {sellTransaction.date} identified with the
            Section 104 holding. The average price of the sold shares was {averageDisposalPrice}
            GBP, and the average price of the shares in the Section 104 holding was {averagePurchasePrice} GBP.
     */
    @Test
    fun generateTransactionSummaryTest() {
        val sellTransaction = SELL_TRANSACTION_1.copy()
        section104 = Section104(
            transactionIDs = BUY_TRANSACTION_1.transactionIDs,
            quantity = BUY_TRANSACTION_1.quantity,
            price = BUY_TRANSACTION_1.price
        )

        val expectedSummary = "SECTION 104 " + sellTransaction.quantity + " shares from sell transaction " +
                "(ID(s) " + sellTransaction.transactionIDs + ") dated " + sellTransaction.date + " identified " +
                "with the Section 104 holding. The average price of the sold shares was " +
                sellTransaction.price / sellTransaction.quantity + " GBP, and the average price of the shares " +
                "in the Section 104 holding was " + section104.price / section104.quantity + " GBP. "

        val disposalQuantityToBeMatched = sellTransaction.quantity
        val averageDisposalPrice = sellTransaction.price / sellTransaction.quantity
        val averagePurchasePrice = section104.price / section104.quantity
        val summary = "SECTION 104 $disposalQuantityToBeMatched shares from sell transaction (ID(s) " +
                sellTransaction.transactionIDs + ") dated " + sellTransaction.date + " identified with " +
                "the Section 104 holding. The average price of the sold shares was $averageDisposalPrice GBP, " +
                "and the average price of the shares in the Section 104 holding was $averagePurchasePrice GBP. "

        assertEquals(expectedSummary, summary)
    }

    /**
     * Prints a summary of a disposal that is matched against shares in the Section 104 holding.
     * The transaction summary includes the profit or loss resulting from the disposal.
     *
     * Acceptance criteria:
     *  The profit or loss amount must always be rounded to two decimal figures.
     *
     * @Param profitOrLoss - The profit or loss of the transaction. Negative values indicate a loss.
     */
    @ParameterizedTest
    @ValueSource(doubles = [0.00, 10.20, 200.45, -10.23, -200.00])
    fun printTransactionSummaryTest(profitOrLoss: Double) {
        val profitOrLossRounded = BigDecimal(profitOrLoss)
            .setScale(2, RoundingMode.HALF_EVEN).toString()

        val expectedOutput = if (profitOrLoss >= 0) "Placeholder summary Profit = £$profitOrLossRounded."
        else "Placeholder summary Loss = £$profitOrLossRounded."

        val summary = "Placeholder summary "
        val profitOrLossSummary = if (profitOrLoss >= 0) "Profit = £$profitOrLossRounded."
        else "Loss = £$profitOrLossRounded."

        assertEquals(2, BigDecimal(profitOrLossRounded).scale())
        assertEquals(expectedOutput, summary + profitOrLossSummary)
    }

    /**
     * Prints a summary of shares remaining in the Section 104 holding after all suitable disposals
     * have been processed. Any shares remaining in the Section 104 holding will likely need to be
     * carried forward to future tax years and matched with future disposals.
     *
     * Acceptance criteria:
     *  The Section 104 summary should be formatted as shown below:
     *      \nCARRY FORWARD TO NEXT TAX TEAR There are purchased shares remaining in the Section 104 holding.
            These shares may need to be carried forward to be matched with future disposals. \n
            Summary of outstanding Section 104 holding: {section104}\n
     *
     * @param scenario The testing scenario number, which is used to select the appropriate test data.
     *  Scenario 1 - The Section 104 holding is empty.
     *  Scenario 2 - The Section 104 contains shares.
     */
    @ParameterizedTest
    @ValueSource(ints = [1, 2])
    private fun printSummaryOfSection104Test(scenario: Int) {
        var expectedMessage = ""
        if (scenario == 2) {
            section104 = Section104(
                transactionIDs = BUY_TRANSACTION_1.transactionIDs,
                quantity = BUY_TRANSACTION_1.quantity,
                price = BUY_TRANSACTION_1.price
            )
            expectedMessage = "\nCARRY FORWARD TO NEXT TAX TEAR There are purchased shares remaining in " +
                    "the Section 104 holding. These shares may need to be carried forward to be matched with " +
                    "future disposals. \n" +
                    "Summary of outstanding Section 104 holding: $section104\n"
        }

        if (section104.quantity != 0) {
            val outputMessage = "\nCARRY FORWARD TO NEXT TAX TEAR There are purchased shares remaining in the Section 104 holding. " +
                    "These shares may need to be carried forward to be matched with future disposals. \n" +
                    "Summary of outstanding Section 104 holding: $section104\n"
            assertEquals(expectedMessage, outputMessage)
        } else assertEquals(section104, Section104())
    }
}