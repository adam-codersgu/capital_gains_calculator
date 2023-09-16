
import model.OutstandingTransactions
import model.Transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProcessTransactionTest {

    var outstandingTransactions = OutstandingTransactions()

    /**
     * Test Transaction object scenarios:
     *  BUY_TRANSACTION_1 and SELL_TRANSACTION_1 are the same day, same size, same price
     *  BUY_TRANSACTION_2 and SELL_TRANSACTION_2 are the same day but the sale transaction
     *  has a lower quantity and price
     *  BUY_TRANSACTION_3 and SELL_TRANSACTION_3 are the same day but the buy transaction
     *  has a lower quantity and price
     *  BUY_TRANSACTION_4 and SELL_TRANSACTION_4 are different days, the same price, and should
     *  be matched according to the Bed and Breakfast disposal rule
     *  BUY_TRANSACTION_5 and SELL_TRANSACTION_5 are different days, different prices, and should
     *  be matched according to the Bed and Breakfast disposal rule
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
        private val BUY_TRANSACTION_4 = BUY_TRANSACTION_1.copy(
            date = LocalDate.now().minusDays(4),
            remainingQuantity = 20,
            remainingValue = 432.23
        )
        private val BUY_TRANSACTION_5 = BUY_TRANSACTION_1.copy(
            date = LocalDate.now().minusDays(6),
            remainingQuantity = 25,
            remainingValue = 467.20
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
        private val SELL_TRANSACTION_4 = BUY_TRANSACTION_4.copy(
            type = "Sell",
            date = LocalDate.now().minusDays(5)
        )
        private val SELL_TRANSACTION_5 = BUY_TRANSACTION_5.copy(
            type = "Sell",
            date = LocalDate.now().minusDays(7),
            remainingValue = 485.23
        )
    }

    /**
     * Before the tests begin, we should create lists of mock buy and sell model.Transaction objects
     */
    @BeforeEach
    private fun setup() {
        outstandingTransactions = OutstandingTransactions(
            buyTransactions = mutableListOf(BUY_TRANSACTION_1, BUY_TRANSACTION_2,
                BUY_TRANSACTION_3, BUY_TRANSACTION_4, BUY_TRANSACTION_5),
            sellTransactions = mutableListOf(SELL_TRANSACTION_1, SELL_TRANSACTION_2,
                SELL_TRANSACTION_3, SELL_TRANSACTION_4, SELL_TRANSACTION_5)
        )
    }

    /**
     * Returns a summary of the profit or loss resulting from a given transaction.
     *
     * Acceptance criteria:
     *  Profit should be reported as Profit = £{Value}
     *  Losses should be reported as Loss = £{Value}
     *  The profit or loss amount must always be rounded to two decimal figures.
     *
     * @Param profitOrLoss - The profit or loss of the transaction. Negative values indicate a loss.
     */
    @ParameterizedTest
    @ValueSource(doubles = [0.00, 10.20, 200.45, -10.23, -200.00])
    fun getProfitOrLossTest(profitOrLoss: Double) {
        val profitOrLossRounded = BigDecimal(profitOrLoss)
            .setScale(2, RoundingMode.HALF_EVEN).toString()

        val expectedOutput = if (profitOrLoss >= 0) "Profit = £$profitOrLossRounded."
        else "Loss = £$profitOrLossRounded."

        assertEquals(2, BigDecimal(profitOrLossRounded).scale())
        if (profitOrLoss >= 0) {
            assertEquals(expectedOutput, "Profit = £$profitOrLossRounded.")
        } else {
            assertEquals(expectedOutput, "Loss = £$profitOrLossRounded.")
        }
    }

    /**
     * Calculate the profit or loss incurred from the sale of an asset.
     *
     * @Param rowIndex - The index of the buy and sell transactions in the buyTransactions
     * and sellTransactions lists that should be processed.
     */
    @ParameterizedTest
    @ValueSource(ints = [0, 1, 2, 3, 4])
    fun calculateProfitLossTest(rowIndex: Int) {
        val buyTransaction = outstandingTransactions.buyTransactions[rowIndex].copy()
        val sellTransaction = outstandingTransactions.sellTransactions[rowIndex].copy()

        val profitOrLoss: Double
        when {
            // Quantity purchased and sold are equal
            sellTransaction.remainingQuantity == buyTransaction.remainingQuantity -> {
                profitOrLoss = sellTransaction.remainingValue - buyTransaction.remainingValue
                assertEquals(sellTransaction.remainingValue - buyTransaction.remainingValue, profitOrLoss)
            }
            // Quantity sold is greater than the quantity purchased
            sellTransaction.remainingQuantity > buyTransaction.remainingQuantity -> {
                val percentageOfSoldSharesRemaining = buyTransaction.remainingQuantity.toDouble() /
                        sellTransaction.remainingQuantity.toDouble()
                val valueOfSoldShares = sellTransaction.remainingValue * percentageOfSoldSharesRemaining
                val index = outstandingTransactions.sellTransactions.indexOf(sellTransaction)
                outstandingTransactions.sellTransactions[index].remainingQuantity -= buyTransaction.remainingQuantity
                outstandingTransactions.sellTransactions[index].remainingValue -= valueOfSoldShares
                assertEquals(
                    sellTransaction.remainingQuantity - buyTransaction.remainingQuantity,
                    outstandingTransactions.sellTransactions[index].remainingQuantity
                )
                assertEquals(
                    String.format("%.2f", sellTransaction.remainingValue * percentageOfSoldSharesRemaining),
                    String.format("%.2f", outstandingTransactions.sellTransactions[index].remainingValue)
                )
                profitOrLoss = valueOfSoldShares - buyTransaction.remainingValue
                assertEquals(valueOfSoldShares - buyTransaction.remainingValue, profitOrLoss)
            }
            // Quantity purchased is greater than the quantity sold
            else -> {
                val percentageOfPurchasedSharesRemaining = sellTransaction.remainingQuantity.toDouble() /
                        buyTransaction.remainingQuantity.toDouble()
                val valueOfPurchasedShares = buyTransaction.remainingValue * percentageOfPurchasedSharesRemaining
                val index = outstandingTransactions.buyTransactions.indexOf(buyTransaction)
                outstandingTransactions.buyTransactions[index].remainingQuantity -= sellTransaction.remainingQuantity
                outstandingTransactions.buyTransactions[index].remainingValue -= valueOfPurchasedShares
                assertEquals(
                    buyTransaction.remainingQuantity - sellTransaction.remainingQuantity,
                    outstandingTransactions.buyTransactions[index].remainingQuantity
                )
                assertEquals(
                    String.format("%.2f", buyTransaction.remainingValue * percentageOfPurchasedSharesRemaining),
                    String.format("%.2f", outstandingTransactions.buyTransactions[index].remainingValue)
                )
                profitOrLoss = sellTransaction.remainingValue - valueOfPurchasedShares
                assertEquals(sellTransaction.remainingValue - valueOfPurchasedShares, profitOrLoss)
            }
        }
    }

    /**
     * Update the lists of outstanding transactions to reflect the purchases and disposals
     * that have been matched and no longer require processing.
     *
     * @Param rowIndex - The index of the buy and sell transactions in the buyTransactions
     * and sellTransactions lists that should be processed.
     */
    @ParameterizedTest
    @ValueSource(ints = [0, 1, 2, 3, 4])
    fun updateOutstandingTransactionsTest(rowIndex: Int) {
        val buyTransaction = outstandingTransactions.buyTransactions[rowIndex].copy()
        val sellTransaction = outstandingTransactions.sellTransactions[rowIndex].copy()

        when {
            // Quantity purchased and sold are equal
            sellTransaction.remainingQuantity == buyTransaction.remainingQuantity -> {
                assertTrue(outstandingTransactions.buyTransactions.remove(buyTransaction))
                assertTrue(outstandingTransactions.sellTransactions.remove(sellTransaction))
            }
            // Quantity sold is greater than the quantity purchased
            sellTransaction.remainingQuantity > buyTransaction.remainingQuantity -> {
                val percentageOfSoldSharesRemaining = buyTransaction.remainingQuantity.toDouble() /
                        sellTransaction.remainingQuantity.toDouble()
                val valueOfSoldShares = sellTransaction.remainingValue * percentageOfSoldSharesRemaining
                val index = outstandingTransactions.sellTransactions.indexOf(sellTransaction)
                outstandingTransactions.sellTransactions[index].remainingQuantity -= buyTransaction.remainingQuantity
                assertEquals(outstandingTransactions.sellTransactions[index].remainingQuantity, buyTransaction.remainingQuantity)
                outstandingTransactions.sellTransactions[index].remainingValue -= valueOfSoldShares
                assertEquals(outstandingTransactions.sellTransactions[index].remainingValue, valueOfSoldShares)
                assertTrue(outstandingTransactions.buyTransactions.remove(buyTransaction))
            }
            // Quantity purchased is greater than the quantity sold
            else -> {
                val percentageOfPurchasedSharesRemaining = sellTransaction.remainingQuantity.toDouble() /
                        buyTransaction.remainingQuantity.toDouble()
                val valueOfPurchasedShares = buyTransaction.remainingValue * percentageOfPurchasedSharesRemaining
                val index = outstandingTransactions.buyTransactions.indexOf(buyTransaction)
                outstandingTransactions.buyTransactions[index].remainingQuantity -= sellTransaction.remainingQuantity
                assertEquals(outstandingTransactions.buyTransactions[index].remainingQuantity, sellTransaction.remainingQuantity)
                outstandingTransactions.buyTransactions[index].remainingValue -= valueOfPurchasedShares
                assertEquals(outstandingTransactions.buyTransactions[index].remainingValue, valueOfPurchasedShares)
                assertTrue( outstandingTransactions.sellTransactions.remove(sellTransaction))
            }
        }
    }
}