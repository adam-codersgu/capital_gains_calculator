
import constants.ErrorConstants.UNKNOWN_DATE_COLUMN_VALUE
import logger.ErrorLogger
import model.FXTransaction
import model.Transaction
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFRow
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileInputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class ProcessSpreadsheet(spreadsheetFileLocation: String) {

    private val buyTransactions = mutableListOf<Transaction>()
    private val sellTransactions = mutableListOf<Transaction>()
    private val fxDebitTransactions = mutableListOf<FXTransaction>()
    private val fxCreditTransactions = mutableListOf<FXTransaction>()
    private val errorLogger = ErrorLogger()

    private val fxTransactionDescriptions = listOf(
        "FX Credit",
        "FX Debit"
    )

    init {
        val file = FileInputStream(File(spreadsheetFileLocation))
        val workbook = XSSFWorkbook(file)
        val sheet = workbook.getSheetAt(0)
        outputAssetNameAndISIN(sheet.getRow(0))
        addTransactionsToLists(sheet)
        printNumberOfDisposals()
        // fixme ProcessTransactions(buyTransactions, sellTransactions)
        errorLogger.printErrors()
    }

    /**
     * Prints the asset's name and ISIN to the console. Used to indicate which asset
     * the report is about.
     *
     * @param row an XSSFRow that contains columns for the asset's name and ISIN
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
     * @param sheet an XSSFSheet that contains rows of transactions
     */
    private fun addTransactionsToLists(sheet: XSSFSheet) {
        val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")

        for (row in sheet) {
            // TODO: Test that processing continues to the remainder of the sheet, even if a blank
            //  or non-date first column entry is encountered.
            val transactionDate = try {
                LocalDate.parse(row.getCell(0).stringCellValue, formatter)
            } catch (_: DateTimeParseException) {
                errorLogger.rowSkipped(row.rowNum, UNKNOWN_DATE_COLUMN_VALUE)
                continue
            }

            val transactionDescription = getTransactionDescription(row)
            val transactionType = getTransactionType(transactionDescription)

            // TODO: Note foreign currency dividends can't be processed (I don't think)
            when(transactionType) {
                "Buy" -> addBuyTransaction(row, transactionDate)
                "Sell" -> addSellTransaction(row, transactionDate)
                "FX Credit" -> addFxCreditTransaction(row, transactionDate)
                "FX Debit" -> addFxDebitTransaction(row, transactionDate)
            }
        }

        mergeBuyTransactions()
        mergeSellTransactions()

        // TODO:
        //  MERGE FX TRANSACTIONS
        //  APPLY FX VALUES TO FOREIGN TRANSACTIONS
    }

    private fun getTransactionDescription(row: Row): String = row.getCell(5).stringCellValue

    private fun getTransactionCurrency(row: Row): String = row.getCell(7).stringCellValue

    private fun getTransactionId(row: Row): String = row.getCell(11).stringCellValue

    private fun addBuyTransaction(row: Row, transactionDate: LocalDate) {
        val transactionDescription = getTransactionDescription(row)
        val transactionId = getTransactionId(row)
        val transactionType = "Buy"
        val transactionQuantity = getTransactionQuantity(transactionDescription)
        val transactionAmount = -row.getCell(8).numericCellValue
        val transactionCurrency = getTransactionCurrency(row)

        val transaction = Transaction(mutableListOf(transactionId),
            transactionDate, transactionType, transactionQuantity,
            transactionAmount, transactionCurrency)
        buyTransactions.add(transaction)
    }

    private fun addSellTransaction(row: Row, transactionDate: LocalDate) {
        val transactionDescription = getTransactionDescription(row)
        val transactionId = getTransactionId(row)
        val transactionType = "Sell"
        val transactionQuantity = getTransactionQuantity(transactionDescription)
        val transactionAmount = row.getCell(8).numericCellValue
        val transactionCurrency = getTransactionCurrency(row)

        val transaction = Transaction(mutableListOf(transactionId),
            transactionDate, transactionType, transactionQuantity,
            transactionAmount, transactionCurrency)
        sellTransactions.add(transaction)
    }

    private fun addFxDebitTransaction(row: Row, transactionDate: LocalDate) {
        val transactionId = getTransactionId(row)
        val transactionType = "FX Debit"
        val transactionAmount = -row.getCell(8).numericCellValue
        val transactionCurrency = getTransactionCurrency(row)

        val transaction = FXTransaction(mutableListOf(transactionId),
            transactionDate, transactionType, transactionAmount, transactionCurrency)
        fxDebitTransactions.add(transaction)
    }

    private fun addFxCreditTransaction(row: Row, transactionDate: LocalDate) {
        val transactionId = getTransactionId(row)
        val transactionType = "FX Credit"
        val transactionAmount = row.getCell(8).numericCellValue
        val transactionCurrency = getTransactionCurrency(row)

        val transaction = FXTransaction(mutableListOf(transactionId),
            transactionDate, transactionType, transactionAmount, transactionCurrency)
        fxCreditTransactions.add(transaction)
    }

    // TODO: TEST THIS -> Also do a test run of version 1 app and version 2. Compare output
    private fun mergeBuyTransactions() {
        val groupedTransactions = buyTransactions.groupBy {
            it.transactionIds
        }
        val transactionsSharingTransactionIds = groupedTransactions.values.filter {
            it.size > 1
        }
        for (listOfTransactions in transactionsSharingTransactionIds) {
            val newTransaction = listOfTransactions[0].copy(
                transactionIds = mutableListOf(),
                quantity = 0,
                amount = 0.00
            )
            for (transaction in listOfTransactions) {
                newTransaction.transactionIds.add(transaction.transactionIds[0])
                newTransaction.quantity += transaction.quantity
                newTransaction.amount += transaction.amount
            }
            buyTransactions.removeAll(listOfTransactions)
            buyTransactions.add(newTransaction)
        }
    }

    private fun mergeSellTransactions() {
        val groupedTransactions = sellTransactions.groupBy {
            it.transactionIds
        }
        val transactionsSharingTransactionIds = groupedTransactions.values.filter {
            it.size > 1
        }
        for (listOfTransactions in transactionsSharingTransactionIds) {
            val newTransaction = listOfTransactions[0].copy(
                transactionIds = mutableListOf(),
                quantity = 0,
                amount = 0.00
            )
            for (transaction in listOfTransactions) {
                newTransaction.transactionIds.add(transaction.transactionIds[0])
                newTransaction.quantity += transaction.quantity
                newTransaction.amount += transaction.amount
            }
            sellTransactions.removeAll(listOfTransactions)
            sellTransactions.add(newTransaction)
        }
    }

    /** Prints the total number of disposals of the asset to the console. */
    private fun printNumberOfDisposals() = println("Number of disposals: " + sellTransactions.size)

    /**
     * Extracts the transaction type for a given spreadsheet row.
     *
     * @param transactionDescription The value of the description field for the spreadsheet row.
     * The description will contain the transaction type.
     *
     * @return A String describing the transaction type or null if unrecognised.
     */
    private fun getTransactionType(transactionDescription: String): String? {
        return when {
            transactionDescription.startsWith("Sell") -> "Sell"
            transactionDescription.startsWith("Buy ") -> "Buy"
            fxTransactionDescriptions.contains(transactionDescription) -> transactionDescription
            else -> null
        }
    }

    /**
     * Extracts the transaction quantity for a given spreadsheet row.
     *
     * @param transactionDescription The value of the description field for the spreadsheet row.
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