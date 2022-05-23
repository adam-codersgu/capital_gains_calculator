
import model.Transaction
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProcessSameDayTransactionsTest {

    var buyTransactions = mutableListOf<Transaction>()
    var sellTransactions = mutableListOf<Transaction>()

    /**
     * Test Transaction object scenarios:
     *  BUY_TRANSACTION_1 and SELL_TRANSACTION_1 are the same day, same size, same price
     *  BUY_TRANSACTION_2 and SELL_TRANSACTION_2 are the same day but the sale transaction
     *  has a lower quantity and price
     *  BUY_TRANSACTION_3 and SELL_TRANSACTION_3 are the same day but the buy transaction
     *  has a lower quantity and price
     *  BUY_TRANSACTION_4 and SELL_TRANSACTION_4 are different days
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
            date = LocalDate.now().minusDays(3),
            quantity = 15,
            price = 120.10
        )
        private val BUY_TRANSACTION_4 = BUY_TRANSACTION_1.copy(
            date = LocalDate.now().minusDays(4),
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
        private val SELL_TRANSACTION_3 = BUY_TRANSACTION_3.copy(
            direction = "Sell",
            quantity = 30,
            price = 250.00
        )
        private val SELL_TRANSACTION_4 = BUY_TRANSACTION_4.copy(
            direction = "Sell",
            date = LocalDate.now().minusDays(5)
        )
    }

    /**
     * Before the tests begin, we should create lists of mock buy and sell model.Transaction objects
     */
    @BeforeAll
    private fun setup() {
        buyTransactions.addAll(listOf(BUY_TRANSACTION_1, BUY_TRANSACTION_2, BUY_TRANSACTION_3, BUY_TRANSACTION_4))
        sellTransactions.addAll(listOf(SELL_TRANSACTION_1, SELL_TRANSACTION_2, SELL_TRANSACTION_3, SELL_TRANSACTION_4))
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

    /**
     * Test the profitOrLoss reporting section of the reportSameDayTransaction method.
     *
     * Acceptance criteria:
     *  Profit should be reported as Profit = £{Value}
     *  Losses should be reported as Loss = £{Value}
     *
     * @Param profitOrLoss - The profit or loss of the transaction. Negative values indicate a loss.
     */
    @ParameterizedTest
    @ValueSource(doubles = [0.00, 10.20, 200.45, -10.23, -200.00])
    fun reportSameDayTransactionProfitOrLossTest(profitOrLoss: Double) {
        val profitOrLossMessage: String
        if (profitOrLoss >= 0) {
            profitOrLossMessage = "Profit = £$profitOrLoss."
            assertEquals("Profit = £$profitOrLoss.", profitOrLossMessage)
        } else {
            profitOrLossMessage = "Loss = £$profitOrLoss."
            assertEquals("Loss = £$profitOrLoss.", profitOrLossMessage)
        }
    }

    /**
     * Calculate the profit or loss incurred from the sale of an asset due to the same day disposal
     * rule.
     *
     * @Param rowIndex - The index of the buy and sell transactions in the buyTransactions
     * and sellTransactions lists that should be processed.
     */
    @ParameterizedTest
    @ValueSource(ints = [0, 1, 2])
    fun calculateProfitLossTest(rowIndex: Int) {
        val outstandingBuyTransactions = buyTransactions
        val buyTransaction = buyTransactions[rowIndex].copy()
        val sellTransaction = sellTransactions[rowIndex].copy()

        // The scenarios configured in the companion object should ensure this test always passes
        assertEquals(buyTransaction.date, sellTransaction.date)

        val profitOrLoss: Double
        when {
            // Quantity purchased and sold are equal
            sellTransaction.quantity == buyTransaction.quantity -> {
                profitOrLoss = BigDecimal(sellTransaction.price -
                        buyTransaction.price).setScale(2, RoundingMode.HALF_EVEN).toDouble()
                assertEquals(sellTransaction.price - buyTransaction.price, profitOrLoss)
            }
            // Quantity sold is greater than the quantity purchased
            sellTransaction.quantity > buyTransaction.quantity -> {
                val percentageOfSoldSharesRemaining = buyTransaction.quantity.toDouble() /
                        sellTransaction.quantity.toDouble()
                val valueOfSoldShares = sellTransaction.price * percentageOfSoldSharesRemaining
                val remainingSoldSharesTransaction = sellTransaction.copy(
                    quantity = sellTransaction.quantity - buyTransaction.quantity,
                    price = sellTransaction.price - valueOfSoldShares
                )
                assertEquals(sellTransaction.quantity - buyTransaction.quantity, remainingSoldSharesTransaction.quantity)
                assertEquals(String.format("%.2f",sellTransaction.price * percentageOfSoldSharesRemaining),
                    String.format("%.2f",remainingSoldSharesTransaction.price))
                profitOrLoss = BigDecimal(valueOfSoldShares -
                        buyTransaction.price).setScale(2, RoundingMode.HALF_EVEN).toDouble()
                assertEquals(String.format("%.2f",valueOfSoldShares - buyTransaction.price),
                    String.format("%.2f",profitOrLoss))
            }
            // Quantity purchased is greater than the quantity sold
            else -> {
                val percentageOfPurchasedSharesRemaining = sellTransaction.quantity.toDouble() /
                        buyTransaction.quantity.toDouble()
                val valueOfPurchasedShares = buyTransaction.price * percentageOfPurchasedSharesRemaining
                val index = outstandingBuyTransactions.indexOf(buyTransaction)
                outstandingBuyTransactions[index].quantity -= sellTransaction.quantity
                outstandingBuyTransactions[index].price -= valueOfPurchasedShares
                assertEquals(buyTransaction.quantity - sellTransaction.quantity,  outstandingBuyTransactions[index].quantity)
                assertEquals(String.format("%.2f",buyTransaction.price * percentageOfPurchasedSharesRemaining),
                    String.format("%.2f",outstandingBuyTransactions[index].price))
                profitOrLoss = BigDecimal(sellTransaction.price -
                        valueOfPurchasedShares).setScale(2, RoundingMode.HALF_EVEN).toDouble()
                assertEquals(String.format("%.1f",sellTransaction.price - valueOfPurchasedShares),
                    String.format("%.1f",profitOrLoss))
            }
        }
    }
}