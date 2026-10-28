package io.github.sjakubiak.fitboi.ui

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

object Format {
    private val numbers = NumberFormat.getIntegerInstance()
    private val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private val longDate = DateTimeFormatter.ofPattern("EEEE, MMMM d")
    private val shortDate = DateTimeFormatter.ofPattern("MMM d")

    fun count(value: Long): String = numbers.format(value)
    fun count(value: Double): String = numbers.format(value.roundToInt())
    fun km(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)

    /** 412 -> "6h 52m". */
    fun minutes(total: Long): String {
        val h = total / 60
        val m = total % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    fun clock(instant: Instant): String = time.format(instant.atZone(ZoneId.systemDefault()))
    fun longDate(date: LocalDate): String = longDate.format(date)
    fun shortDate(date: LocalDate): String = shortDate.format(date)
    fun weekday(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
}
