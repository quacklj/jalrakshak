package com.example.jalraksha.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.jalraksha.R
import com.example.jalraksha.data.SampleData
import com.example.jalraksha.data.model.Notice
import com.example.jalraksha.data.model.Village
import com.example.jalraksha.data.model.WaterParameter
import com.example.jalraksha.data.model.WaterReport
import com.example.jalraksha.data.model.WaterRing
import com.example.jalraksha.locale.LocalAppLocale
import com.example.jalraksha.ui.components.Droplet
import com.example.jalraksha.ui.components.JalrakshaMark
import com.example.jalraksha.ui.components.JrCheck
import com.example.jalraksha.ui.text.WaterStrings
import com.example.jalraksha.ui.text.formatCount
import com.example.jalraksha.ui.text.formatDecimal
import com.example.jalraksha.ui.text.formatTestedAt
import com.example.jalraksha.ui.theme.JalrakshaTheme
import com.example.jalraksha.ui.theme.JrColor
import com.example.jalraksha.ui.theme.JrType

/** Screen 04 — the water score dashboard. */
@Composable
fun DashboardScreen(
    onNoVillage: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.needsVillage) {
        LaunchedEffect(Unit) { onNoVillage() }
    }

    DashboardContent(
        state = state,
        onNoticesClick = viewModel::markNoticesRead,
        onRetry = { viewModel.load() },
        modifier = modifier,
    )
}

@Composable
private fun DashboardContent(
    state: DashboardUiState,
    onNoticesClick: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(JrColor.SurfaceMuted),
    ) {
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = JrColor.Primary, strokeWidth = 3.dp)
            }

            state.report == null -> Column(
                Modifier.fillMaxSize().padding(30.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(
                        if (state.failed) R.string.error_report_load else R.string.dashboard_no_reading,
                    ),
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
                DashboardHeader(
                    village = state.village,
                    report = state.report,
                    hasUnreadNotice = state.hasUnreadNotice,
                    onNoticesClick = onNoticesClick,
                )
                DashboardBody(report = state.report)
            }
        }

    }
}

