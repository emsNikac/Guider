package com.nikac.guider.domain.money

import java.math.BigDecimal
import java.math.RoundingMode

object MoneyCalculations {
    fun totalMinor(spendings: List<Spending>): Long =
        spendings.fold(0L) { total, spending -> Math.addExact(total, spending.amountMinor) }

    fun parseAmountToMinor(input: String): Long? {
        val normalized = input.trim().replace(',', '.')
        if (!AMOUNT_PATTERN.matches(normalized)) return null
        return runCatching {
            BigDecimal(normalized)
                .setScale(MINOR_UNIT_SCALE, RoundingMode.UNNECESSARY)
                .movePointRight(MINOR_UNIT_SCALE)
                .longValueExact()
                .takeIf { it > 0L }
        }.getOrNull()
    }

    fun minorToInput(amountMinor: Long): String =
        BigDecimal.valueOf(amountMinor, MINOR_UNIT_SCALE)
            .stripTrailingZeros()
            .toPlainString()

    private val AMOUNT_PATTERN = Regex("""\d+(?:[.,]\d{1,2})?""")
    private const val MINOR_UNIT_SCALE = 2
}
