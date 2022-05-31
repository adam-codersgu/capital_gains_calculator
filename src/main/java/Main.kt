
import java.io.File
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.system.exitProcess

/**
 * @author Adam Hawke
 *
 * An application to help report stock transactions for an HMRC Capital Gains tax report.
 *
 * This application is designed for stock transactions completed using the broker DEGIRO.
 *
 * This application is for personal use only.
 * This application is not authorised by, or affiliated with, HMRC or DEGIRO.
 *
 * This application's output is for reference purposes only. Use at your own discretion.
 * The accuracy of the application's output cannot be guaranteed, and the developer accepts
 * no responsibility for the use of the application's output in any circumstances, including tax returns.
 *
 * Instructions:
 *
 *  - Export account statement from DEGIRO
 *  - Isolate a given assets transactions (buy + sell) in a separate spreadsheet
 *  - Run the spreadsheet through the application and refer to the console output for a report
 *
 */

fun main() {
    println("Application ready")

    openFileChooserDialogWindow()
}

/**
 * Opens a file chooser dialog window so the user can select an
 * Excel spreadsheet containing transaction data.
 */
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
        ProcessSpreadsheet(file.absolutePath)
    }
    exitProcess(0)
}