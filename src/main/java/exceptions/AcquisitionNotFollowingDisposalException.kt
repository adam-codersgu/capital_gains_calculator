package exceptions

import model.Transaction

class AcquisitionNotFollowingDisposalException() : Exception() {

    constructor(buyTransaction: Transaction, sellTransaction: Transaction) : this() {
        println("ERROR: An error occurred while validating " +
                "the acquisition following a disposal rule." +
                "The date of the acquisition transaction was not greater " +
                "than the date of the disposal. This error may occur because buy or " +
                "sell transactions are missing from the input spreadsheet. \n" +
                "Buy transaction: ${buyTransaction.getTransactionDateAndIdsMessage()}\n " +
                "Sell transaction: ${sellTransaction.getTransactionDateAndIdsMessage()}")
    }
}