
import constants.ErrorConstants.UNKNOWN_DATE_COLUMN_VALUE
import constants.ErrorConstants.UNSUPPORTED_DESCRIPTION_COLUMN_VALUE
import model.DividendTransaction
import model.FXTransaction
import model.Section104
import model.Transaction
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import util.ErrorLogger
import util.PrintUtils
import java.io.File
import java.io.FileInputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.system.exitProcess

/**
 * TODO - We need a test for this class that confirms the value of the outstanding buy/sell
 *      transactions are updated accordingly after merging FX amounts.
 */
class ProcessSpreadsheet(spreadsheetFileLocation: String, private val section104: Section104) {

    companion object {
        private const val DEFAULT_CURRENCY = "GBP"
        private const val DIVIDEND = "Dividend"
        private const val DIVIDEND_TAX = "Dividend Tax"
        private const val FX_CREDIT = "FX Credit"
        private const val FX_DEBIT = "FX Debit"
        private const val TRANSACTION_FEE = "DEGIRO Transaction and/or third party fees"
    }

    private val buyTransactions = mutableListOf<Transaction>()
    private val sellTransactions = mutableListOf<Transaction>()
    private val foreignDividends = mutableListOf<DividendTransaction>()
    private val dividendTaxes = mutableListOf<DividendTransaction>()
    private val fxTransactions = mutableListOf<FXTransaction>()
    private var transactionFees = 0.00
    private var dividends = 0.00
    private val errorLogger = ErrorLogger()
    private val dateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")

    private val validTransactionDescriptions = listOf(
        DIVIDEND,
        DIVIDEND_TAX,
        FX_CREDIT,
        FX_DEBIT,
        TRANSACTION_FEE
    )

    init {
        val file = FileInputStream(File(spreadsheetFileLocation))
        val workbook = XSSFWorkbook(file)
        val sheet = workbook.getSheetAt(0)
        outputAssetNameAndISIN(sheet)
        addTransactionsToLists(sheet)
        printReportSummary()
        ProcessTransactions(buyTransactions, sellTransactions, section104)
        finishProcessing()
    }

    /**
     * Prints the asset's name and ISIN to the console. Used to indicate which asset
     * the report is about.
     *
     * @param sheet an XSSFSheet that contains the columns for the asset's name and ISIN
     */
    private fun outputAssetNameAndISIN(sheet: XSSFSheet) {
        var i = 0
        var assetISIN = "Unknown ISIN"
        var assetName = "Unknown asset name"
        do {
            val row = sheet.getRow(i) ?: break
            val isin = row.getCell(4).stringCellValue
            if (isin.isNotBlank() && isin != "ISIN") {
                assetISIN = isin
            }
            val name = row.getCell(3).stringCellValue
            if (name.isNotBlank() && name != "Product") {
                assetName = name
            }
            ++i
        } while (isin.isBlank() || isin == "ISIN" || name.isBlank() ||
                name == "Product")

        println("--- $assetName --- ISIN: $assetISIN ---\n")
    }

