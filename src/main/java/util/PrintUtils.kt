package util

import java.math.BigDecimal
import java.math.RoundingMode

object PrintUtils {

    fun roundTo2DpString(amount: Double): String {
        return BigDecimal(amount)
            .setScale(2, RoundingMode.HALF_EVEN).toString()
    }
}