/** White panel with the village identity, the gauge and the two verdict chips. */
@Composable
private fun DashboardHeader(
    village: Village?,
    report: WaterReport,
    hasUnreadNotice: Boolean,
    onNoticesClick: () -> Unit,
) {
    val language = LocalAppLocale.current.language

    Column(
        Modifier
            .fillMaxWidth()
            .background(JrColor.Surface, RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp))
            .statusBarsPadding()
            .padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 22.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                JalrakshaMark(size = 42.dp)
                Spacer(Modifier.width(11.dp))
                Column {
                    Text(
                        stringResource(R.string.dashboard_your_village),
                        style = JrType.Caption.copy(
                            fontSize = JrType.Caption.fontSize * 0.96f,
                            letterSpacing = 0.06.em,
                        ),
                        color = JrColor.Muted,
                    )
                    Text(
                        village?.displayName(language)
                            ?: stringResource(R.string.dashboard_no_village),
                        style = JrType.Section.copy(fontSize = JrType.Section.fontSize * 1.07f),
                        color = JrColor.Ink,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            NotificationBell(hasUnread = hasUnreadNotice, onClick = onNoticesClick)
        }

        Spacer(Modifier.height(20.dp))

        ScoreGauge(score = report.score)

        Spacer(Modifier.height(2.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(JrColor.ChipFill)
                    .border(1.dp, JrColor.ChipBorder, RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                JrCheck(size = 18.dp, background = JrColor.Primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    WaterStrings.verdict(report.verdictKey),
                    style = JrType.Label.copy(fontSize = JrType.Body.fontSize * 0.96f),
                    color = JrColor.Deep,
                )
            }
            val grade = WaterStrings.grade(report.gradeKey)
            if (grade.isNotEmpty()) {
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(JrColor.Deep)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(
                        grade,
                        style = JrType.Label.copy(fontSize = JrType.Body.fontSize * 0.96f),
                        color = Color.White,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(
                R.string.dashboard_checks_and_tested,
                pluralStringResource(
                    R.plurals.dashboard_checks_passed,
                    report.checksPassed,
                    formatCount(report.checksPassed),
                ),
                formatTestedAt(report.testedAtEpochSeconds),
            ),
            style = JrType.BodySmall.copy(fontSize = JrType.Label.fontSize),
            color = JrColor.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Parameters, history and the pushed notice. */
@Composable
private fun DashboardBody(report: WaterReport) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
            .padding(top = 20.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.dashboard_parameters_title),
                style = JrType.Section,
                color = JrColor.Ink,
            )
            Text(stringResource(R.string.action_see_all), style = JrType.Label, color = JrColor.Primary)
        }

        // Ring cards, two per row.
        report.rings.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { ring ->
                    RingCard(ring, Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        // Bar cards, two per row.
        report.parameters.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { parameter ->
                    ParameterCard(parameter, Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        if (report.history.size >= 2) {
            ScoreHistoryCard(
                history = report.history,
                latestScore = report.score,
                delta = report.historyDelta,
            )
        }

        report.notice?.let { NoticeBanner(it) }

        // Clearance for the floating bottom nav.
        Spacer(Modifier.height(66.dp))
    }
}

@Composable
private fun RingCard(ring: WaterRing, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(JrColor.Surface, RoundedCornerShape(22.dp))
            .border(1.5.dp, JrColor.BorderSoft, RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProgressRing(fill = ring.fill, value = formatDecimal(ring.value, ring.decimals))
        Spacer(Modifier.width(13.dp))
        Column {
            Text(
                WaterStrings.label(ring),
                style = JrType.Section.copy(fontSize = JrType.BodySmall.fontSize * 1.04f),
                color = JrColor.Ink,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                WaterStrings.note(ring),
                style = JrType.Caption.copy(fontWeight = FontWeight.SemiBold),
                color = JrColor.Muted,
            )
        }
    }
}

/** 52dp ring swept clockwise from 12 o'clock, with the value in a white well at the centre. */
@Composable
private fun ProgressRing(fill: Int, value: String, size: Dp = 52.dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = this.size.minDimension * (7f / 52f)
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(
                color = JrColor.RingTrack,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke),
            )
            drawArc(
                color = JrColor.Primary,
                startAngle = -90f,
                sweepAngle = 360f * fill.coerceIn(0, 100) / 100f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Butt),
            )
        }
        Text(
            value,
            style = JrType.Caption.copy(
                fontSize = JrType.Caption.fontSize * 1.09f,
                fontWeight = FontWeight.ExtraBold,
            ),
            color = JrColor.Deep,
        )
    }
}

@Composable
private fun ParameterCard(parameter: WaterParameter, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(JrColor.Surface, RoundedCornerShape(22.dp))
            .border(1.5.dp, JrColor.BorderSoft, RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 15.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                WaterStrings.label(parameter),
                style = JrType.Label,
                color = JrColor.Muted,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(8.dp).background(JrColor.Primary, RoundedCornerShape(50)))
        }
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                formatDecimal(parameter.value, parameter.decimals),
                style = JrType.Metric,
                color = JrColor.Ink,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                WaterStrings.unit(parameter),
                style = JrType.Caption,
                color = JrColor.Faint,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        Spacer(Modifier.height(11.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(JrColor.Track),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(parameter.fill.coerceIn(0, 100) / 100f)
                    .height(6.dp)
                    .background(JrColor.Primary, RoundedCornerShape(3.dp)),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            WaterStrings.status(parameter),
            style = JrType.Caption.copy(fontSize = JrType.Caption.fontSize * 0.96f),
            color = JrColor.Primary,
        )
    }
}

/** The navy banner carrying whatever the central dashboard last pushed. */
@Composable
private fun NoticeBanner(notice: Notice) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(JrColor.Deep, RoundedCornerShape(24.dp))
            .padding(horizontal = 18.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Droplet(size = 17.dp, color = JrColor.Sky)
        }
        Spacer(Modifier.width(13.dp))
        Column {
            Text(
                WaterStrings.noticeTitle(notice),
                style = JrType.Label.copy(fontSize = JrType.Body.fontSize * 0.96f),
                color = Color.White,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                WaterStrings.noticeBody(notice),
                style = JrType.Caption,
                color = JrColor.OnDeepMuted,
            )
        }
    }
}

@Composable
private fun NotificationBell(hasUnread: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(JrColor.SurfaceMuted)
            .border(1.5.dp, JrColor.BorderSoft, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 14.dp, height = 12.dp)
                .border(
                    2.dp,
                    JrColor.Ink,
                    RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp, bottomStart = 3.dp, bottomEnd = 3.dp),
                ),
        )
        if (hasUnread) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 9.dp, end = 10.dp)
                    .size(8.dp)
                    .background(JrColor.Primary, RoundedCornerShape(50))
                    .border(2.dp, JrColor.SurfaceMuted, RoundedCornerShape(50)),
            )
        }
    }
}

@Preview(name = "English", widthDp = 390, heightDp = 844, showBackground = true, locale = "en")
@Preview(name = "हिंदी", widthDp = 390, heightDp = 844, showBackground = true, locale = "hi")
@Preview(name = "తెలుగు", widthDp = 390, heightDp = 844, showBackground = true, locale = "te")
@Composable
private fun DashboardPreview() {
    JalrakshaTheme {
        DashboardContent(
            state = DashboardUiState(
                village = SampleData.villages.first(),
                report = SampleData.report,
                loading = false,
            ),
            onNoticesClick = {},
            onRetry = {},
        )
    }
}