    /**
     * Extracts each transaction from the spreadsheet and assigns the
     * transactions to lists for purchases and disposals.
     *
     * @param sheet an XSSFSheet that contains rows of transactions
     */
    private fun addTransactionsToLists(sheet: XSSFSheet) {
        for (row in sheet) {
            val transactionDate = try {
                LocalDate.parse(row.getCell(0).stringCellValue, dateFormatter)
            } catch (_: DateTimeParseException) {
                errorLogger.rowSkipped(row.rowNum, UNKNOWN_DATE_COLUMN_VALUE)
                continue
            } catch (_: NullPointerException) {
                errorLogger.rowSkipped(row.rowNum, "NullPointerException encountered " +
                        "when trying to extract the date.\n" +
                        "Will not process any further rows.\n")
                break
            }

            val transactionDescription = getTransactionDescription(row)

            when (getTransactionType(transactionDescription)) {
                "Buy" -> addBuyTransaction(row, transactionDate)
                "Sell" -> addSellTransaction(row, transactionDate)
                TRANSACTION_FEE -> addTransactionFee(row)
                FX_CREDIT -> addFxCreditTransaction(row, transactionDate)
                FX_DEBIT -> addFxDebitTransaction(row, transactionDate)
                DIVIDEND -> addDividend(row)
                DIVIDEND_TAX -> addDividendTax(row)
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

        val fxCreditTransactions = fxTransactions.filter { it.type == FX_CREDIT &&
                !it.transactionId.isNullOrBlank() }.groupBy { it.transactionId }.values.filter { it.size > 1 }
        mergeFxTransactions(fxCreditTransactions)
        val fxDebitTransactions = fxTransactions.filter { it.type == FX_DEBIT &&
                !it.transactionId.isNullOrBlank() }.groupBy { it.transactionId }.values.filter { it.size > 1 }
        mergeFxTransactions(fxDebitTransactions)
        integrateFxValuesForBuyTransactions()
        integrateFxValuesForSellTransactions()

        val buyTransactionsSharingDate = buyTransactions.groupBy { it.date }.values.filter { it.size > 1 }
        mergeBuyTransactions(buyTransactionsSharingDate)
        val sellTransactionsSharingDate = sellTransactions.groupBy { it.date }.values.filter { it.size > 1 }
        mergeSellTransactions(sellTransactionsSharingDate)

        mergeFxTransactionsForDividends()
    }

    private fun getTransactionDescription(row: Row): String = row.getCell(5).stringCellValue

    private fun getTransactionCurrency(row: Row): String = row.getCell(7).stringCellValue

    private fun getTransactionId(row: Row): String = row.getCell(11).stringCellValue

    private fun addBuyTransaction(row: Row, transactionDate: LocalDate) {
        val transactionType = "Buy"
        val transactionAmount = -getColumn8Value(row)
        val transaction = buildTransaction(row, transactionDate, transactionType, transactionAmount)
        buyTransactions.add(transaction)
    }

    private fun addSellTransaction(row: Row, transactionDate: LocalDate) {
        val transactionType = "Sell"
        val transactionAmount = getColumn8Value(row)
        val transaction = buildTransaction(row, transactionDate, transactionType, transactionAmount)
        sellTransactions.add(transaction)
    }

    private fun buildTransaction(row: Row, date: LocalDate, type: String, amount: Double): Transaction {
        val transactionDescription = getTransactionDescription(row)
        val transactionId = getTransactionId(row)
        val transactionQuantity = getTransactionQuantity(transactionDescription)
        val transactionCurrency = getTransactionCurrency(row)

        return Transaction(mutableListOf(transactionId),
            date, type, transactionQuantity, transactionQuantity,
            amount, amount, transactionCurrency)
    }

    private fun buildDividendTransaction(row: Row, type: String, amount: Double): DividendTransaction {
        val date = LocalDate.parse(row.getCell(0).stringCellValue, dateFormatter)
        return DividendTransaction(date, type, amount, getTransactionCurrency(row))
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

        transactionFees += -getColumn8Value(row)
    }

    private fun addDividend(row: Row) {
        if (getTransactionCurrency(row) == DEFAULT_CURRENCY) {
            dividends += getColumn8Value(row)
        } else {
            val amount = getColumn8Value(row)
            foreignDividends.add(buildDividendTransaction(row, DIVIDEND, amount))
        }
    }

    private fun addDividendTax(row: Row) {
        val amount = -getColumn8Value(row)
        dividendTaxes.add(buildDividendTransaction(row, DIVIDEND_TAX, amount))
    }

    private fun getColumn8Value(row: Row): Double {
        return try {
            row.getCell(8).numericCellValue
        } catch (_: java.lang.IllegalStateException) {
            val value = row.getCell(8).stringCellValue
            value.replace(",", "").toDouble()
        }
    }

    private fun addFxDebitTransaction(row: Row, transactionDate: LocalDate) {
        val transactionAmount = -getColumn8Value(row)

        val transaction = buildFXTransaction(row, transactionDate,
            FX_DEBIT, transactionAmount)
        fxTransactions.add(transaction)
    }

    private fun addFxCreditTransaction(row: Row, transactionDate: LocalDate) {
        val transactionAmount = getColumn8Value(row)

        val transaction = buildFXTransaction(row, transactionDate,
            FX_CREDIT, transactionAmount)
        fxTransactions.add(transaction)
    }

    private fun buildFXTransaction(row: Row, date: LocalDate, type: String, amount: Double): FXTransaction {
        val transactionId = getTransactionId(row)
        val valueDate = LocalDate.parse(row.getCell(2).stringCellValue, dateFormatter)
        val transactionCurrency = getTransactionCurrency(row)

        return FXTransaction(transactionId, date, valueDate,
            type, amount, transactionCurrency)
    }

    private fun mergeBuyTransactions(transactions: List<List<Transaction>>) {
        for (transactionsToMerge in transactions) {
            buyTransactions.removeAll(transactionsToMerge)
            buyTransactions.add(mergeTransactions(transactionsToMerge))
        }
    }

    private fun mergeSellTransactions(transactions: List<List<Transaction>>) {
        for (transactionsToMerge in transactions) {
            sellTransactions.removeAll(transactionsToMerge)
            sellTransactions.add(mergeTransactions(transactionsToMerge))
        }
    }

    private fun mergeTransactions(transactionsToMerge: List<Transaction>): Transaction {
        val newTransaction = transactionsToMerge[0].copy(
            transactionIds = mutableListOf(),
            initialQuantity = transactionsToMerge.sumOf { it.initialQuantity },
            remainingQuantity = transactionsToMerge.sumOf { it.remainingQuantity },
            initialValue = transactionsToMerge.sumOf { it.remainingValue },
            remainingValue = transactionsToMerge.sumOf { it.remainingValue }
        )

        if (newTransaction.initialQuantity != newTransaction.remainingQuantity) {
            throw IllegalStateException("The initial quantity and unmatched quantity of the merged transaction" +
                    "do not match: $newTransaction")
        }

        val transactionIds = mutableListOf<String>()
        transactionsToMerge.forEach {
            transactionIds.addAll(it.transactionIds)
        }

        newTransaction.transactionIds.addAll(transactionIds.toSet())
        return newTransaction
    }

    private fun mergeFxTransactions(transactions: List<List<FXTransaction>>) {
        for (transactionsToMerge in transactions) {
            val newTransaction = transactionsToMerge[0].copy(amount = transactionsToMerge.sumOf { it.amount })
            fxTransactions.removeAll(transactionsToMerge)
            fxTransactions.add(newTransaction)
        }
    }

    private fun integrateFxValuesForBuyTransactions() {
        val foreignBuyTransactions = buyTransactions.filterNot { it.currency == DEFAULT_CURRENCY }

        for (transaction in foreignBuyTransactions) {
            fxTransactions.find {
                transaction.transactionIds.contains(it.transactionId) && it.type == FX_DEBIT
            }?.let {
                val newTransaction = transaction.copy(
                    currency = DEFAULT_CURRENCY,
                    initialValue = it.amount,
                    remainingValue = it.amount
                )
                buyTransactions.remove(transaction)
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
                        it.type == FX_CREDIT
            }?.let {
                val newTransaction = transaction.copy(
                    currency = DEFAULT_CURRENCY,
                    initialValue = it.amount,
                    remainingValue = it.amount
                )
                sellTransactions.remove(transaction)
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

    // TODO Need to streamline this method if possible
    private fun mergeFxTransactionsForDividends() {
        if (foreignDividends.isEmpty()) return

        println("Commencing foreign dividend processing.")

        for (dividend in foreignDividends) {
            val tax = dividendTaxes.find { tax ->
                tax.date == dividend.date
            }

            if (tax == null) {
                println("No dividend tax found for ${dividend.date}")
            } else {
                val fxCredit = fxTransactions.find { fx ->
                    fx.type == FX_CREDIT && fx.valueDate == dividend.date && fx.transactionId.isNullOrBlank()
                }
                if (fxCredit == null) {
                    println("No FX Credit found for ${dividend.date}")
                }

                val fxDebit = fxTransactions.find { fx ->
                    fx.type == FX_DEBIT && fx.valueDate == dividend.date && fx.transactionId.isNullOrBlank()
                }
                if (fxDebit == null) {
                    println("No FX Debit found for ${dividend.date}")
                }

                if (dividend.amount - tax.amount != fxDebit?.amount) {
                    println("ERROR: ${dividend.amount} - ${tax.amount} != ${fxDebit?.amount}")
                    exitProcess(1)
                }

                val proportionTaxed = tax.amount / dividend.amount
                val dividendInGBP = fxCredit?.amount?.div(1 - proportionTaxed) ?: exitProcess(2)
                val dividendTaxInGBP = dividendInGBP.minus(fxCredit.amount)
                println("Dividend GBP: $dividendInGBP. Dividend tax GBP: $dividendTaxInGBP\n")

                foreignDividends.add(dividend.copy(amount = dividendInGBP, currency = DEFAULT_CURRENCY))
                dividendTaxes.add(tax.copy(amount = dividendTaxInGBP, currency = DEFAULT_CURRENCY))
                foreignDividends.remove(dividend)
                dividendTaxes.remove(tax)
            }
        }

        if (foreignDividends.any { it.currency != DEFAULT_CURRENCY }) {
            println("There remain dividends in a foreign currency.")
            exitProcess(2)
        }

        if (dividendTaxes.any { it.currency != DEFAULT_CURRENCY }) {
            println("There remain dividend taxes in a foreign currency.")
            exitProcess(2)
        }
    }

    /**
     * Prints information about the report including the total number of disposals, total
     * purchase costs, total disposal proceeds and incurred fees.
     */
    private fun printReportSummary() {
        println("Number of disposals: ${sellTransactions.size}")
        println("Transaction fees = £${PrintUtils.roundTo2DpString(transactionFees)}")
        println("Disposal proceeds: £${PrintUtils
            .roundTo2DpString(sellTransactions.sumOf { it.remainingValue })}")
        println("Purchase costs: £${PrintUtils
            .roundTo2DpString(buyTransactions.sumOf { it.remainingValue } + section104.price)}\n")

        println("GBP dividends = £${PrintUtils.roundTo2DpString(dividends)}")
        if (foreignDividends.isNotEmpty()) {
            println("Foreign dividends converted to GBP = £${PrintUtils
                .roundTo2DpString(foreignDividends.sumOf { it.amount })}")
        }
        if (dividendTaxes.isNotEmpty()) {
            println("Foreign dividend taxes converted to GBP = £${PrintUtils
                .roundTo2DpString(dividendTaxes.sumOf { it.amount })}\n")
        }
    }

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
            validTransactionDescriptions.contains(transactionDescription) -> transactionDescription
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

    private fun finishProcessing() {
        errorLogger.printErrors()
        println("\nProcessing complete")
        exitProcess(0)
    }
}