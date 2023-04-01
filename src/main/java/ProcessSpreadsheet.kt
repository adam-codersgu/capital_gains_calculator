import constants.ErrorConstants.UNKNOWN_DATE_COLUMN_VALUE
import constants.ErrorConstants.UNSUPPORTED_DESCRIPTION_COLUMN_VALUE
import logger.ErrorLogger
import model.FXTransaction
import model.Transaction
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFRow
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileInputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class ProcessSpreadsheet(spreadsheetFileLocation: String) {

    companion object {
        private const val DEFAULT_CURRENCY = "GBP"
    }

    private val buyTransactions = mutableListOf<Transaction>()
    private val sellTransactions = mutableListOf<Transaction>()
    private val fxTransactions = mutableListOf<FXTransaction>()
    private var transactionFees = 0.00
    private val errorLogger = ErrorLogger()

    private val fxTransactionDescriptions = listOf(
        "DEGIRO Transaction and/or third party fees",
        "FX Credit",
        "FX Debit"
    )

    init {
        val file = FileInputStream(File(spreadsheetFileLocation))
        val workbook = XSSFWorkbook(file)
        val sheet = workbook.getSheetAt(0)
        outputAssetNameAndISIN(sheet.getRow(0))
        addTransactionsToLists(sheet)
        // TODO: See if these print methods can be delegated to another class
        printNumberOfDisposals()
        ProcessTransactions(buyTransactions, sellTransactions)
        printFees()
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
            val transactionDate = try {
                LocalDate.parse(row.getCell(0).stringCellValue, formatter)
            } catch (_: DateTimeParseException) {
                errorLogger.rowSkipped(row.rowNum, UNKNOWN_DATE_COLUMN_VALUE)
                continue
            }

            val transactionDescription = getTransactionDescription(row)

            // TODO: Note foreign currency dividends can't be processed (I don't think)
            //  Print a warning to the console about dividends in foreign currencies?
            when (getTransactionType(transactionDescription)) {
                "Buy" -> addBuyTransaction(row, transactionDate)
                "Sell" -> addSellTransaction(row, transactionDate)
                "DEGIRO Transaction and/or third party fees" -> addTransactionFee(row)
                "FX Credit" -> addFxCreditTransaction(row, transactionDate)
                "FX Debit" -> addFxDebitTransaction(row, transactionDate)
                else -> {
                    errorLogger.rowSkipped(row.rowNum,
                        UNSUPPORTED_DESCRIPTION_COLUMN_VALUE + transactionDescription)
                    continue
                }
            }
        }

        val buyTransactionsSharingId = buyTransactions.groupBy { it.transactionIds }.values.filter { it.size > 1 }
        mergeBuyTransactions(buyTransactionsSharingId)
        val sellTransactionsSharingId = sellTransactions.groupBy { it.transactionIds }.values.filter { it.size > 1 }
        mergeSellTransactions(sellTransactionsSharingId)

        val fxCreditTransactions = fxTransactions.filter { it.type == "FX Credit" &&
                !it.transactionId.isNullOrBlank() }.groupBy { it.transactionId }.values.filter { it.size > 1 }
        mergeFxTransactions(fxCreditTransactions)
        val fxDebitTransactions = fxTransactions.filter { it.type == "FX Debit" &&
                !it.transactionId.isNullOrBlank() }.groupBy { it.transactionId }.values.filter { it.size > 1 }
        mergeFxTransactions(fxDebitTransactions)
        integrateFxValuesForBuyTransactions()
        integrateFxValuesForSellTransactions()

        val buyTransactionsSharingDate = buyTransactions.groupBy { it.date }.values.filter { it.size > 1 }
        mergeBuyTransactions(buyTransactionsSharingDate)
        val sellTransactionsSharingDate = sellTransactions.groupBy { it.date }.values.filter { it.size > 1 }
        mergeSellTransactions(sellTransactionsSharingDate)
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

    /**
     * Add a transaction fee to the running fee total.
     *
     * @param row A spreadsheet row containing the details of the fee.
     * @throw Exception if the transaction fee is reported in a currency other than the
     * default currency.
     */
    private fun addTransactionFee(row: Row) {
        if (getTransactionCurrency(row) != DEFAULT_CURRENCY) {
            throw Exception("A transaction fee for an invalid currency was found on row "
                + row.rowNum)
        }

        transactionFees += -row.getCell(8).numericCellValue
    }

    private fun addFxDebitTransaction(row: Row, transactionDate: LocalDate) {
        val transactionId = getTransactionId(row)
        val transactionType = "FX Debit"
        val transactionAmount = -row.getCell(8).numericCellValue
        val transactionCurrency = getTransactionCurrency(row)

        val transaction = FXTransaction(transactionId, transactionDate,
            transactionType, transactionAmount, transactionCurrency)
        fxTransactions.add(transaction)
    }

    private fun addFxCreditTransaction(row: Row, transactionDate: LocalDate) {
        val transactionId = getTransactionId(row)
        val transactionType = "FX Credit"
        val transactionAmount = row.getCell(8).numericCellValue
        val transactionCurrency = getTransactionCurrency(row)

        val transaction = FXTransaction(transactionId, transactionDate,
            transactionType, transactionAmount, transactionCurrency)
        fxTransactions.add(transaction)
    }

    private fun mergeBuyTransactions(transactions: List<List<Transaction>>) {
        for (listOfTransactions in transactions) {
            val newTransaction = listOfTransactions[0].copy(
                quantity = 0,
                amount = 0.00
            )
            for (transaction in listOfTransactions) {
                newTransaction.quantity += transaction.quantity
                newTransaction.amount += transaction.amount
            }
            buyTransactions.removeAll(listOfTransactions)
            buyTransactions.add(newTransaction)
        }
    }

    private fun mergeSellTransactions(transactions: List<List<Transaction>>) {
        for (listOfTransactions in transactions) {
            val newTransaction = listOfTransactions[0].copy(
                quantity = 0,
                amount = 0.00
            )
            for (transaction in listOfTransactions) {
                newTransaction.quantity += transaction.quantity
                newTransaction.amount += transaction.amount
            }
            sellTransactions.removeAll(listOfTransactions)
            sellTransactions.add(newTransaction)
        }
    }

    private fun mergeFxTransactions(transactions: List<List<FXTransaction>>) {
        for (listOfTransactions in transactions) {
            val newTransaction = listOfTransactions[0].copy(amount = 0.00)
            for (transaction in listOfTransactions) {
                newTransaction.amount += transaction.amount
            }
            fxTransactions.removeAll(listOfTransactions)
            fxTransactions.add(newTransaction)
        }
    }

    private fun integrateFxValuesForBuyTransactions() {
        val foreignBuyTransactions = buyTransactions.filterNot { it.currency == DEFAULT_CURRENCY }

        for (transaction in foreignBuyTransactions) {
            fxTransactions.find {
                it.transactionId == transaction.transactionIds[0] && it.type == "FX Debit"
            }?.let {
                val newTransaction = transaction.copy(
                    currency = DEFAULT_CURRENCY,
                    amount = it.amount
                )
                buyTransactions.removeIf { t ->
                    t.transactionIds.contains(it.transactionId) && t.currency != DEFAULT_CURRENCY
                }
                buyTransactions.add(newTransaction)
            }
        }

        buyTransactions.find {
            it.currency != DEFAULT_CURRENCY
        }?.let { transaction ->
            throw Exception("Foreign currency buy transaction found that could not be matched " +
                    "with an FX Debit transaction: $transaction")
        }
    }

    private fun integrateFxValuesForSellTransactions() {
        val foreignSellTransactions = sellTransactions.filterNot { it.currency == DEFAULT_CURRENCY }

        for (transaction in foreignSellTransactions) {
            fxTransactions.find {
                transaction.transactionIds.contains(it.transactionId) &&
                        it.type == "FX Credit"
            }?.let {
                val newTransaction = transaction.copy(
                    currency = DEFAULT_CURRENCY,
                    amount = it.amount
                )
                sellTransactions.removeIf { transaction ->
                    transaction.transactionIds.contains(it.transactionId) &&
                            transaction.currency != DEFAULT_CURRENCY
                }
                sellTransactions.add(newTransaction)
            }
        }

        sellTransactions.find {
            it.currency != DEFAULT_CURRENCY
        }?.let { transaction ->
            throw Exception("Foreign currency sell transaction found that could not be matched " +
                    "with an FX Credit transaction: $transaction")
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

    private fun printFees() {
        val transactionFeesRounded = BigDecimal(transactionFees)
            .setScale(2, RoundingMode.HALF_EVEN).toString()
        println("Transaction fees = £$transactionFeesRounded")
    }
}