package com.example.guider.util

import java.math.BigDecimal
import java.text.DateFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

/** Reuses locale formatters without resolving Locale and TimeZone before every lookup. */
object LocalizedFormatters {
    private class FormatterCache(
        val locale: Locale,
        val timeZone: TimeZone,
    ) {
        val dateFormatters = ConcurrentHashMap<String, ThreadLocal<DateFormat>>()
        val shortTimeFormatter: ThreadLocal<DateFormat> = ThreadLocal.withInitial {
            DateFormat.getTimeInstance(DateFormat.SHORT, locale).apply {
                timeZone = this@FormatterCache.timeZone
            }
        }
        val currencyFormatter: ThreadLocal<NumberFormat> = ThreadLocal.withInitial {
            NumberFormat.getCurrencyInstance(locale).apply {
                minimumFractionDigits = 2
                maximumFractionDigits = 2
            }
        }
        val currencySymbol: String =
            NumberFormat.getCurrencyInstance(locale).currency?.getSymbol(locale).orEmpty()
    }

    @Volatile
    private var cache = newCache()

    fun refreshConfiguration() {
        cache = newCache()
    }

    fun formatDate(pattern: String, epochMillis: Long): String {
        val current = cache
        val formatter = checkNotNull(current.dateFormatters.computeIfAbsent(pattern) {
            ThreadLocal.withInitial {
                SimpleDateFormat(pattern, current.locale).apply {
                    timeZone = current.timeZone
                }
            }
        }.get())
        return formatter.format(Date(epochMillis))
    }

    fun formatShortTime(epochMillis: Long): String =
        checkNotNull(cache.shortTimeFormatter.get()).format(Date(epochMillis))

    fun formatCurrency(amountMinor: Long): String =
        checkNotNull(cache.currencyFormatter.get()).format(BigDecimal.valueOf(amountMinor, 2))

    fun currencySymbol(): String = cache.currencySymbol

    private fun newCache(): FormatterCache = FormatterCache(
        locale = Locale.getDefault(),
        timeZone = TimeZone.getDefault(),
    )
}
