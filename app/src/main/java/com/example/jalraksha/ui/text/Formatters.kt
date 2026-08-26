package com.example.jalraksha.ui.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.jalraksha.R
import com.example.jalraksha.locale.LocalAppLocale
import java.text.DateFormatSymbols
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Number and time formatting that follows the language chosen in the app, not the device.
 *
 * Every figure on screen goes through here. A score rendered with `toString()` would keep the
 * device's digit shapes and grouping while the words around it changed language — the kind of
 * mismatch that makes a translated screen feel half-done.
 */

/** A whole number: a score, a household count, a sensor count. */
@Composable
fun formatCount(value: Int): String {
    val locale = LocalAppLocale.current
    val format = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    return format.format(value)
}

/** A measured value with a fixed number of decimal places. */
@Composable
fun formatDecimal(value: Double, decimals: Int): String {
    val locale = LocalAppLocale.current
    val format = remember(locale, decimals) {
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = decimals
            maximumFractionDigits = decimals
        }
    }
    return format.format(value)
}

/**
 * A week-on-week change, always carrying its sign — "+4" reads as an improvement, "4" reads as a
 * quantity. The minus sign comes from the locale rather than the keyboard's hyphen.
 */
@Composable
fun formatSignedDelta(value: Int): String {
    val locale = LocalAppLocale.current
    val format = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val magnitude = format.format(abs(value).toLong())
    return when {
        value > 0 -> "+$magnitude"
        value < 0 -> format.format(value.toLong())
        else -> magnitude
    }
}

/**
 * How long ago the reading was taken, phrased in the reader's language: "tested 2 hours ago".
 *
 * Deliberately coarse. A villager needs to know whether the number is from this morning or from
 * last week, and a coarse phrase survives a clock that is a few minutes off.
 */
@Composable
fun formatTestedAt(epochSeconds: Long, now: Long = System.currentTimeMillis() / 1000): String {
    if (epochSeconds <= 0) return ""
    val elapsed = (now - epochSeconds).coerceAtLeast(0)
    val minutes = (elapsed / 60).toInt()
    val hours = (elapsed / 3600).toInt()
    val days = (elapsed / 86_400).toInt()

    return when {
        minutes < 1 -> stringResource(R.string.tested_just_now)
        hours < 1 -> pluralStringResource(R.plurals.tested_minutes_ago, minutes, formatCount(minutes))
        days < 1 -> pluralStringResource(R.plurals.tested_hours_ago, hours, formatCount(hours))
        else -> pluralStringResource(R.plurals.tested_days_ago, days, formatCount(days))
    }
}

/**
 * A signed change in a measured value — "−28", "+0.1". Used on the trends screen, where the sign
 * is the whole point of the chip.
 */
@Composable
fun formatSignedDecimal(value: Double, decimals: Int): String {
    val locale = LocalAppLocale.current
    val format = remember(locale, decimals) {
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = decimals
            maximumFractionDigits = decimals
        }
    }
    val magnitude = format.format(abs(value))
    return if (value > 0) "+$magnitude" else format.format(value)
}

/**
 * The locale's own abbreviated month name for [month] (1–12).
 *
 * Read from ICU rather than from `strings.xml`: Android already ships month names for every
 * language the app offers, and twelve more rows per file would be twelve more chances for a
 * translation to drift.
 */
fun shortMonthName(month: Int, locale: Locale): String =
    DateFormatSymbols.getInstance(locale).shortMonths
        .getOrNull((month - 1).coerceIn(0, 11))
        .orEmpty()

/** A short date like "12 Aug", in the reader's language. */
fun formatShortDate(epochSeconds: Long, locale: Locale): String =
    SimpleDateFormat("d MMM", locale).format(Date(epochSeconds * 1000))

/** Non-composable variant for the messaging service, which has a [Locale] but no composition. */
fun formatCount(value: Int, locale: Locale): String =
    NumberFormat.getIntegerInstance(locale).format(value)
