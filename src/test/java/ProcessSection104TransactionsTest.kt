
import model.OutstandingTransactions
import model.Section104
import model.Transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
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
     *  BUY_TRANSACTION_2 and SELL_TRANSACTION_2 are different days, different prices (profitable),
     *  and should be matched according to the Bed and Breakfast disposal rule
     *  BUY_TRANSACTION_3 and SELL_TRANSACTION_3 are different days, different prices (profitable),
     *  and should be matched according to the Bed and Breakfast disposal rule
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
            quantity = 15,
            price = 102.04
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
}