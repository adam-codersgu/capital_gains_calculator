
import exceptions.UnknownTransactionTypeException
import mock.TestSpreadsheetRowData
import mock.templates.SpreadsheetRow
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.junit.jupiter.MockitoExtension
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalDate.now
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProcessSpreadsheetTest {

    private lateinit var fileInputStream: FileInputStream
    private lateinit var testData: Array<SpreadsheetRow>
    private lateinit var workbook: XSSFWorkbook

    /**
     * Before the tests begin, we should create a testing Excel spreadsheet containing example stock transactions.
     * The example stock transactions are defined in the companion object.
     *
     * @Param tempDir - A temporary directory created by JUnit that will store the Excel spreadsheet
     */

    @BeforeAll
    private fun setup(@TempDir tempDir: Path) {
        val testSpreadsheet = tempDir.resolve("test_stock_transactions.xlsx")

        val xlWb = XSSFWorkbook()
        val xlWs = xlWb.createSheet()
        testData = TestSpreadsheetRowData().getTestSpreadsheetRowData()

        for ((index, row) in testData.withIndex()) {
            val insertedRow = xlWs.createRow(index)
            val content = row.getData()

            for (i in 0..11) {
                val cell = insertedRow.createCell(i)
                when (val item = content[i]) {
                    is String -> cell.setCellValue(item)
                    is Double -> cell.setCellValue(item)
                }
            }
        }

        val outputStream = FileOutputStream(testSpreadsheet.toFile())
        xlWb.apply {
            write(outputStream)
            close()
        }

        fileInputStream = FileInputStream(testSpreadsheet.toFile())
        workbook = XSSFWorkbook(fileInputStream)
    }

    /**
     * Once testing is complete, the input stream to the test spreadsheet should be closed.
     * Closing the input stream is required for the temporary test directory to be deleted.
     */

    @AfterAll
    private fun teardown() {
        fileInputStream.close()
    }

    /**
     * Establish that the test data spreadsheet has been successfully created by the setup() method
     * Assess that an input stream to the spreadsheet is available
     */
    @Test
    fun testExampleSpreadsheetExistsAndIsAvailable() {
        assertTrue(this::fileInputStream.isInitialized)
        assertEquals(testData.size, TestSpreadsheetRowData().getTestSpreadsheetRowData().size)
    }

    /**
     * Assess that the following data can be accurately extracted from the test data spreadsheet for
     * buy and sell transactions:
     *  - Asset name
     *  - Asset ISIN
     *
     * Acceptance criteria:
     *  The asset name and ISIN should be reported in the format provided below:
     *      --- {Asset Name} --- ISIN: {ISIN} ---
     *  A line break should be included after the asset name and ISIN
     *
     *  @Param rowNumber - The row number from the test data Excel spreadsheet that will be tested
     *      Default value: 0
     */
    @ParameterizedTest
    @ValueSource(ints = [0])
    fun outputAssetNameAndISIN(rowNumber: Int = 0) {
        val testDataRow = testData[rowNumber]
        val sheet = workbook.getSheetAt(0)
        val row = sheet.getRow(rowNumber)

        val expectedOutput = "--- " + testDataRow.product + " --- ISIN: " +
                testDataRow.ISIN + " ---\n"

        val assetName = row.getCell(3).stringCellValue
        val assetISIN = row.getCell(4).stringCellValue
        val output = "--- $assetName --- ISIN: $assetISIN ---\n"
        assertEquals(expectedOutput, output)
    }

    /**
     * A mock for the first half of the addTransactionsToLists() method
     * Assess that the following data can be accurately extracted from the test data spreadsheet for
     * buy and sell transactions:
     *  - Transaction date
     *  - Transaction ID
     *  - Total price of stock traded
     *
     * @Param rowNumber - The row number from the test data Excel spreadsheet that will be tested,
     * as supplied by the @ValueSource annotation
     */
    @ParameterizedTest
    @ValueSource(ints = [0, 1])
    fun getTransactionDateIDPriceTest(rowNumber: Int) {
        val testDataRow = testData[rowNumber]
        val sheet = workbook.getSheetAt(0)
        val row = sheet.getRow(rowNumber)

        // 1. Check that the date has been determined correctly
        val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
        val transactionDate = LocalDate.parse(row.getCell(0).stringCellValue, formatter)
        assertEquals(LocalDate.parse(testDataRow.date, formatter), transactionDate)

        // 2. Verify that the transaction ID has been successfully extracted
        val transactionID = row.getCell(11).stringCellValue
        assertEquals(testDataRow.orderID, transactionID)

        // 3. Verify that the transaction price has been extracted correctly
        val transactionPrice = if (row.getCell(5).stringCellValue.take(4) == "Sell") {
            row.getCell(8).numericCellValue
        } else -row.getCell(8).numericCellValue

        val testDataPrice = if (testDataRow.description.take(4) == "Sell") {
            testDataRow.changeValue
        } else -testDataRow.changeValue
        assertEquals(testDataPrice, transactionPrice)
    }

    /**
     * A mock for the second half of the addTransactionsToLists() method
     * Determine if the transaction should be merged with another
     *  The index will detail whether a matching transaction is found,
     *  or equal -1 if no match is found (a unique transaction).
     *  N.B. All transactions in the same direction (buy/sell), in the same company,
     *  on the same day should be treated as one transaction.
     *
     *  This test assesses unique transactions -> those that should be added to the list
     *
     * @Param transactionType - The type of transaction (buy or sell)
     */
    @ParameterizedTest
    @ValueSource(strings = ["Buy", "Sell"])
    fun addUniqueTransactionToListsTest(transactionType: String) {
        val transactionID = "id1"
        val transactionDate = now()
        val transactionQuantity = 10
        val transactionPrice = 1.11
        val buyTransactions = mutableListOf<Transaction>()
        val sellTransactions = mutableListOf<Transaction>()

        val index = if (transactionType == "Sell") { sellTransactions.indexOfFirst {
            it.date == transactionDate
        }} else buyTransactions.indexOfFirst { it.date == transactionDate }

        // If the transaction is unique (index equals -1) then build a new Transaction object
        if (index == -1) {
            val transaction = Transaction(mutableListOf(transactionID), transactionDate, transactionType, transactionQuantity, transactionPrice)
            // Add the new transaction to the relevant list
            if (transactionType == "Sell") assertTrue(sellTransactions.add(transaction))
            else assertTrue(buyTransactions.add(transaction))
        } else assertTrue(false)

        if (transactionType == "Sell") {
            assertEquals(sellTransactions.size, 1)
            assertTrue(sellTransactions[sellTransactions.size - 1].transactionIDs.contains(transactionID))
            assertEquals(sellTransactions[sellTransactions.size - 1].date, transactionDate)
            assertEquals(sellTransactions[sellTransactions.size - 1].quantity, transactionQuantity)
            assertEquals(sellTransactions[sellTransactions.size - 1].direction, transactionType)
            assertEquals(sellTransactions[sellTransactions.size - 1].price, transactionPrice)
        } else {
            assertEquals(buyTransactions.size, 1)
            assertTrue(buyTransactions[buyTransactions.size - 1].transactionIDs.contains(transactionID))
            assertEquals(buyTransactions[buyTransactions.size - 1].date, transactionDate)
            assertEquals(buyTransactions[buyTransactions.size - 1].quantity, transactionQuantity)
            assertEquals(buyTransactions[buyTransactions.size - 1].direction, transactionType)
            assertEquals(buyTransactions[buyTransactions.size - 1].price, transactionPrice)
        }
    }

    /**
     * A mock for the second half of the addTransactionsToLists() method
     * Determine if the transaction should be merged with another
     *  The index will detail whether a matching transaction is found,
     *  or equal -1 if no match is found (a unique transaction).
     *  N.B. All transactions in the same direction (buy/sell), in the same company,
     *  on the same day should be treated as one transaction.
     *
     *  This test assesses matching transactions -> those that should be combined with an existing transaction
     *
     * @Param transactionType - The type of transaction (buy or sell)
     */
    @ParameterizedTest
    @ValueSource(strings = ["Buy", "Sell"])
    fun addMatchingTransactionToListsTest(transactionType: String) {
        val existingBuyTransaction = Transaction(mutableListOf("id1"), now(), "Buy", 20, 2.22)
        val existingSellTransaction = Transaction(mutableListOf("id2"), now(), "Sell", 10, 1.11)
        val transactionID = "id1"
        val transactionDate = now()
        val transactionQuantity = 10
        val transactionPrice = 1.11
        // Need to add copies of the Transaction objects to the transaction lists
        // Otherwise subsequent modifications alter the value of raw object and disrupt the JUnit assertions
        val buyTransactions = mutableListOf(existingBuyTransaction.copy())
        val sellTransactions = mutableListOf(existingSellTransaction.copy())

        val index = if (transactionType == "Sell") { sellTransactions.indexOfFirst {
            it.date == transactionDate
        }} else buyTransactions.indexOfFirst { it.date == transactionDate }

        // If the transaction is unique (index equals -1) then build a new Transaction object
        if (index == -1) assertTrue(false)
        // Else incorporate the transaction details with the matching transaction
        else {
            if (transactionType == "Sell") {
                sellTransactions[index].transactionIDs.add(transactionID)
                sellTransactions[index].quantity += transactionQuantity
                sellTransactions[index].price += transactionPrice
            } else {
                buyTransactions[index].transactionIDs.add(transactionID)
                buyTransactions[index].quantity += transactionQuantity
                buyTransactions[index].price += transactionPrice
            }
        }

        if (transactionType == "Sell") {
            assertEquals(1, sellTransactions.size)
            assertTrue {
                sellTransactions[index].transactionIDs.containsAll(
                    listOf(existingSellTransaction.transactionIDs[0], transactionID))
            }
            assertEquals(sellTransactions[index].date, transactionDate)
            assertEquals(existingSellTransaction.quantity + transactionQuantity, sellTransactions[index].quantity)
            assertEquals(sellTransactions[index].direction, transactionType)
            assertEquals(existingSellTransaction.price + transactionPrice, sellTransactions[index].price)
        } else {
            assertEquals(1, buyTransactions.size)
            assertTrue {
                buyTransactions[index].transactionIDs.containsAll(
                    listOf(existingBuyTransaction.transactionIDs[0], transactionID))
            }
            assertEquals(buyTransactions[index].date, transactionDate)
            assertEquals(existingBuyTransaction.quantity + transactionQuantity, buyTransactions[index].quantity)
            assertEquals(buyTransactions[index].direction, transactionType)
            assertEquals(existingBuyTransaction.price + transactionPrice, buyTransactions[index].price)
        }
    }

    /**
     * Assess that the Transaction type can be accurately extracted from the test data spreadsheet for
     * buy and sell transactions.
     *
     * @Param rowNumber - The row number from the test data Excel spreadsheet that will be tested,
     * as supplied by the @ValueSource annotation
     * @throws UnknownTransactionTypeException - If the transaction type is neither buy nor sell
     */
    @ParameterizedTest
    @ValueSource(ints = [0, 1, 2])
    fun getTransactionTypeTest(rowNumber: Int) {
        val testDataDescription = testData[rowNumber].description
        val testDataTransactionType = when (testDataDescription.take(4)) {
            "Sell" -> "Sell"
            "Buy " -> "Buy"
            else -> "Invalid"
        }

        val sheet = workbook.getSheetAt(0)
        val row = sheet.getRow(rowNumber)
        val transactionDescription = row.getCell(5).stringCellValue

        fun getTransactionType(transactionDescription: String): String {
            return when (transactionDescription.take(4)) {
                "Sell" -> "Sell"
                "Buy " -> "Buy"
                else -> throw UnknownTransactionTypeException()
            }
        }

        // Check that UnknownTransactionTypeException is thrown if the transaction is not a buy or sell
        if (testDataTransactionType == "Invalid") {
            assertFailsWith<UnknownTransactionTypeException>(
                block = { getTransactionType(transactionDescription) })
        }
        // Else the transaction type should be extracted as normal
        else assertEquals(testDataTransactionType, getTransactionType(transactionDescription))
    }

    /**
     * Assess that the Transaction quantity can be accurately extracted from the test data spreadsheet for
     * buy and sell transactions.
     *
     * Acceptance criteria:
     *  - The quantity should be reported as an integer
     *
     * @Param transactionDescription - The transaction description, including how many shares were bought or sold
     *  Scenario 1: Buy transaction
     *  Scenario 2: Sell transaction
     */
    @ParameterizedTest
    @ValueSource(strings = ["Buy 10 Company A", "Sell 6 Company B"])
    fun getTransactionQuantity(transactionDescription: String) {
        // Remove Buy/Sell prefix from the description
        var truncatedDescription = transactionDescription.removePrefix("Buy ")
        truncatedDescription = truncatedDescription.removePrefix("Sell ")

        var quantity = ""
        for (element in truncatedDescription) {
            // Ignore commas and stop reading the quantity when whitespace is reached
            when (element.toString()) {
                " " -> break
                "," -> continue
                else -> quantity += element.toString()
            }
        }

        when (transactionDescription) {
            "Buy 10 Company A" -> assertEquals(10, quantity.toInt())
            "Sell 6 Company B" -> assertEquals( 6, quantity.toInt())
            else -> assertTrue(false)
        }
    }

    /* @Test
    fun testCreateAndReturnSheetFromWorkbookReturnsSheet() {
        val mockWorkbook: XSSFWorkbook = mock(XSSFWorkbook::class.java)
        val mockSheet = mock(XSSFSheet::class.java)
        val mockRow = mock(XSSFRow::class.java)
        val mockCell = mock(XSSFCell::class.java)
        `when`(mockWorkbook.createSheet(anyString())).thenReturn(mockSheet)
        `when`(mockSheet.createRow(anyInt())).thenReturn(mockRow)
        `when`(mockRow.createCell(anyInt())).thenReturn(mockCell)
        val sheet = CreateMockSpreadsheet().createMockSheet(mockWorkbook)
        println(sheet.physicalNumberOfRows)
        assertTrue {
            sheet.getRow(0).count() == 5
        }
    } */
}