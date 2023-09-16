
import model.OutstandingTransactions
import model.Transaction
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDate
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProcessSameDayTransactionsTest {

    var outstandingTransactions = OutstandingTransactions()

    /**
     * Test Transaction object scenarios:
     *  BUY_TRANSACTION_1 and SELL_TRANSACTION_1 are the same day, same size, same price
     *  BUY_TRANSACTION_2 and SELL_TRANSACTION_2 are the same day but the sale transaction
     *  has a lower quantity and price
     *  BUY_TRANSACTION_3 and SELL_TRANSACTION_3 are the same day but the buy transaction
     *  has a lower quantity and price
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
            remainingQuantity = 200,
            remainingValue = 3500.45
        )
        private val BUY_TRANSACTION_3 = BUY_TRANSACTION_1.copy(
            date = LocalDate.now().minusDays(3),
            remainingQuantity = 15,
            remainingValue = 120.10
        )

        private val SELL_TRANSACTION_1 = BUY_TRANSACTION_1.copy(
            type = "Sell"
        )
        private val SELL_TRANSACTION_2 = BUY_TRANSACTION_2.copy(
            type = "Sell",
            remainingQuantity = 100,
            remainingValue = 250.00
        )
        private val SELL_TRANSACTION_3 = BUY_TRANSACTION_3.copy(
            type = "Sell",
            remainingQuantity = 30,
            remainingValue = 250.00
        )
    }

    /**
     * Before the tests begin, we should create lists of mock buy and sell model.Transaction objects
     */
    @BeforeAll
    private fun setup() {
        outstandingTransactions.buyTransactions.addAll(listOf(BUY_TRANSACTION_1, BUY_TRANSACTION_2, BUY_TRANSACTION_3))
        outstandingTransactions.sellTransactions.addAll(listOf(SELL_TRANSACTION_1, SELL_TRANSACTION_2, SELL_TRANSACTION_3))
    }

    /**
     * If a purchase occurs on the same day as a sale, then match the buy transaction and
     * sale transaction.
     */
    @Test
    fun processTest() {
        for (sellTransaction in outstandingTransactions.sellTransactions) {
            val buyTransaction = outstandingTransactions.buyTransactions.find { it.date == sellTransaction.date }
            // A buy transaction occurred on the same day as the sell transaction
            if (buyTransaction != null) {
                assertTrue {
                    outstandingTransactions.buyTransactions.indexOfFirst { it.date == sellTransaction.date } != -1
                }
            }
            // No buy transaction found, hence not a same day disposal
            else {
                assertTrue {
                    outstandingTransactions.buyTransactions.indexOfFirst { it.date == sellTransaction.date } == -1
                }
            }
        }
    }

    /**
     * Test the transaction reporting section of the reportSameDayTransaction method.
     *
     * Acceptance criteria:
     *  The output should be formatted as shown below:
     *      "SAME DAY Sell transaction(s) (IDs {sell transaction IDs}) identified with buy transaction(s)
     *      (IDs {buy transaction IDs} ) on {transaction date}. {buy transaction quantity} shares were
     *      bought for an average price of {average buy transaction price} GBP and {sell transaction quantity}
     *      shares were sold for an average price of {average buy transaction price} GBP. {Profit/Loss from the transaction}"
     *
     * @Param rowIndex - The index of the buy and sell transactions in the buyTransactions
     * and sellTransactions lists that should be processed.
     */
    @ParameterizedTest
    @ValueSource(ints = [0, 1, 2])
    fun reportSameDayTransactionTest(rowIndex: Int) {
        val buyTransaction = outstandingTransactions.buyTransactions[rowIndex].copy()
        val sellTransaction = outstandingTransactions.sellTransactions[rowIndex].copy()
        val profitOrLossMessage = "Profit or loss message placeholder"

        val expectedOutput = "SAME DAY Sell transaction(s) (IDs " + sellTransaction.transactionIds +
                ") identified with buy transaction(s) (IDs " + buyTransaction.transactionIds +
                ") on " + buyTransaction.date + ". " + buyTransaction.remainingQuantity +
                " shares were bought for an average price of " + buyTransaction.remainingValue / buyTransaction.remainingQuantity +
                " GBP and " + sellTransaction.remainingQuantity + " shares were sold for an average price of " +
                sellTransaction.remainingValue / sellTransaction.remainingQuantity + " GBP. $profitOrLossMessage"

        val averageSellPrice = sellTransaction.remainingValue / sellTransaction.remainingQuantity
        val averageBuyPrice = buyTransaction.remainingValue / buyTransaction.remainingQuantity
        val output = "SAME DAY Sell transaction(s) (IDs " + sellTransaction.transactionIds +
                ") identified with buy transaction(s) (IDs " + buyTransaction.transactionIds +
                ") on " + buyTransaction.date + ". " + buyTransaction.remainingQuantity +
                " shares were bought for an average price of $averageBuyPrice GBP and " +
                sellTransaction.remainingQuantity + " shares were sold for an average price of $averageSellPrice " +
                "GBP. $profitOrLossMessage"
        assertEquals(expectedOutput, output)
    }
}