package model

data class SpreadsheetRow(
    val date: String,
    val time: String,
    val valueDate: String,
    val product: String,
    val ISIN: String,
    val description: String,
    val fx: String?,
    val changeISOCode: String,
    val changeValue: Double,
    val balanceISOCode: String,
    val balanceValue: Double,
    val orderID: String
) {
    fun getData() : List<Any?> = listOf(date, time, valueDate, product, ISIN, description, fx, changeISOCode, changeValue, balanceISOCode, balanceValue, orderID)
}
