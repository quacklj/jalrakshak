package com.example.jalraksha.ui.trends

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.jalraksha.R
import com.example.jalraksha.data.SampleData
import com.example.jalraksha.data.SampleTrends
import com.example.jalraksha.data.model.MonthlySafeDays
import com.example.jalraksha.data.model.ParameterMovement
import com.example.jalraksha.data.model.TrendRange
import com.example.jalraksha.data.model.TrendsReport
import com.example.jalraksha.locale.LocalAppLocale
import com.example.jalraksha.ui.components.AreaChart
import com.example.jalraksha.ui.components.ChartGrid
import com.example.jalraksha.ui.components.softShadow
import com.example.jalraksha.ui.text.WaterStrings
import com.example.jalraksha.ui.text.formatCount
import com.example.jalraksha.ui.text.formatDecimal
import com.example.jalraksha.ui.text.formatSignedDecimal
import com.example.jalraksha.ui.text.formatSignedDelta
import com.example.jalraksha.ui.text.shortMonthName
import com.example.jalraksha.ui.theme.JalrakshaTheme
import com.example.jalraksha.ui.theme.JrColor
import com.example.jalraksha.ui.theme.JrType

/** Screen 05 — how the village's water has moved over the chosen window. */
@Composable
fun TrendsScreen(
    modifier: Modifier = Modifier,
    viewModel: TrendsViewModel = viewModel(factory = TrendsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TrendsContent(
        state = state,
        onRangeSelected = viewModel::selectRange,
        onRetry = viewModel::load,
        modifier = modifier,
    )
}

@Composable
private fun TrendsContent(
    state: TrendsUiState,
    onRangeSelected: (TrendRange) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = LocalAppLocale.current.language

    Box(modifier.fillMaxSize().background(JrColor.SurfaceMuted)) {
        when {
            state.report == null && state.loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = JrColor.Primary, strokeWidth = 3.dp)
                }

            state.report == null -> Column(
                Modifier.fillMaxSize().padding(30.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.error_report_load),
                    style = JrType.Body,
                    color = JrColor.Muted,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(R.string.action_retry),
                    style = JrType.Label,
                    color = JrColor.Primary,
                    modifier = Modifier.clickable(onClick = onRetry),
                )
            }

            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                TrendsHeader(
                    villageName = state.village?.displayName(language).orEmpty(),
                    report = state.report,
                    range = state.range,
                    onRangeSelected = onRangeSelected,
                )
                TrendsBody(report = state.report)
            }
        }
    }
}

/** White panel: identity, range switch, headline average and the big chart. */
@Composable
private fun TrendsHeader(
    villageName: String,
    report: TrendsReport,
    range: TrendRange,
    onRangeSelected: (TrendRange) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(JrColor.Surface, RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp))
            .statusBarsPadding()
            .padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 20.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    villageName.uppercase(LocalAppLocale.current),
                    style = JrType.Caption.copy(letterSpacing = JrType.Eyebrow.letterSpacing * 0.3f),
                    color = JrColor.Muted,
                )
                Spacer(Modifier.height(2.dp))
                Text(stringResource(R.string.trends_title), style = JrType.ScreenTitle, color = JrColor.Ink)
            }
            Spacer(Modifier.width(10.dp))
            DirectionChip(report.directionKey)
        }

        Spacer(Modifier.height(18.dp))

        RangeSwitch(range = range, onRangeSelected = onRangeSelected)

        Spacer(Modifier.height(22.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.trends_average_score, WaterStrings.rangeLabel(range)),
                    style = JrType.Label,
                    color = JrColor.Muted,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        formatCount(report.averageScore),
                        style = JrType.Headline,
                        color = JrColor.Ink,
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .padding(bottom = 4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(JrColor.ChipFill)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            formatSignedDelta(report.delta),
                            style = JrType.Caption,
                            color = JrColor.Primary,
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    stringResource(
                        R.string.trends_peak_low,
                        formatCount(report.peak),
                        formatCount(report.low),
                    ),
                    style = JrType.Caption,
                    color = JrColor.Faint,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (report.unsafeDays == 0) {
                        stringResource(R.string.trends_no_unsafe_days)
                    } else {
                        pluralStringResource(
                            R.plurals.trends_unsafe_days,
                            report.unsafeDays,
                            formatCount(report.unsafeDays),
                        )
                    },
                    style = JrType.Caption,
                    color = JrColor.Faint,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Box(Modifier.fillMaxWidth()) {
            AreaChart(
                series = report.series,
                modifier = Modifier.fillMaxWidth().height(130.dp),
                grid = ChartGrid.SolidThirds,
                lineWidth = 3.2.dp,
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 6.dp)
                    .background(JrColor.Deep, RoundedCornerShape(9.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    formatCount(report.series.lastOrNull() ?: report.averageScore),
                    style = JrType.Caption,
                    color = Color.White,
                )
            }
        }
    }
}

