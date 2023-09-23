
import model.OutstandingTransactions
import model.Transaction
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDate
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProcessBedAndBreakfastTransactionsTest {

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
            10,
            100.00,
            100.00
        )
        private val BUY_TRANSACTION_2 = BUY_TRANSACTION_1.copy(
            date = LocalDate.now().minusDays(2),
            remainingQuantity = 25,
            remainingValue = 467.20
        )
        private val BUY_TRANSACTION_3 = BUY_TRANSACTION_1.copy(
            date = LocalDate.now().minusDays(3),
            remainingQuantity = 15,
            remainingValue = 120.10
        )

        private val SELL_TRANSACTION_1 = BUY_TRANSACTION_1.copy(
            date = LocalDate.now().minusDays(1),
            type = "Sell"
        )
        private val SELL_TRANSACTION_2 = BUY_TRANSACTION_2.copy(
            type = "Sell",
            date = LocalDate.now().minusDays(3),
            remainingValue = 485.23
        )
        private val SELL_TRANSACTION_3 = BUY_TRANSACTION_3.copy(
            type = "Sell",
            date = LocalDate.now().minusDays(5),
            remainingValue = 102.04
        )
    }

    /**
     * Before the tests begin, create lists of mock outstanding buy and sell Transaction objects
     */
    @BeforeAll
    private fun setup() {
        outstandingTransactions.buyTransactions.addAll(listOf(BUY_TRANSACTION_1, BUY_TRANSACTION_2, BUY_TRANSACTION_3))
        outstandingTransactions.sellTransactions.addAll(listOf(SELL_TRANSACTION_1, SELL_TRANSACTION_2, SELL_TRANSACTION_3))
    }

    /**
     * If a purchase occurs up to 30 days after a sale, then match the buy transaction and
     * sale transaction.
     */
    @Test
    fun processTest() {
        for (sellTransaction in outstandingTransactions.sellTransactions) {
            val maxDate = sellTransaction.date.plusDays(31)

            var quantityOfSharesToMatch = sellTransaction.remainingQuantity
            while (quantityOfSharesToMatch > 0) {
                val buyTransaction = outstandingTransactions.buyTransactions.find {
                    it.date.isAfter(sellTransaction.date) && it.date.isBefore(maxDate)
                }

                // A buy transaction up to 30 days after the disposal
                if (buyTransaction != null) {
                    assertTrue {
                        outstandingTransactions.buyTransactions.indexOfFirst { it.date.isAfter(sellTransaction.date) && it.date.isBefore(maxDate) } != -1
                    }

                    // Must retrieve an up-to-date sell Transaction object in case its quantity and price have
                    // been modified during previous iterations.
                    assertNotNull(outstandingTransactions.sellTransactions.find {
                        it.transactionIds == sellTransaction.transactionIds
                    })
                    quantityOfSharesToMatch -= buyTransaction.remainingQuantity
                }
                // No buy transaction found, hence not a bed and breakfast disposal
                else {
                    assertTrue {
                        outstandingTransactions.buyTransactions.indexOfFirst { it.date.isAfter(sellTransaction.date) && it.date.isBefore(maxDate) } == -1
                    }
                    break
                }
            }
        }
    }

    /**
     * Test the transaction reporting section of the reportSameDayTransaction method.
     *
     * Acceptance criteria:
     *  The output should be formatted as shown below:
     *      "BED AND BREAKFAST Sell transaction(s) (IDs {sell transaction IDs}) identified with buy transaction(s)
     *      (IDs {buy transaction IDs} ). {sell transaction quantity} shares sold for an average price of
     *      {average sell transaction price} GBP on {sell transaction date} identified with {buy transaction quantity}
     *      shares bought on {buy transaction date} for an average price of {average buy transaction price} GBP.
     *      {Profit/Loss from the transaction}"
     *
     * @Param rowIndex - The index of the buy and sell transactions in the buyTransactions
     * and sellTransactions lists that should be processed.
     */
    @ParameterizedTest
    @ValueSource(ints = [0, 1, 2])
    fun reportBedAndBreakfastTransaction(rowIndex: Int) {
        val buyTransaction = outstandingTransactions.buyTransactions[rowIndex].copy()
        val sellTransaction = outstandingTransactions.sellTransactions[rowIndex].copy()
        val profitOrLossMessage = "Profit or loss message placeholder"

        val expectedOutput = "BED AND BREAKFAST Sell transaction(s) (IDs " + sellTransaction.transactionIds +
                ") identified with buy transaction(s) (IDs " + buyTransaction.transactionIds +
                "). " + sellTransaction.remainingQuantity + " shares sold for an average price of " +
                sellTransaction.remainingValue / sellTransaction.remainingQuantity + " GBP on " + sellTransaction.date +
                " identified with " + buyTransaction.remainingQuantity + " shares bought on " + buyTransaction.date +
                " for an average price of " + buyTransaction.remainingValue / buyTransaction.remainingQuantity +
                " GBP. $profitOrLossMessage"

        val averageSellPrice = sellTransaction.remainingValue / sellTransaction.remainingQuantity
        val averageBuyPrice = buyTransaction.remainingValue / buyTransaction.remainingQuantity
        val output = "BED AND BREAKFAST Sell transaction(s) (IDs " + sellTransaction.transactionIds +
                ") identified with buy transaction(s) (IDs " + buyTransaction.transactionIds +
                "). " + sellTransaction.remainingQuantity + " shares sold for an average price of $averageSellPrice" +
                " GBP on " + sellTransaction.date + " identified with " + buyTransaction.remainingQuantity +
                " shares bought on " + buyTransaction.date + " for an average price of $averageBuyPrice" +
                " GBP. $profitOrLossMessage"
        assertEquals(expectedOutput, output)
    }
}