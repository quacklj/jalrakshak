package com.example.jalraksha.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.example.jalraksha.ui.theme.JrColor

/**
 * The Jalraksha droplet: a square with one sharp corner and three round ones, rotated 45° so the
 * sharp corner points up. Mirrors `border-radius:<sharp> 50% 50% 50%; transform:rotate(45deg)`.
 */
fun dropletShape(sharpCorner: Dp): RoundedCornerShape = RoundedCornerShape(
    topStart = CornerSize(sharpCorner),
    topEnd = CornerSize(50),
    bottomEnd = CornerSize(50),
    bottomStart = CornerSize(50),
)

/** A single droplet of [size], filled with [color]. */
@Composable
fun Droplet(
    size: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    sharpCorner: Dp = size * 0.107f,
) {
    Box(
        modifier
            .size(size)
            .rotate(45f)
            .background(color, dropletShape(sharpCorner)),
    )
}

/**
 * The three-layer brand mark. Layers step down-left from palest to deepest, with a soft blue glow
 * behind and a translucent highlight on the front droplet.
 *
 * [size] is the overall box; every child dimension is a fixed fraction of it, so the same
 * composable serves the 88dp splash mark and the 42dp dashboard header mark.
 */
@Composable
fun JalrakshaMark(
    size: Dp,
    modifier: Modifier = Modifier,
    showGlow: Boolean = true,
) {
    val droplet = size * 0.636f
    val step = size * 0.091f
    val sharp = size * 0.068f

    Box(modifier.size(size)) {
        if (showGlow) {
            Box(
                Modifier
                    .size(size)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(JrColor.Blue.copy(alpha = 0.16f), Color.Transparent),
                            radius = with(LocalDensity.current) { size.toPx() * 0.35f },
                        ),
                    ),
            )
        }
        // Back to front: palest tint, mid blue, deep navy.
        listOf(JrColor.Tint to 1f, JrColor.Blue to 0.9f, JrColor.Deep to 1f)
            .forEachIndexed { i, (color, layerAlpha) ->
                Box(
                    Modifier
                        .offset(x = size * 0.341f - step * i, y = size * 0.114f + step * i)
                        .size(droplet)
                        .rotate(45f)
                        .alpha(layerAlpha)
                        .background(color, dropletShape(sharp)),
                )
            }
        // Specular highlight on the front droplet.
        Box(
            Modifier
                .offset(x = size * 0.386f, y = size * 0.568f)
                .size(size * 0.125f)
                .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(50)),
        )
    }
}