/** "Improving" / "Steady" / "Declining", with a dot in the accent. */
@Composable
private fun DirectionChip(directionKey: String) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(JrColor.ChipFill)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(JrColor.Primary, RoundedCornerShape(50)))
        Spacer(Modifier.width(7.dp))
        Text(WaterStrings.trendDirection(directionKey), style = JrType.Label, color = JrColor.Deep)
    }
}

/** The 7D / 30D / 1Y segmented control. */
@Composable
private fun RangeSwitch(range: TrendRange, onRangeSelected: (TrendRange) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(JrColor.SurfaceMuted, RoundedCornerShape(16.dp))
            .border(1.5.dp, JrColor.BorderSoft, RoundedCornerShape(16.dp))
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TrendRange.entries.forEach { entry ->
            val selected = entry == range
            val background by animateColorAsState(
                targetValue = if (selected) JrColor.Primary else Color.Transparent,
                label = "rangeBackground",
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(38.dp)
                    .then(
                        if (selected) {
                            Modifier.softShadow(
                                radius = 12.dp,
                                color = JrColor.Primary.copy(alpha = 0.6f),
                                elevation = 8.dp,
                            )
                        } else {
                            Modifier
                        },
                    )
                    .clip(RoundedCornerShape(12.dp))
                    .background(background)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onRangeSelected(entry) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    WaterStrings.rangeChip(entry),
                    style = JrType.Label.copy(fontSize = JrType.BodySmall.fontSize * 1.04f),
                    color = if (selected) Color.White else JrColor.Muted,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Parameter movement rows and the monthly safe-days bar chart. */
@Composable
private fun TrendsBody(report: TrendsReport) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
            .padding(top = 20.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            stringResource(R.string.trends_parameter_movement),
            style = JrType.Section,
            color = JrColor.Ink,
        )

        report.movers.forEach { MoverRow(it) }

        if (report.monthlySafeDays.isNotEmpty()) {
            MonthlySafeDaysCard(report.monthlySafeDays)
        }

        // Clearance for the floating bottom nav.
        Spacer(Modifier.height(66.dp))
    }
}

@Composable
private fun MoverRow(mover: ParameterMovement) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(JrColor.Surface, RoundedCornerShape(22.dp))
            .border(1.5.dp, JrColor.BorderSoft, RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(44.dp)) {
            Text(
                formatDecimal(mover.value, mover.decimals),
                style = JrType.Caption.copy(fontSize = JrType.BodySmall.fontSize),
                color = JrColor.Ink,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                WaterStrings.unit(mover.unitKey),
                style = JrType.Caption.copy(fontSize = JrType.Caption.fontSize * 0.91f),
                color = JrColor.Faint,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    WaterStrings.parameterLabel(mover.key),
                    style = JrType.Section.copy(fontSize = JrType.BodySmall.fontSize * 1.04f),
                    color = JrColor.Ink,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                DeltaChip(mover)
            }
            Spacer(Modifier.height(9.dp))
            MoverBars(mover.series)
        }
    }
}

