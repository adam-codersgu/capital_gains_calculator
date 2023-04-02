package ui

import java.awt.FlowLayout
import java.awt.Font
import java.awt.Font.BOLD
import javax.swing.*

object ExistingSection104 {

    fun openDialog() {
        val frame = JFrame("Existing Section 104 holding").apply {
            defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        }
        val layout = BoxLayout(frame.contentPane, BoxLayout.Y_AXIS)
        frame.layout = layout

        val font = Font("SansSerif", BOLD, 16)
        val label = JLabel("Quantity:")
        val input = JFormattedTextField(0).apply {
            value = 0
            columns = 20
            this.font = font
        }
        val panel = JPanel(FlowLayout(FlowLayout.CENTER)).apply {
            add(label)
            add(input)
        }

        frame.apply {
            add(panel)
            add(JTextField())
            pack()
            // fixme isVisible = true
        }

        // TODO: Have an enter button that processes the submitted quantity
        //  and average buy price fields (to be created)
    }
}