package exceptions

class AcquisitionNotFollowingDisposalException(message: String = "ERROR: An error occurred while validating " +
        "the acquisition following a disposal rule. The date of the acquisition transaction was not greater " +
        "than the date of the disposal. This error may occur because buy or sell transactions are missing from " +
        "the input spreadsheet.",)
    : Exception(message) {
}