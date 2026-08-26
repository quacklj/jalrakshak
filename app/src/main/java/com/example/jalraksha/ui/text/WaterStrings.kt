package com.example.jalraksha.ui.text

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.jalraksha.R
import com.example.jalraksha.data.model.Notice
import com.example.jalraksha.data.model.ReportSeverity
import com.example.jalraksha.data.model.TrendRange
import com.example.jalraksha.data.model.WaterIssue
import com.example.jalraksha.data.model.WaterParameter
import com.example.jalraksha.data.model.WaterRing

/**
 * Turns the keys the backend sends into translated text.
 *
 * The server has no idea what language the reader wants; it sends `"safe"` and the app looks up
 * `verdict_safe`. Any key this build does not recognise falls back to the free-text field the
 * server also sends — so a parameter added on the server after this app shipped still appears,
 * translated by Railway rather than silently vanishing.
 */
object WaterStrings {

    @StringRes
    private fun verdictRes(key: String): Int? = when (key) {
        "safe" -> R.string.verdict_safe
        "boil" -> R.string.verdict_boil
        "unsafe" -> R.string.verdict_unsafe
        "unknown" -> R.string.verdict_unknown
        else -> null
    }

    @StringRes
    private fun gradeRes(key: String): Int? = when (key) {
        "crystal_clear" -> R.string.grade_crystal_clear
        "good" -> R.string.grade_good
        "fair" -> R.string.grade_fair
        "poor" -> R.string.grade_poor
        else -> null
    }

    @StringRes
    private fun labelRes(key: String): Int? = when (key) {
        "ph" -> R.string.param_ph
        "tds" -> R.string.param_tds
        "turbidity" -> R.string.param_turbidity
        "chlorine" -> R.string.param_chlorine
        "hardness" -> R.string.param_hardness
        "ecoli" -> R.string.param_ecoli
        "temperature" -> R.string.param_temperature
        else -> null
    }

    /** The one-line explanation under a ring's label. Only the ring parameters have one. */
    @StringRes
    private fun noteRes(key: String): Int? = when (key) {
        "ph" -> R.string.param_ph_note
        "tds" -> R.string.param_tds_note
        else -> null
    }

    @StringRes
    private fun unitRes(key: String): Int? = when (key) {
        "ph" -> R.string.unit_ph
        "ntu" -> R.string.unit_ntu
        "mgl" -> R.string.unit_mgl
        "cfu" -> R.string.unit_cfu
        "ppm" -> R.string.unit_ppm
        "celsius" -> R.string.unit_celsius
        else -> null
    }

    @StringRes
    private fun statusRes(key: String): Int? = when (key) {
        "clear" -> R.string.status_clear
        "within_limit" -> R.string.status_within_limit
        "soft" -> R.string.status_soft
        "hard" -> R.string.status_hard
        "not_detected" -> R.string.status_not_detected
        "detected" -> R.string.status_detected
        "high" -> R.string.status_high
        "low" -> R.string.status_low
        "not_reading" -> R.string.status_not_reading
        else -> null
    }

    /** Recurring notices the app knows about, as a title/body pair. */
    private fun noticeRes(key: String): Pair<Int, Int>? = when (key) {
        "tanker_check" -> R.string.notice_tanker_check_title to R.string.notice_tanker_check_body
        else -> null
    }

    @StringRes
    private fun directionRes(key: String): Int? = when (key) {
        "improving" -> R.string.trend_improving
        "steady" -> R.string.trend_steady
        "declining" -> R.string.trend_declining
        else -> null
    }

    @StringRes
    private fun issueRes(key: String): Int? = when (key) {
        "taste" -> R.string.issue_taste
        "odour" -> R.string.issue_odour
        "colour" -> R.string.issue_colour
        "no_supply" -> R.string.issue_no_supply
        "illness" -> R.string.issue_illness
        "other" -> R.string.issue_other
        else -> null
    }

