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
        val reusableDate: ThreadLocal<Date> = threadLocal(::Date)
        val dateFormatters = ConcurrentHashMap<String, ThreadLocal<DateFormat>>()
        val shortTimeFormatter: ThreadLocal<DateFormat> = threadLocal {
            DateFormat.getTimeInstance(DateFormat.SHORT, locale).apply {
                timeZone = this@FormatterCache.timeZone
            }
        }
        val currencyFormatter: ThreadLocal<NumberFormat> = threadLocal {
            NumberFormat.getCurrencyInstance(locale).apply {
                minimumFractionDigits = 2
                maximumFractionDigits = 2
            }
        }
        val currencySymbol: String by lazy {
            NumberFormat.getCurrencyInstance(locale).currency?.getSymbol(locale).orEmpty()
        }

        fun date(epochMillis: Long): Date = checkNotNull(reusableDate.get()).apply {
            time = epochMillis
        }
    }

    private val cacheLock = Any()

    @Volatile
    private var cache: FormatterCache? = null

    fun refreshConfiguration() {
        synchronized(cacheLock) {
            cache = null
        }
    }

    fun formatDate(pattern: String, epochMillis: Long): String {
        val current = currentCache()
        val formatter = checkNotNull(current.dateFormatters.computeIfAbsent(pattern) {
            threadLocal {
                SimpleDateFormat(pattern, current.locale).apply {
                    timeZone = current.timeZone
                }
            }
        }.get())
        return formatter.format(current.date(epochMillis))
    }

    fun formatShortTime(epochMillis: Long): String {
        val current = currentCache()
        return checkNotNull(current.shortTimeFormatter.get()).format(current.date(epochMillis))
    }

    fun formatCurrency(amountMinor: Long): String {
        val current = currentCache()
        return checkNotNull(current.currencyFormatter.get()).format(BigDecimal.valueOf(amountMinor, 2))
    }

    fun currencySymbol(): String = currentCache().currencySymbol

    private fun currentCache(): FormatterCache = cache ?: synchronized(cacheLock) {
        cache ?: newCache().also { cache = it }
    }

    private fun newCache(): FormatterCache = FormatterCache(
        locale = Locale.getDefault(),
        timeZone = TimeZone.getDefault(),
    )

    private fun <T> threadLocal(initializer: () -> T): ThreadLocal<T> =
        object : ThreadLocal<T>() {
            override fun initialValue(): T = initializer()
        }
}
