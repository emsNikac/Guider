package com.example.guider.util

import java.math.BigDecimal
import java.text.DateFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

/** Reuses the relatively expensive locale formatters while keeping them thread-confined. */
object LocalizedFormatters {
    private data class DateFormatterKey(
        val pattern: String,
        val localeTag: String,
        val timeZoneId: String,
    )

    private val dateFormatters =
        ConcurrentHashMap<DateFormatterKey, ThreadLocal<DateFormat>>()
    private val timeFormatters =
        ConcurrentHashMap<DateFormatterKey, ThreadLocal<DateFormat>>()
    private val currencyFormatters =
        ConcurrentHashMap<String, ThreadLocal<NumberFormat>>()

    fun formatDate(pattern: String, epochMillis: Long): String {
        val key = currentDateKey(pattern)
        val formatter = checkNotNull(dateFormatters.computeIfAbsent(key) {
            ThreadLocal.withInitial {
                SimpleDateFormat(pattern, Locale.forLanguageTag(key.localeTag)).apply {
                    timeZone = TimeZone.getTimeZone(key.timeZoneId)
                }
            }
        }.get())
        return formatter.format(Date(epochMillis))
    }

    fun formatShortTime(epochMillis: Long): String {
        val key = currentDateKey(SHORT_TIME_KEY)
        val formatter = checkNotNull(timeFormatters.computeIfAbsent(key) {
            ThreadLocal.withInitial {
                DateFormat.getTimeInstance(
                    DateFormat.SHORT,
                    Locale.forLanguageTag(key.localeTag),
                ).apply {
                    timeZone = TimeZone.getTimeZone(key.timeZoneId)
                }
            }
        }.get())
        return formatter.format(Date(epochMillis))
    }

    fun formatCurrency(amountMinor: Long): String =
        currencyFormatter().format(BigDecimal.valueOf(amountMinor, 2))

    fun currencySymbol(): String =
        currencyFormatter().currency?.getSymbol(Locale.getDefault()).orEmpty()

    private fun currencyFormatter(): NumberFormat {
        val locale = Locale.getDefault()
        val localeTag = locale.toLanguageTag()
        return checkNotNull(currencyFormatters.computeIfAbsent(localeTag) {
            ThreadLocal.withInitial {
                NumberFormat.getCurrencyInstance(locale).apply {
                    minimumFractionDigits = 2
                    maximumFractionDigits = 2
                }
            }
        }.get())
    }

    private fun currentDateKey(pattern: String): DateFormatterKey = DateFormatterKey(
        pattern = pattern,
        localeTag = Locale.getDefault().toLanguageTag(),
        timeZoneId = TimeZone.getDefault().id,
    )

    private const val SHORT_TIME_KEY = "localized-short-time"
}
