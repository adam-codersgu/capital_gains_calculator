
import model.SpreadsheetRow
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.junit.jupiter.MockitoExtension
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MainTest {

    private lateinit var fileInputStream: FileInputStream
    private lateinit var testSpreadsheet: Path
    private lateinit var workbook: XSSFWorkbook

    /**
     * Companion object where example test data is defined:
     *  ROW0 -> Buy 10 shares company A
     *  ROW1 -> Sell 10 shares company A same day as ROW0
     */
    companion object {
        private val ROW0_VALUE = SpreadsheetRow(
            "16-05-2022",
            "09:22",
            "16-05-2022",
            "Company A",
            "AB123456789",
            "Buy 10 Company A",
            null,
            "GBP",
            -199.99,
            "GBP",
            -199.99,
            "abcdefg-123456-hijk7890"
        )
        private val ROW1_VALUE = SpreadsheetRow(
            "16-05-2022",
            "09:55",
            "16-05-2022",
            "Company A",
            "AB123456789",
            "Sell 10 Company A",
            null,
            "GBP",
            206.54,
            "GBP",
            206.54,
            "bvcxza-654321-lkjhgf"
        )
        val ROWS = arrayOf(ROW0_VALUE, ROW1_VALUE)
    }

    /**
     * Before the tests begin, we should create a testing Excel spreadsheet containing example stock transactions.
     * The example stock transactions are defined in the companion object.
     *
     * @Param tempDir - A temporary directory created by JUnit that will store the Excel spreadsheet
     */
    
    @BeforeAll
    private fun setup(@TempDir tempDir: Path) {
        testSpreadsheet = tempDir.resolve("test_stock_transactions.xlsx")

        //Instantiate Excel workbook:
        val xlWb = XSSFWorkbook()
        //Instantiate Excel worksheet:
        val xlWs = xlWb.createSheet()

        for ((index, row) in ROWS.withIndex()) {
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

        //Write file:
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
        assertTrue {
            this::testSpreadsheet.isInitialized
            Files.exists(testSpreadsheet)
            this::fileInputStream.isInitialized
            this::workbook.isInitialized
        }
    }

    /**
     * Verify that the test data spreadsheet contains the appropriate data
     */
    @Test
    fun testExampleSpreadsheetContainsTestData() {
        val sheet = workbook.getSheetAt(0)

        val row1 = sheet.getRow(0)
        val assetName = row1.getCell(3).stringCellValue
        val assetISIN = row1.getCell(4).stringCellValue

        assertEquals(ROWS[0].product, assetName)
        assertEquals(ROWS[0].ISIN, assetISIN)
    }

    /**
     * Test the openFileChooserDialogWindow method
     *
     * Acceptance criteria:
     *  The user should be prompted to select a spreadsheet from the files on their computer
     *  The user should only be able to select valid spreadsheet file types (.xls or .xlsx)
     *
     * Test scenarios:
     * 1) Attempt to select a non-spreadsheet file (e.g. an image)
     *  Expected result: The user should not be able to select a non-spreadsheet file
     *
     * 2) Select a valid spreadsheet file (the file must have an extension of .xls or .xlsx)
     *  Expected result: The file should be accepted and the test should pass.
     */

    // Disabled because the test is validated as working
    // It is not necessary to re-run the test each time the test class is initialised
    @Disabled
    @Test
    fun openFileChooserDialogWindow() {
        val filter = FileNameExtensionFilter("Excel file (*.xls;*.xlsx)", "xls", "xlsx")
        val fileChooser = JFileChooser().apply {
            dialogTitle = "Select file"
            currentDirectory = File(System.getProperty("user.home"))
            isAcceptAllFileFilterUsed = false
            fileFilter = filter
        }

        val frame = JFrame()
        val result = fileChooser.showOpenDialog(frame)
        if (result == JFileChooser.APPROVE_OPTION) {
            val file = fileChooser.selectedFile
            assertTrue {
                file.path.endsWith(".xls") || file.path.endsWith(".xlsx")
            }
        }
    }

    /**
     * Assess that the following data can be accurately extracted from the test data spreadsheet for
     * buy and sell transactions:
     *  - Asset name
     *  - Asset ISIN
     *  - Transaction ID
     *  - Transaction date
     *  - Transaction type
     *  - Quantity traded
     *  - Total price of stock traded
     *
     * Acceptance criteria:
     *  The asset name and ISIN should be reported in the format provided below:
     *      --- {Asset Name} --- ISIN: {ISIN} ---
     *  A line break should be included after the asset name and ISIN
     *
     * @Param rowNumber - The row number from the test data Excel spreadsheet that will be tested,
     * as supplied by the @ValueSource annotation
     */

    @ParameterizedTest
    @ValueSource(ints = [0, 1])
    fun getTransactionDataFromSpreadsheet(rowNumber: Int) {
        val testDataRow = ROWS[rowNumber]
        val sheet = workbook.getSheetAt(0)
        val row = sheet.getRow(rowNumber)

        // 1. Verify that the asset name and ISIN are extracted and processed correctly
        val expectedOutput = "--- " + testDataRow.product + " --- ISIN: " +
                testDataRow.ISIN + " ---\n"

        val assetName = row.getCell(3).stringCellValue
        val assetISIN = row.getCell(4).stringCellValue
        val output = "--- $assetName --- ISIN: $assetISIN ---\n"
        assertEquals(expectedOutput, output)

        // 2. Verify that the transaction ID has been successfully extracted
        val transactionID = row.getCell(11).stringCellValue
        assertEquals(testDataRow.orderID, transactionID)

        // 3. Check that the data has been determined correctly
        val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
        val date = LocalDate.parse(row.getCell(0).stringCellValue, formatter)
        assertEquals(LocalDate.parse(testDataRow.date, formatter), date)

        // 4. Determine the correct transaction type
        val testDataDescription = testDataRow.description
        val description = row.getCell(5).stringCellValue

        val testDataTransactionType = when (testDataDescription.take(4)) {
            "Sell" -> "Sell"
            "Buy " -> "Buy"
            else -> "Unknown direction"
        }
        val transactionType = when (description.take(4)) {
            "Sell" -> "Sell"
            "Buy " -> "Buy"
            else -> "Unknown direction"
        }
        assertEquals(testDataTransactionType, transactionType)

        // 5. Extract the quantity of stock traded
        // 6. Extract the price of the stock traded
        fun determineQuantity(quantityField: String): String {
            var quantity = ""
            for (element in quantityField) {
                // Ignore commas and stop reading the quantity when whitespace is reached
                when (element.toString()) {
                    " " -> break
                    "," -> continue
                    else -> quantity += element.toString()
                }
            }
            return quantity
        }

        var testDataQuantity = ""; var testDataPrice = testDataRow.changeValue
        when (testDataTransactionType) {
            "Sell" -> {
                testDataQuantity = determineQuantity(testDataDescription.substring(5))
                testDataPrice = testDataRow.changeValue
            }
            "Buy" -> {
                testDataQuantity = determineQuantity(testDataDescription.substring(4))
                testDataPrice = -testDataRow.changeValue
            }
        }

        var quantity = ""; var price = 0.00
        when (transactionType) {
            "Sell" -> {
                quantity = determineQuantity(description.substring(5))
                price = row.getCell(8).numericCellValue
            }
            "Buy" -> {
                quantity = determineQuantity(description.substring(4))
                price = -row.getCell(8).numericCellValue
            }
        }

        assertEquals(testDataQuantity, quantity)
        assertEquals(testDataPrice, price)
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