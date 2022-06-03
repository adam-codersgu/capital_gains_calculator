
import exceptions.AcquisitionNotFollowingDisposalException
import model.OutstandingTransactions
import model.Transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProcessAcquisitionFollowingDisposalTransactionsTest() {

    var outstandingTransactions = OutstandingTransactions()

    /**
     * Test Transaction object scenarios:
     *  BUY_TRANSACTION_1 and SELL_TRANSACTION_1 are one day apart, same size, same price
     *  BUY_TRANSACTION_2 and SELL_TRANSACTION_2 are different days, different prices (profitable)
     *  BUY_TRANSACTION_3 and SELL_TRANSACTION_3 are different days but the disposal occurs after
     *  the acquisition. An AcquisitionNotFollowingDisposalException should be thrown.
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
        private val BUY_TRANSACTION_3 = BUY_TRANSACTION_2.copy(
            date = LocalDate.now().minusDays(5)
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
        private val SELL_TRANSACTION_3 = SELL_TRANSACTION_2.copy(
            date = LocalDate.now().minusDays(4)
        )
    }

    /**
     * Before the tests begin, create a list of mock outstanding sell Transaction objects
     */
    @BeforeEach
    private fun setup() {
        outstandingTransactions.buyTransactions.addAll(listOf(BUY_TRANSACTION_1, BUY_TRANSACTION_2, BUY_TRANSACTION_3))
        outstandingTransactions.sellTransactions.addAll(listOf(SELL_TRANSACTION_1, SELL_TRANSACTION_2, SELL_TRANSACTION_3))
    }

    /**
     * Reset the lists of outstanding transaction objects after each test.
     */
    @AfterEach
    fun teardown() {
        outstandingTransactions.buyTransactions.clear()
        outstandingTransactions.sellTransactions.clear()
    }

    /**
     * Match all purchases that occurred following a disposal.
     * Acquisitions following disposals have the lowest priority in an HMRC Capital Gains report
     * and transactions should only be matched this way if no other disposal rule applied.
     * For example, the acquisitions following disposals rule may apply to the closure
     * of a short position.
     *
     * @param scenario The testing scenario number, which is used to select the appropriate test data.
     *  Scenario 1 - The list of outstanding buy transactions is empty.
     *  Scenario 2 - The list of outstanding buy transactions contains items.
     */
    @ParameterizedTest
    @ValueSource(ints = [1, 2])
    fun processTest(scenario: Int) {
        if (scenario == 1) outstandingTransactions.buyTransactions.clear()

        for (sellTransaction in outstandingTransactions.sellTransactions.toList()) {
            var quantityOfSharesToMatch = sellTransaction.quantity
            while (quantityOfSharesToMatch > 0) {
                val buyTransaction = if (outstandingTransactions.buyTransactions.isEmpty()) null
                else outstandingTransactions.buyTransactions[0]

                if (buyTransaction == null) assertEquals(outstandingTransactions.buyTransactions.size, 0)
                else assertEquals(outstandingTransactions.buyTransactions[0], buyTransaction)

                if (buyTransaction != null) {
                    assertNotNull(outstandingTransactions.sellTransactions.find {
                        it.transactionIDs == sellTransaction.transactionIDs
                    })
                    val originalQuantityOfSharesToMatch = quantityOfSharesToMatch
                    quantityOfSharesToMatch -= buyTransaction.quantity
                    assertEquals(originalQuantityOfSharesToMatch - buyTransaction.quantity, quantityOfSharesToMatch)
                } else break
            }
        }
    }

    /**
     * Print a summary of the acquisition following a disposal report to the console.
     *
     * Acceptance criteria:
     *  The output should be formatted as shown below:
     *      "ACQUISITION FOLLOWING DISPOSAL Sell transaction(s) (IDs {sell transaction IDs}) identified with
     *      buy transaction(s) (IDs {buy transaction IDs} ). {sell transaction quantity} shares sold for an average
     *      price of {average sell transaction price} GBP on {sell transaction date} identified with
     *      {buy transaction quantity} shares bought on {buy transaction date} for an average price of
     *      {average buy transaction price} GBP. {Profit/Loss from the transaction}"
     *
     * @Param rowIndex - The index of the buy and sell transactions in the buyTransactions
     * and sellTransactions lists that should be processed.
     * @throws AcquisitionNotFollowingDisposalException If the date of the buy transaction does
     * not exceed the date of the sell transaction.
     */
    @ParameterizedTest
    @ValueSource(ints = [0, 1, 2])
    fun reportAcquisitionFollowingDisposalTransactionTest(rowIndex: Int) {
        val buyTransaction = outstandingTransactions.buyTransactions[rowIndex].copy()
        val sellTransaction = outstandingTransactions.sellTransactions[rowIndex].copy()
        val profitOrLossMessage = "Profit or loss message placeholder"

        val expectedOutput = "ACQUISITION FOLLOWING DISPOSAL Sell transaction(s) (IDs " + sellTransaction.transactionIDs +
                ") identified with buy transaction(s) (IDs " + buyTransaction.transactionIDs +
                "). " + sellTransaction.quantity + " shares sold for an average price of " +
                sellTransaction.price / sellTransaction.quantity + " GBP on " + sellTransaction.date +
                " identified with " + buyTransaction.quantity + " shares bought on " + buyTransaction.date +
                " for an average price of " + buyTransaction.price / buyTransaction.quantity +
                " GBP. $profitOrLossMessage"

        // The date of the buy transaction must exceed the date of the sell transaction
        fun reportAcquisitionFollowingDisposalTransaction(buyTransaction: Transaction, sellTransaction: Transaction) {
            if (buyTransaction.date <= sellTransaction.date) {
                throw AcquisitionNotFollowingDisposalException()
            }
        }

        // Check that AcquisitionNotFollowingDisposalException if the buy transaction date does not exceed
        // the sell transaction date.
        if (sellTransaction.date >= buyTransaction.date) {
            assertFailsWith<AcquisitionNotFollowingDisposalException>(
                block = { reportAcquisitionFollowingDisposalTransaction(buyTransaction, sellTransaction) })
        }

        val averageSellPrice = sellTransaction.price / sellTransaction.quantity
        val averageBuyPrice = buyTransaction.price / buyTransaction.quantity
        val output = "ACQUISITION FOLLOWING DISPOSAL Sell transaction(s) (IDs " + sellTransaction.transactionIDs +
                ") identified with buy transaction(s) (IDs " + buyTransaction.transactionIDs +
                "). " + sellTransaction.quantity + " shares sold for an average price of $averageSellPrice" +
                " GBP on " + sellTransaction.date + " identified with " + buyTransaction.quantity +
                " shares bought on " + buyTransaction.date + " for an average price of $averageBuyPrice" +
                " GBP. $profitOrLossMessage"
        assertEquals(expectedOutput, output)
    }
}