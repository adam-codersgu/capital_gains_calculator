
import model.Transaction
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDate

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProcessSameDayTransactionsTest {

    var buyTransactions = mutableListOf<Transaction>()
    var sellTransactions = mutableListOf<Transaction>()

    /**
     * Test Transaction object scenarios:
     *  BUY_TRANSACTION_1 and SELL_TRANSACTION_1 are the same day, same size, same price
     *  BUY_TRANSACTION_2 and SELL_TRANSACTION_2 are the same day but the sale transaction
     *  has a lower quantity and price
     *  BUY_TRANSACTION_3 and SELL_TRANSACTION_3 are different days
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
            date = LocalDate.now().minusDays(1),
            quantity = 200,
            price = 3500.45
        )
        private val BUY_TRANSACTION_3 = BUY_TRANSACTION_1.copy(
            date = LocalDate.now().minusDays(2),
            quantity = 20,
            price = 432.23
        )

        private val SELL_TRANSACTION_1 = BUY_TRANSACTION_1.copy(
            direction = "Sell"
        )
        private val SELL_TRANSACTION_2 = BUY_TRANSACTION_2.copy(
            direction = "Sell",
            quantity = 100,
            price = 250.00
        )
        private val SELL_TRANSACTION_3 = BUY_TRANSACTION_2.copy(
            direction = "Sell",
            date = LocalDate.now().minusDays(3)
        )
    }

    /**
     * Before the tests begin, we should create lists of mock buy and sell model.Transaction objects
     */
    @BeforeAll
    private fun setup() {
        buyTransactions.addAll(listOf(BUY_TRANSACTION_1, BUY_TRANSACTION_2, BUY_TRANSACTION_3))
        sellTransactions.addAll(listOf(SELL_TRANSACTION_1, SELL_TRANSACTION_2, SELL_TRANSACTION_3))
    }

    /**
     * If a purchase occurs on the same day as a sale, then match the buy transaction and
     * sale transaction.
     */
    @Test
    fun processTest() {
        for (sellTransaction in sellTransactions) {
            val buyTransaction = buyTransactions.find { it.date == sellTransaction.date }
            // A buy transaction occurred on the same day as the sell transaction
            if (buyTransaction != null) {
                assertTrue {
                    buyTransactions.indexOfFirst { it.date == sellTransaction.date } != -1
                }
            }
            // No buy transaction found, hence not a same day disposal
            else {
                assertTrue {
                    buyTransactions.indexOfFirst { it.date == sellTransaction.date } == -1
                }
            }
        }
    }
}