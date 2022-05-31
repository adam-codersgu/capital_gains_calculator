import exceptions.UnknownTransactionTypeException
import model.Transaction
import org.apache.poi.xssf.usermodel.XSSFRow
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileInputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ProcessSpreadsheet(spreadsheetFileLocation: String) {

    private val buyTransactions = mutableListOf<Transaction>()
    private val sellTransactions = mutableListOf<Transaction>()

    init {
        val file = FileInputStream(File(spreadsheetFileLocation))
        val workbook = XSSFWorkbook(file)
        val sheet = workbook.getSheetAt(0)
        outputAssetNameAndISIN(sheet.getRow(0))
        addTransactionsToLists(sheet)
        printNumberOfDisposals()
        ProcessTransactions(buyTransactions, sellTransactions)
    }

    /**
     * Prints the asset's name and ISIN to the console. Used to indicate which asset
     * the report is about.
     *
     * @param  row  an XSSFRow that contains columns for the asset's name and ISIN
     */
    private fun outputAssetNameAndISIN(row: XSSFRow) {
        val assetName = row.getCell(3).stringCellValue
        val assetISIN = row.getCell(4).stringCellValue
        println("--- $assetName --- ISIN: $assetISIN ---\n")
    }

    /**
     * Extracts each transaction from the spreadsheet and assigns the
     * transactions to lists for purchases and disposals.
     *
     * @param  sheet an XSSFSheet that contains rows of transactions
     */
    private fun addTransactionsToLists(sheet: XSSFSheet) {
        val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")

        for (row in sheet) {
            val transactionDate = LocalDate.parse(row.getCell(0).stringCellValue, formatter)
            val transactionDescription = row.getCell(5).stringCellValue
            val transactionType = getTransactionType(transactionDescription)
            val transactionID = row.getCell(11).stringCellValue
            val transactionQuantity = getTransactionQuantity(transactionDescription)
            val transactionPrice = if (transactionType == "Sell") {
                row.getCell(8).numericCellValue
            } else -row.getCell(8).numericCellValue

            /* Determine if the transaction should be merged with another
                 The index will detail whether a matching transaction is found,
                 or equal -1 if no match is found (a unique transaction).
                 N.B. All transactions in the same direction (buy/sell), in the same company,
                 on the same day should be treated as one transaction. */
            val index = if (transactionType == "Sell") { sellTransactions.indexOfFirst {
                it.date == transactionDate
            }} else buyTransactions.indexOfFirst { it.date == transactionDate }

            // If the transaction is unique (index equals -1) then build a new model.Transaction object
            if (index == -1) {
                val transaction = Transaction(mutableListOf(transactionID), transactionDate, transactionType, transactionQuantity, transactionPrice)
                // Add the new transaction to the relevant list
                if (transactionType == "Sell") sellTransactions.add(transaction)
                else buyTransactions.add(transaction)
            }
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
        }
    }

    /**
     * Prints the total number of disposals of the asset to the console.
     */
    private fun printNumberOfDisposals() = println("Number of disposals: " + sellTransactions.size)

    /**
     * Extracts the transaction type (buy or sell) for a given spreadsheet row.
     *
     * @param  transactionDescription  The value of the description field for the spreadsheet row.
     * The description will contain the transaction type.
     * @return A String describing the transaction type (Buy or Sell)
     * @throws UnknownTransactionTypeException if the transaction type cannot be determined. This
     * exception may be thrown if the user includes non-buy/sell transactions in the spreadsheet.
     */
    private fun getTransactionType(transactionDescription: String): String {
        return when (transactionDescription.take(4)) {
            "Sell" -> "Sell"
            "Buy " -> "Buy"
            else -> throw UnknownTransactionTypeException()
        }
    }

    /**
     * Extracts the transaction quantity for a given spreadsheet row.
     *
     * @param  transactionDescription  The value of the description field for the spreadsheet row.
     * The description will contain the transaction quantity.
     * @return An Int detailing the transaction quantity.
     */
    private fun getTransactionQuantity(transactionDescription: String): Int {
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
        return quantity.toInt()
    }
}