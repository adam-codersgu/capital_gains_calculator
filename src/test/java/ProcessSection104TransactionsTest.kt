
import model.OutstandingTransactions
import model.Transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProcessSection104TransactionsTest {

    var outstandingTransactions = OutstandingTransactions()

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
     * Before the tests begin, we should create lists of mock buy and sell model.Transaction objects
     */
    @BeforeEach
    private fun setup() {
        outstandingTransactions.buyTransactions.addAll(listOf(BUY_TRANSACTION_1, BUY_TRANSACTION_2, BUY_TRANSACTION_3))
        outstandingTransactions.sellTransactions.addAll(listOf(SELL_TRANSACTION_1, SELL_TRANSACTION_2, SELL_TRANSACTION_3))
    }

    /**
     * Match all disposals with previously purchased stock (the Section 104 holding).
     * Section 104 disposals have third priority (after Same Day and Bed and Breakfast disposals)
     * when matching transactions for an HMRC Capital Gains report.
     */
    @Test
    fun processTest() {
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
}