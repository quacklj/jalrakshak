package com.example.jalraksha.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The design's tinted drop shadow (`box-shadow: 0 14px 26px -12px rgba(29,78,216,.7)`).
 *
 * Compose only gives us elevation shadows, so we approximate: the shadow colour carries the tint
 * and the elevation carries the spread.
 */
fun Modifier.softShadow(
    radius: Dp,
    color: Color,
    elevation: Dp = 14.dp,
): Modifier = shadow(
    elevation = elevation,
    shape = RoundedCornerShape(radius),
    ambientColor = color,
    spotColor = color,
)

/**
 * Draws the left and bottom edges of the layout box — the two strokes that, once the box is
 * rotated, become a chevron or a checkmark. The design builds both arrows this way.
 */
fun Modifier.drawLeftBottom(
    color: Color,
    strokeWidth: Dp = 2.dp,
): Modifier = drawBehind {
    val w = strokeWidth.toPx()
    val inset = w / 2f
    drawLine(
        color = color,
        start = Offset(inset, 0f),
        end = Offset(inset, size.height - inset),
        strokeWidth = w,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = color,
        start = Offset(0f, size.height - inset),
        end = Offset(size.width, size.height - inset),
        strokeWidth = w,
        cap = StrokeCap.Round,
    )
}
