package mock

import mock.templates.SpreadsheetRow

class TestSpreadsheetRowData {

    /**
     * Companion object where example test data is defined:
     *  ROW0 -> Buy 10 shares company A
     *  ROW1 -> Sell 10 shares company A same day as ROW0
     *  ROW2 -> INVALID RECORD (Transaction fee - Not a buy or sell transaction)
     */
    companion object {
        private val ROW0 = SpreadsheetRow(
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
        private val ROW1 = SpreadsheetRow(
            "16-05-2022",
            "09:55",
            "16-05-2022",
            "Company A",
            "AB123456789",
            "Sell 10 Company A",
            null,
            "GBP",
            206.54,
            "GBP",
            206.54,
            "bvcxza-654321-lkjhgf"
        )
        private val ROW2 = SpreadsheetRow(
            "16-05-2022",
            "09:22",
            "16-05-2022",
            "Company A",
            "AB123456789",
            "Transaction and/or third party fees",
            null,
            "GBP",
            -0.42,
            "GBP",
            -0.42,
            "abcdefg-123456-hijk7890"
        )
    }

    fun getTestSpreadsheetRowData(): Array<SpreadsheetRow> {
        return arrayOf(ROW0, ROW1, ROW2)
    }
}