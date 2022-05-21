
import model.Section104
import model.Transaction
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.system.exitProcess

var section104 = Section104()
var profit = 0.00
var loss = 0.00

/**
 * Developer: Adam Hawke
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

fun process(sellTransactions: MutableList<Transaction>, buyTransactions: MutableList<Transaction>) {
    val sellTransactionsToRemove = mutableListOf<Int>()
    for ((i, t) in sellTransactions.withIndex()) {
        // Process same day transactions
        val index = buyTransactions.indexOfFirst {
            it.date == t.date
        }
        if (index != -1) {
            val buyTransaction = buyTransactions[index]
            val sellAvgPrice = t.price / t.quantity
            val buyAvgPrice = buyTransaction.price / buyTransaction.quantity
            var message = "SAME DAY Sell transaction(s) (IDs " + t.transactionIDs + ") identified with buy transaction(s) (IDs " + buyTransaction.transactionIDs +
                    ") on " + buyTransaction.date + ". " + buyTransaction.quantity + " shares were bought for an average price of $buyAvgPrice GBP and " + t.quantity +
                    " shares were sold for an average price of $sellAvgPrice GBP. "
            var pl: String?
            when {
                t.quantity == buyTransaction.quantity -> {
                    pl = BigDecimal(t.price - buyTransaction.price).setScale(2, RoundingMode.HALF_EVEN).toString()
                    buyTransactions.removeAt(index)
                    sellTransactionsToRemove.add(0, i)
                }
                t.quantity > buyTransaction.quantity -> {
                    // More sold than bought, must process remainder as 30 day or section 104
                    val percentage = buyTransaction.quantity.toDouble() / t.quantity.toDouble()
                    val sameDaySellPrice = t.price * percentage
                    sellTransactions[i].quantity -= buyTransaction.quantity
                    sellTransactions[i].price -= sameDaySellPrice
                    pl = BigDecimal(sameDaySellPrice - buyTransaction.price).setScale(2, RoundingMode.HALF_EVEN).toString()
                    buyTransactions.removeAt(index)
                }
                // t.quantity < buyTransaction.quantity
                else -> {
                    // More bought than sold
                    val percentage = t.quantity.toDouble() / buyTransaction.quantity.toDouble()
                    val buyingCost = buyTransaction.price * percentage
                    buyTransactions[index].quantity -= t.quantity
                    buyTransactions[index].price -= buyingCost
                    pl = BigDecimal(t.price - buyingCost).setScale(2, RoundingMode.HALF_EVEN).toString()
                    sellTransactionsToRemove.add(0, i)
                }
            }
            pl.let {
                if (pl.toDouble() >= 0) {
                    message += "Profit = £$pl."
                    profit += pl.toDouble()
                } else {
                    message += "Loss = £$pl."
                    loss += pl.toDouble()
                }
            }
            println(message)
        }
    }
    for (i in sellTransactionsToRemove) sellTransactions.removeAt(i)
    processBedBreakfastTransactions(sellTransactions, buyTransactions)
}

fun processBedBreakfastTransactions(sellTransactions: MutableList<Transaction>, buyTransactions: MutableList<Transaction>) {
    val sellTransactionsToRemove = mutableListOf<Int>()
    for ((i, t) in sellTransactions.withIndex()) {
        val maxDate = t.date.plusDays(31)
        var index = buyTransactions.indexOfFirst {
            it.date.isAfter(t.date) && it.date.isBefore(maxDate)
        }
        do {
            if (index != -1) {
                val buyTransaction = buyTransactions[index]

                val quantityTransacted = if (t.quantity <= buyTransaction.quantity) t.quantity
                else buyTransaction.quantity

                val sellAvgPrice = t.price / t.quantity
                val buyAvgPrice = buyTransaction.price / buyTransaction.quantity

                var message = "BED AND BREAKFAST Sell transaction(s) (IDs " + t.transactionIDs + ") identified with buy transaction(s) (IDs " + buyTransaction.transactionIDs +
                        "). $quantityTransacted shares sold for an average price of $sellAvgPrice GBP on " + t.date + " identified with $quantityTransacted shares bought on " +
                        buyTransaction.date + " for an average price of $buyAvgPrice GBP. "

                var pl: String? = null
                when {
                    t.quantity == buyTransaction.quantity -> {
                        pl = BigDecimal(t.price - buyTransaction.price).setScale(2, RoundingMode.HALF_EVEN).toString()
                        buyTransactions.removeAt(index)
                        sellTransactionsToRemove.add(0, i)
                        index = -1
                    }
                    t.quantity > buyTransaction.quantity -> {
                        // More sold than bought, must process remainder as 30 day or section 104
                        val percentage = buyTransaction.quantity.toDouble() / t.quantity.toDouble()
                        val sellPrice = t.price * percentage
                        t.quantity -= buyTransaction.quantity
                        t.price -= sellPrice
                        sellTransactions[i] = t
                        pl = BigDecimal(sellPrice - buyTransaction.price).setScale(2, RoundingMode.HALF_EVEN).toString()
                        buyTransactions.removeAt(index)
                        index = buyTransactions.indexOfFirst {
                            it.date.isAfter(t.date) && it.date.isBefore(maxDate)
                        }
                    }
                    t.quantity < buyTransaction.quantity -> {
                        // More bought than sold
                        val percentage = t.quantity.toDouble() / buyTransaction.quantity.toDouble()
                        val buyingCost = buyTransaction.price * percentage
                        buyTransactions[index].quantity -= t.quantity
                        buyTransactions[index].price -= buyingCost
                        pl = BigDecimal(t.price - buyingCost).setScale(2, RoundingMode.HALF_EVEN).toString()
                        sellTransactionsToRemove.add(0, i)
                        index = -1
                    }
                }
                if (pl != null) {
                    if (pl.toDouble() >= 0) {
                        message += "Profit = £$pl."
                        profit += pl.toDouble()
                    } else {
                        message += "Loss = £$pl."
                        loss += pl.toDouble()
                    }
                }
                println(message)
            }
        } while (index != -1)
    }
    for (i in sellTransactionsToRemove) sellTransactions.removeAt(i)
    val allTransactions = sellTransactions + buyTransactions
    processSection104Transactions(allTransactions.sortedBy { it.date }.toMutableList())
}

fun processSection104Transactions(allTransactions: MutableList<Transaction>) {
    val shortTransactions = mutableListOf<Transaction>()
    for (t in allTransactions) {
        when {
            t.direction == "Buy" -> {
                section104.quantity += t.quantity
                section104.price += t.price
                val message = "SECTION 104 " + t.quantity + " shares priced at " + t.price / t.quantity + " GBP added to Section 104 holding on " +
                        t.date + " (transaction ID(s) " + t.transactionIDs + ")"
                println(message)
            }
            t.quantity > section104.quantity -> {
                // FIXME: Need to link these transactions with the earliest eligible purchase
                shortTransactions.add(t)
                // println("Quantity sold greater than section 104 holding for transaction: $t. Section 104 holding was $section104")
                // break
            }
            else -> {
                val sellAvgPrice = t.price / t.quantity
                val buyAvgPrice = section104.price / section104.quantity
                var message = "SECTION 104 Sell transaction (ID(s) " + t.transactionIDs + ") dated " + t.date + " of " + t.quantity + " shares priced at " +
                        "$sellAvgPrice GBP identified with Section 104 holding. Average price of Section 104 holding shares was $buyAvgPrice GBP. "
                val percentage = t.quantity.toDouble() / section104.quantity.toDouble()
                val buyingCost = section104.price * percentage
                val pl = BigDecimal(t.price - buyingCost).setScale(2, RoundingMode.HALF_EVEN).toString()
                section104.quantity -= t.quantity
                section104.price -= buyingCost

                if (pl.toDouble() >= 0) {
                    message += "Profit = £$pl."
                    profit += pl.toDouble()
                } else {
                    message += "Loss = £$pl."
                    loss += pl.toDouble()
                }
                println(message)
            }
        }
    }

    // Unmatched short transactions
    if (shortTransactions.isNotEmpty()) {
        for (t in shortTransactions) {
            println("WARNING: Outstanding short transaction: $t.")
        }
    }

    if (section104.quantity != 0) println(section104.quantity.toString() + " shares remaining in Section 104 holding at the end of the tax year. Average price: " + section104.price / section104.quantity + " GBP.")
    val gains = BigDecimal(profit).setScale(2, RoundingMode.HALF_EVEN).toString()
    val losses = BigDecimal(loss).setScale(2, RoundingMode.HALF_EVEN).toString()
    val profits = BigDecimal(profit + loss).setScale(2, RoundingMode.HALF_EVEN).toString()
    println("Total gains (excluding fees): £$gains")
    println("Total losses (excluding fees): £$losses")
    println("Profit (gains minus losses): £$profits")
    println("Processing complete")
}