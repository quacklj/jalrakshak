package com.example.jalraksha.ui.dashboard

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.jalraksha.R
import com.example.jalraksha.ui.text.formatCount
import com.example.jalraksha.ui.theme.JalrakshaTheme
import com.example.jalraksha.ui.theme.JrColor
import com.example.jalraksha.ui.theme.JrType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The headline water-score gauge: a half-ring that sweeps from 0 on the left to 100 on the right,
 * a dotted inner ring, and the number counting up inside it.
 *
 * Geometry is expressed in the design's own 200×112 viewBox and scaled to whatever width it is
 * given, so the gauge stays proportional from a 320dp phone up to a tablet.
 */
@Composable
fun ScoreGauge(score: Int, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(
        targetValue = score.coerceIn(0, 100) / 100f,
        animationSpec = tween(durationMillis = 1600, easing = EaseOutCubic),
        label = "gaugeProgress",
    )

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val boxWidth = maxWidth
        // 200 × 112 viewBox, drawn 300 × 168 in the design; the label block adds the last 4dp.
        val canvasHeight = boxWidth * (112f / 200f)

        Box(Modifier.fillMaxWidth().height(canvasHeight + 4.dp)) {
            Canvas(Modifier.fillMaxWidth().height(canvasHeight)) {
                val scale = size.width / VIEWBOX_WIDTH
                val cx = 100f * scale
                val cy = 100f * scale
                val r = 82f * scale
                val strokeWidth = 24f * scale
                val arcTopLeft = Offset(cx - r, cy - r)
                val arcSize = Size(r * 2, r * 2)

                // Unfilled track.
                drawArc(
                    color = JrColor.GaugeTrack,
                    startAngle = START_ANGLE,
                    sweepAngle = SWEEP_ANGLE,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )

                // Dotted inner ring. Drawn as a full circle; the bottom half falls outside the
                // canvas, exactly as the design's `overflow:hidden` clips it.
                val innerR = 63f * scale
                drawArc(
                    color = JrColor.GaugeTicks,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(cx - innerR, cy - innerR),
                    size = Size(innerR * 2, innerR * 2),
                    style = Stroke(
                        width = 9f * scale,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(1.4f * scale, 6.2f * scale),
                        ),
                    ),
                )

                if (progress > 0f) {
                    // Gradient runs bottom-left to top-right, matching the SVG's x1/y1 → x2/y2.
                    drawArc(
                        brush = Brush.linearGradient(
                            colorStops = arrayOf(
                                0f to JrColor.Deep,
                                0.52f to JrColor.Primary,
                                1f to JrColor.GaugeTail,
                            ),
                            start = Offset(0f, size.height),
                            end = Offset(size.width, 0f),
                        ),
                        startAngle = START_ANGLE,
                        sweepAngle = SWEEP_ANGLE * progress,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )

                    // Translucent pip riding the leading edge of the arc.
                    val angle = PI * progress
                    drawCircle(
                        color = Color.White.copy(alpha = 0.9f),
                        radius = 5f * scale,
                        center = Offset(
                            x = cx - r * cos(angle).toFloat(),
                            y = cy - r * sin(angle).toFloat(),
                        ),
                    )
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = canvasHeight * (56f / 168f)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    formatCount((score * progress).roundToInt()),
                    style = JrType.Score,
                    color = JrColor.Ink,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.gauge_caption),
                    style = JrType.Label.copy(fontSize = JrType.Body.fontSize * 0.96f),
                    color = JrColor.Muted,
                    textAlign = TextAlign.Center,
                )
            }

            // The scale ends, formatted for the locale like every other figure on screen.
            Text(
                formatCount(0),
                style = JrType.Caption,
                color = JrColor.Faint,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 2.dp),
            )
            Text(
                formatCount(100),
                style = JrType.Caption,
                color = JrColor.Faint,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 2.dp),
            )
        }
    }
}

/** 180° is 9 o'clock in Compose; sweeping +180 goes up and over to 3 o'clock. */
private const val START_ANGLE = 180f
private const val SWEEP_ANGLE = 180f
private const val VIEWBOX_WIDTH = 200f

/** `1 - (1 - p)^3`, the easing the design animates the gauge with. */
private val EaseOutCubic = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)

@Preview(widthDp = 340, backgroundColor = 0xFFFFFFFF, showBackground = true)
@Composable
private fun ScoreGaugePreview() {
    JalrakshaTheme { ScoreGauge(score = 92) }
}
