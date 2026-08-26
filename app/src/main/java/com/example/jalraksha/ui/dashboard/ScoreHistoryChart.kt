package com.example.jalraksha.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.jalraksha.R
import com.example.jalraksha.ui.components.AreaChart
import com.example.jalraksha.ui.components.ChartGrid
import com.example.jalraksha.ui.components.JrChip
import com.example.jalraksha.ui.text.formatCount
import com.example.jalraksha.ui.text.formatSignedDelta
import com.example.jalraksha.ui.theme.JalrakshaTheme
import com.example.jalraksha.ui.theme.JrColor
import com.example.jalraksha.ui.theme.JrType

/**
 * "Score history" card: a smoothed 7-day area chart with the latest reading called out.
 *
 * The design draws one hand-tuned bezier; this builds the equivalent curve from real data using
 * Catmull-Rom control points, so a flat week looks flat instead of borrowing the mock's shape.
 */
@Composable
fun ScoreHistoryCard(
    history: List<Int>,
    latestScore: Int,
    delta: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(JrColor.Surface, RoundedCornerShape(26.dp))
            .border(1.5.dp, JrColor.BorderSoft, RoundedCornerShape(26.dp))
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text(stringResource(R.string.history_title), style = JrType.Section, color = JrColor.Ink)
                Spacer(Modifier.height(3.dp))
                Text(
                    stringResource(R.string.history_subtitle, formatSignedDelta(delta)),
                    style = JrType.Caption.copy(fontWeight = FontWeight.SemiBold),
                    color = JrColor.Muted,
                )
            }
            JrChip(
                text = stringResource(R.string.history_range_7d),
                background = JrColor.ChipFill,
                contentColor = JrColor.Primary,
            )
        }

        Spacer(Modifier.height(14.dp))

        Box(Modifier.fillMaxWidth()) {
            AreaChart(
                series = history,
                modifier = Modifier.fillMaxWidth().height(104.dp),
                grid = ChartGrid.DashedTop,
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 14.dp)
                    .background(JrColor.Deep, RoundedCornerShape(9.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(formatCount(latestScore), style = JrType.Caption, color = Color.White)
            }
        }

        Spacer(Modifier.height(2.dp))

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            DayLabels.forEach {
                Text(stringResource(it), style = JrType.Caption, color = JrColor.Faint)
            }
        }
    }
}

private val DayLabels = listOf(
    R.string.day_mon,
    R.string.day_tue,
    R.string.day_wed,
    R.string.day_thu,
    R.string.day_fri,
    R.string.day_sat,
    R.string.day_sun,
)

@Preview(widthDp = 346, backgroundColor = 0xFFF6F9FF, showBackground = true)
@Composable
private fun ScoreHistoryPreview() {
    JalrakshaTheme {
        ScoreHistoryCard(
            history = listOf(74, 78, 76, 84, 82, 88, 92),
            latestScore = 92,
            delta = 4,
            modifier = Modifier.padding(12.dp),
        )
    }
}
