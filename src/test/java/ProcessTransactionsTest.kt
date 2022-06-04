import model.OutstandingTransactions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.math.BigDecimal
import java.math.RoundingMode

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProcessTransactionsTest {

    /**
     * Prints a summary of  the total gain, loss and profit figures from all the transactions processed
     * by the application.
     *
     * @param scenario The testing scenario number, which is used to select the appropriate test data.
     *  Scenario 1 - The gains and losses are zero.
     *  Scenario 2 - The gains are greater than the losses.
     *  Scenario 3 - The losses are greater than the gains.
     */
    @ParameterizedTest
    @ValueSource(ints = [1, 2, 3])
    fun printGainsLossesProfitsSummaryTest(scenario: Int) {
        val outstandingTransactions = when (scenario) {
            2 -> OutstandingTransactions(
                totalGains = 100.20,
                totalLoss = 53.20
            )
            3 -> OutstandingTransactions(
                totalGains = 43.56,
                totalLoss = 65.43
            )
            else -> OutstandingTransactions()
        }

        val gains = BigDecimal(outstandingTransactions.totalGains).setScale(2, RoundingMode.HALF_EVEN).toString()
        assertEquals(String.format("%.2f", outstandingTransactions.totalGains), gains)
        val losses = BigDecimal(outstandingTransactions.totalLoss).setScale(2, RoundingMode.HALF_EVEN).toString()
        assertEquals(String.format("%.2f", outstandingTransactions.totalLoss), losses)
        val profits = BigDecimal(gains.toDouble() + losses.toDouble())
            .setScale(2, RoundingMode.HALF_EVEN).toString()
        assertEquals(String.format("%.2f", gains.toDouble() + losses.toDouble()), profits)
    }
}