    @StringRes
    private fun severityRes(key: String): Int? = when (key) {
        "minor" -> R.string.severity_minor
        "concerning" -> R.string.severity_concerning
        "urgent" -> R.string.severity_urgent
        else -> null
    }

    @StringRes
    private fun reportStatusRes(key: String): Int? = when (key) {
        "open" -> R.string.report_status_open
        "resolved" -> R.string.report_status_resolved
        "closed" -> R.string.report_status_closed
        else -> null
    }

    /** Water sources take a number, so they resolve to a format string. */
    @StringRes
    private fun sourceRes(key: String): Int? = when (key) {
        "handpump" -> R.string.source_handpump
        "tap_line" -> R.string.source_tap_line
        "well" -> R.string.source_well
        "tank" -> R.string.source_tank
        else -> null
    }

    @Composable
    fun trendDirection(key: String): String =
        directionRes(key)?.let { stringResource(it) } ?: stringResource(R.string.trend_steady)

    @Composable
    fun issue(key: String): String = issueRes(key)?.let { stringResource(it) } ?: key

    @Composable
    fun issue(issue: WaterIssue): String = stringResource(issueRes(issue.wire)!!)

    @Composable
    fun severity(severity: ReportSeverity): String = stringResource(severityRes(severity.wire)!!)

    @Composable
    fun reportStatus(key: String): String =
        reportStatusRes(key)?.let { stringResource(it) } ?: stringResource(R.string.report_status_open)

    /** e.g. "Handpump 4". Falls back to the bare number when the kind is unknown. */
    @Composable
    fun source(key: String, number: Int): String {
        val formatted = formatCount(number)
        return sourceRes(key)?.let { stringResource(it, formatted) } ?: formatted
    }

    /** The chip on the range switch: "7D", "30D", "1Y". */
    @Composable
    fun rangeChip(range: TrendRange): String = stringResource(
        when (range) {
            TrendRange.Week -> R.string.range_7d
            TrendRange.Month -> R.string.range_30d
            TrendRange.Year -> R.string.range_1y
        },
    )

    /** The phrase inside "Average score · this week". */
    @Composable
    fun rangeLabel(range: TrendRange): String = stringResource(
        when (range) {
            TrendRange.Week -> R.string.range_label_week
            TrendRange.Month -> R.string.range_label_month
            TrendRange.Year -> R.string.range_label_year
        },
    )

    /** Parameter label from a bare key, for callers that hold no [WaterParameter]. */
    @Composable
    fun parameterLabel(key: String): String = labelRes(key)?.let { stringResource(it) } ?: key

    /** Unit label from a bare key. */
    @Composable
    fun unit(key: String): String = unitRes(key)?.let { stringResource(it) } ?: key

    @Composable
    fun verdict(key: String): String =
        verdictRes(key)?.let { stringResource(it) } ?: stringResource(R.string.verdict_unknown)

    @Composable
    fun grade(key: String): String = gradeRes(key)?.let { stringResource(it) } ?: ""

    @Composable
    fun label(parameter: WaterParameter): String =
        labelRes(parameter.key)?.let { stringResource(it) } ?: parameter.label.orEmpty()

    @Composable
    fun label(ring: WaterRing): String =
        labelRes(ring.key)?.let { stringResource(it) } ?: ring.label.orEmpty()

    @Composable
    fun note(ring: WaterRing): String =
        noteRes(ring.key)?.let { stringResource(it) } ?: ring.note.orEmpty()

    @Composable
    fun unit(parameter: WaterParameter): String =
        unitRes(parameter.unitKey)?.let { stringResource(it) } ?: parameter.unitKey

    @Composable
    fun status(parameter: WaterParameter): String =
        statusRes(parameter.statusKey)?.let { stringResource(it) } ?: parameter.status.orEmpty()

    @Composable
    fun noticeTitle(notice: Notice): String =
        notice.key?.let { noticeRes(it) }?.let { stringResource(it.first) } ?: notice.title

    @Composable
    fun noticeBody(notice: Notice): String =
        notice.key?.let { noticeRes(it) }?.let { stringResource(it.second) } ?: notice.body
}
