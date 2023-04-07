package ui

import ProcessSpreadsheet
import model.Section104
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Font.BOLD
import java.io.File
import java.text.DecimalFormat
import java.text.NumberFormat
import javax.swing.*


object ExistingSection104 {

    fun openDialog(file: File) {
        val frame = JFrame("Existing Section 104 holding").apply {
            defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        }
        val layout = BoxLayout(frame.contentPane, BoxLayout.Y_AXIS)
        frame.layout = layout

        val font = Font("SansSerif", BOLD, 16)
        val quantityLabel = JLabel("Quantity:")
        val numberFormat = NumberFormat.getInstance().apply {
            minimumIntegerDigits = 0
            maximumIntegerDigits = 10
        }
        val quantityInput = JFormattedTextField(numberFormat).apply {
            value = 0
            columns = 20
            this.font = font
        }

        val averagePurchasePriceLabel = JLabel("Average purchase price (GBP):")
        val decimalFormat = DecimalFormat.getInstance().apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 20
        }
        val averagePurchasePriceInput = JFormattedTextField(decimalFormat).apply {
            value = 0.00f
            columns = 20
            this.font = font
        }

        val panel = JPanel(FlowLayout(FlowLayout.CENTER)).apply {
            add(quantityLabel)
            add(quantityInput)
            add(averagePurchasePriceLabel)
            add(averagePurchasePriceInput)
        }

        val submitButton = JButton("Submit").apply {
            setBounds(50, 100, 95, 30)
            addActionListener {
                // TODO What happens if you get invalid input e.g. String instead of Long
                val quantity = (quantityInput.value as Number).toInt()
                val averagePurchasePrice = (averagePurchasePriceInput.value as Number).toDouble()
                val section104Price = quantity * averagePurchasePrice
                val section104 = Section104(quantity = quantity, price = section104Price)

                val quantityMessage = if (section104.quantity == 1) "1 share"
                else "${section104.quantity} shares"
                println("The pre-existing Section 104 holding consisted of $quantityMessage " +
                        "purchase at an average price of £$averagePurchasePrice")
                ProcessSpreadsheet(file.absolutePath, section104)
            }
        }

        frame.apply {
            add(panel)
            add(submitButton)
            pack()
            isVisible = true
        }

        // TODO: Have an enter button that processes the submitted quantity
        //  and average buy price fields (to be created)
    }
}