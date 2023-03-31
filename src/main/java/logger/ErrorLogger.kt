package logger

import constants.ErrorConstants.TAG_ROW_SKIPPED

class ErrorLogger {

    private val errors = mutableListOf<String>()

    /**
     * Log an error report for a row that has been skipped.
     *
     * @param rowNumber The number of the skipped row.
     * @param description A description of the reason the row was skipped.
     */
    fun rowSkipped(rowNumber: Int, description: String) {
        errors.add("$TAG_ROW_SKIPPED$rowNumber. $description")
    }

    /** Print any errors that have occurred during processing. */
    fun printErrors() {
        if (errors.isEmpty()) return

        println("\nErrors encountered during processing:")

        for (error in errors) {
            println(error)
        }
    }
}