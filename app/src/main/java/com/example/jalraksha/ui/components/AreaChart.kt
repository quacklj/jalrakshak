package com.example.jalraksha.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.jalraksha.ui.theme.JrColor

/** How the horizontal rules behind an [AreaChart] are drawn. */
enum class ChartGrid {
    /** One dashed rule at the top of the plot — the dashboard's score-history card. */
    DashedTop,

    /** Three solid rules across the plot — the trends screen's larger chart. */
    SolidThirds,
}

/**
 * The smoothed area chart both score charts are built from.
 *
 * The design draws each one as a hand-tuned bezier; this builds the equivalent curve from real
 * data, so a flat week looks flat instead of borrowing the mock's shape. Extracted because the two
 * screens must agree — a villager comparing the dashboard card to the trends screen should not see
 * the same seven numbers drawn two different ways.
 */
@Composable
fun AreaChart(
    series: List<Int>,
    modifier: Modifier = Modifier,
    grid: ChartGrid = ChartGrid.DashedTop,
    lineWidth: Dp = 3.dp,
    /** Padding above and below the curve, as a fraction of the canvas height. */
    verticalInset: Float = 0.12f,
) {
    Canvas(modifier) {
        if (series.size < 2) return@Canvas

        // Pad the range so a near-flat window still has a visible curve rather than a straight
        // line pinned to an edge.
        val min = (series.min() - 4).coerceAtLeast(0)
        val max = (series.max() + 4).coerceAtMost(100)
        val span = (max - min).coerceAtLeast(1).toFloat()

        val topInset = size.height * verticalInset
        val plotHeight = size.height * (1f - verticalInset * 2f)
        val points = series.mapIndexed { i, value ->
            Offset(
                x = size.width * i / (series.size - 1),
                y = topInset + plotHeight * (1f - (value - min) / span),
            )
        }

        when (grid) {
            ChartGrid.DashedTop -> drawLine(
                color = JrColor.GridLine,
                start = Offset(0f, topInset),
                end = Offset(size.width, topInset),
                strokeWidth = 1.2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx())),
            )

            ChartGrid.SolidThirds -> listOf(0.22f, 0.52f, 0.82f).forEach { fraction ->
                drawLine(
                    color = JrColor.GaugeTicks,
                    start = Offset(0f, size.height * fraction),
                    end = Offset(size.width, size.height * fraction),
                    strokeWidth = 1.2.dp.toPx(),
                )
            }
        }

        val line = smoothPath(points)

        val area = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(
            path = area,
            brush = Brush.verticalGradient(
                colors = listOf(
                    JrColor.Primary.copy(alpha = if (grid == ChartGrid.SolidThirds) 0.24f else 0.22f),
                    JrColor.Primary.copy(alpha = 0f),
                ),
            ),
        )

        drawPath(
            path = line,
            color = JrColor.Primary,
            style = Stroke(width = lineWidth.toPx(), cap = StrokeCap.Round),
        )

        // Latest reading, ringed in white so it reads on top of the line.
        val last = points.last()
        drawCircle(color = Color.White, radius = 7.dp.toPx(), center = last)
        drawCircle(color = JrColor.Primary, radius = 5.5.dp.toPx(), center = last)
    }
}

/**
 * Catmull-Rom through every point, converted to cubic beziers. Tension 6 matches the gentle,
 * non-overshooting curve the design draws by hand.
 */
private fun smoothPath(points: List<Offset>): Path = Path().apply {
    moveTo(points.first().x, points.first().y)
    for (i in 0 until points.size - 1) {
        val p0 = points[(i - 1).coerceAtLeast(0)]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points[(i + 2).coerceAtMost(points.size - 1)]
        cubicTo(
            p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
            p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
            p2.x, p2.y,
        )
    }
}