/** "steady" reads grey; a numeric change reads in the accent. */
@Composable
private fun DeltaChip(mover: ParameterMovement) {
    val steady = mover.deltaKey == "steady"
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (steady) JrColor.TrackQuiet else JrColor.ChipFill)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            if (steady) {
                stringResource(R.string.trends_delta_steady)
            } else {
                formatSignedDecimal(mover.delta, mover.deltaDecimals)
            },
            style = JrType.Caption.copy(fontSize = JrType.Caption.fontSize * 0.96f),
            color = if (steady) JrColor.Muted else JrColor.Primary,
            maxLines = 1,
        )
    }
}

/**
 * A row of small bars, most recent on the right. The last three are in the accent — a villager
 * should be able to see "the recent end" without reading an axis.
 */
@Composable
private fun MoverBars(series: List<Int>) {
    Row(
        Modifier.fillMaxWidth().height(26.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        series.forEachIndexed { index, value ->
            val recent = index >= series.size - RECENT_BARS
            Box(
                Modifier
                    .weight(1f)
                    .height((value.coerceIn(0, 100) * 0.20f + 6f).dp)
                    .background(
                        if (recent) JrColor.Primary else JrColor.BarQuiet,
                        RoundedCornerShape(2.dp),
                    ),
            )
        }
    }
}

@Composable
private fun MonthlySafeDaysCard(months: List<MonthlySafeDays>) {
    val locale = LocalAppLocale.current
    // The best month is called out in navy, the newest in the accent — the design highlights two
    // bars, and these are the two worth pointing at.
    val bestIndex = months.indices.maxByOrNull { months[it].days } ?: -1
    val latestIndex = months.lastIndex

    Column(
        Modifier
            .fillMaxWidth()
            .background(JrColor.Surface, RoundedCornerShape(26.dp))
            .border(1.5.dp, JrColor.BorderSoft, RoundedCornerShape(26.dp))
            .padding(18.dp),
    ) {
        Text(stringResource(R.string.trends_monthly_title), style = JrType.Section, color = JrColor.Ink)
        Spacer(Modifier.height(3.dp))
        Text(
            stringResource(R.string.trends_monthly_subtitle),
            style = JrType.Caption.copy(fontWeight = FontWeight.SemiBold),
            color = JrColor.Muted,
        )
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.fillMaxWidth().height(112.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            months.forEachIndexed { index, month ->
                val fraction = month.days.toFloat() / month.ofDays.coerceAtLeast(1)
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        formatCount(month.days),
                        style = JrType.Caption.copy(fontSize = JrType.Caption.fontSize * 0.91f),
                        color = JrColor.Deep,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height((fraction * 62f).dp.coerceAtLeast(4.dp))
                            .background(
                                when (index) {
                                    bestIndex -> JrColor.Deep
                                    latestIndex -> JrColor.Primary
                                    else -> JrColor.BarSoft
                                },
                                RoundedCornerShape(8.dp),
                            ),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        shortMonthName(month.month, locale),
                        style = JrType.Caption.copy(fontSize = JrType.Caption.fontSize * 0.91f),
                        color = JrColor.Faint,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private const val RECENT_BARS = 3

@Preview(name = "English", widthDp = 390, heightDp = 844, showBackground = true, locale = "en")
@Preview(name = "हिंदी", widthDp = 390, heightDp = 844, showBackground = true, locale = "hi")
@Composable
private fun TrendsPreview() {
    JalrakshaTheme {
        TrendsContent(
            state = TrendsUiState(
                village = SampleData.villages.first(),
                report = SampleTrends.forRange(TrendRange.Week),
                loading = false,
            ),
            onRangeSelected = {},
            onRetry = {},
        )
    }
}
