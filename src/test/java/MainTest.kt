
import model.SpreadsheetRow
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.junit.jupiter.MockitoExtension
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
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

    companion object {
        val ROW0_VALUE = SpreadsheetRow(
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
    }

    /**
     * Before the tests begin, we should create a testing Excel spreadsheet containing example stock transactions.
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

        val row0 = xlWs.createRow(0)
        val content = ROW0_VALUE.getData()

        // TODO: CHECK HOW YOU HANDLED OBJECTS IN THE D4D PROJECT

        for (i in 0..11) {
            val cell = row0.createCell(i)
            when (val item = content[i]) {
                is String -> cell.setCellValue(item)
                is Double -> cell.setCellValue(item)
            }
        }

        //Write file:
        val outputStream = FileOutputStream(testSpreadsheet.toFile())
        xlWb.apply {
            write(outputStream)
            close()
        }

        fileInputStream = FileInputStream(testSpreadsheet.toFile())
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
     */
    @Test
    fun testExampleSpreadsheetExists() {
        assertTrue {
            this::testSpreadsheet.isInitialized
            Files.exists(testSpreadsheet)
        }
    }

    /**
     * Verify that the test data spreadsheet contains the appropriate data
     */
    @Test
    fun testExampleSpreadsheetContainsTestData() {
        val workbook = XSSFWorkbook(fileInputStream)
        val sheet = workbook.getSheetAt(0)

        val row1 = sheet.getRow(0)
        val assetName = row1.getCell(3).stringCellValue
        val assetISIN = row1.getCell(4).stringCellValue

        assertEquals(ROW0_VALUE.product, assetName)
        assertEquals(ROW0_VALUE.ISIN, assetISIN)
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