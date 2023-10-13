package model

import java.math.BigDecimal
import java.math.RoundingMode

data class Section104(
    var transactionIDs: MutableList<String> = mutableListOf(),
    var quantity: Int = 0,
    var price: Double = 0.00
) {
    /** Clear the Section 104 holding. */
    fun clear() {
        transactionIDs.clear()
        quantity = 0
        price = 0.00
    }

    fun getAverageUnitPrice(): Double {
        return BigDecimal(price / quantity).setScale(9, RoundingMode.HALF_EVEN).toDouble()
    }
}