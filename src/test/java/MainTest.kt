
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
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