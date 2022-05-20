
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.junit.jupiter.MockitoExtension
import java.io.File
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class MainTest {

    /**
     * A file chooser window should open and allow the user to select an Excel
     * spreadsheet containing transaction data.
     *
     * Acceptance criteria:
     *  The user should be prompted to select an Excel spreadsheet from the files on their computer
     *  The user should only be able to select valid spreadsheet file types (.xls or .xlsx)
     *
     * Test scenarios:
     * 1) Attempt to select a non-spreadsheet file (e.g. an image)
     *  Expected result: The user should not be able to select a non-spreadsheet file
     *
     * 2) Select a valid spreadsheet file (the file must have an extension of .xls or .xlsx)
     *  Expected result: The file should be accepted and the test should pass.
     */

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
     *  - Transaction ID
     *  - Transaction date
     *  - Transaction type
     *  - Quantity traded
     *  - Total price of stock traded
     *
     * @Param rowNumber - The row number from the test data Excel spreadsheet that will be tested,
     * as supplied by the @ValueSource annotation
     */

    /* @ParameterizedTest
    @ValueSource(ints = [0, 1])
    fun getTransactionDataFromSpreadsheet(rowNumber: Int) {
        val testDataRow = ROWS[rowNumber]
        val sheet = workbook.getSheetAt(0)
        val row = sheet.getRow(rowNumber)

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
    } */

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
    fun addTransactionToListOfTransactions(rowNumber: Int) {

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