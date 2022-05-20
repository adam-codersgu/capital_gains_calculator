package exceptions

class UnknownTransactionTypeException(message: String = "An error occurred while determining the type of transaction. \n" +
        "Please check you have only included Buy and Sell transactions. \n" +
        "Remove all other data including dividends, transaction fees, FX Credit/Debit etc.") : Exception(message) {
}