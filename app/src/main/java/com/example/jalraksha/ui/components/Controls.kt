package com.example.jalraksha.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.jalraksha.ui.theme.JrColor
import com.example.jalraksha.ui.theme.JrType

/**
 * The filled call-to-action: 58dp tall, 20dp radius, deep-blue glow underneath.
 *
 * Renders a spinner in place of the label while [loading], and dims to 45% when disabled so a
 * blocked action still reads as the same button rather than disappearing.
 */
@Composable
fun JrPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val background by animateColorAsState(
        targetValue = when {
            !enabled || loading -> JrColor.Primary.copy(alpha = 0.45f)
            pressed -> JrColor.Deep
            else -> JrColor.Primary
        },
        label = "primaryButtonBackground",
    )

    Box(
        modifier
            .fillMaxWidth()
            .height(58.dp)
            .softShadow(radius = 20.dp, color = JrColor.Primary.copy(alpha = 0.45f))
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled && !loading,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Color.White,
                strokeWidth = 2.5.dp,
            )
        } else {
            Text(text, style = JrType.Button, color = Color.White)
        }
    }
}

/** The outlined alternative action: white fill, hairline border, 56dp tall. */
@Composable
fun JrSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Overridden only by Sign out, the one destructive action in the app. */
    contentColor: Color = JrColor.Ink,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val background by animateColorAsState(
        targetValue = when {
            pressed && contentColor == JrColor.Danger -> JrColor.DangerWash
            pressed -> JrColor.SurfaceMuted
            else -> JrColor.Surface
        },
        label = "secondaryButtonBackground",
    )

    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .border(1.5.dp, JrColor.Border, RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = JrType.Button.copy(fontSize = JrType.Button.fontSize * 0.94f),
            color = contentColor,
        )
    }
}

/** 42dp rounded-square back affordance with a hand-drawn chevron. */
@Composable
fun JrBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(JrColor.SurfaceMuted)
            .border(1.5.dp, JrColor.BorderSoft, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Chevron(color = JrColor.Ink, modifier = Modifier.padding(start = 3.dp))
    }
}

/**
 * A chevron built the way the design builds it — two borders of a square, rotated. Cheaper than
 * shipping an icon font for the two arrows the whole app uses.
 */
@Composable
private fun Chevron(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(9.dp)
            .rotate(45f)
            .drawLeftBottom(color),
    )
}

/** The onboarding progress indicator: [total] bars, the first [completed] of them filled. */
@Composable
fun JrStepDots(completed: Int, total: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { i ->
            Box(
                Modifier
                    .width(26.dp)
                    .height(4.dp)
                    .background(
                        if (i < completed) JrColor.Primary else JrColor.Border,
                        RoundedCornerShape(2.dp),
                    ),
            )
        }
    }
}

/** A white check on a filled circle — the "selected" and "safe" affirmation across the design. */
@Composable
fun JrCheck(size: Dp, background: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .background(background, RoundedCornerShape(50)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size * 0.45f, size * 0.25f)
                .rotate(-45f)
                .drawLeftBottom(Color.White, strokeWidth = 2.dp),
        )
    }
}

/** Rounded pill with text, used for score badges and status chips. */
@Composable
fun JrChip(
    text: String,
    background: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    border: Color? = null,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .then(if (border != null) Modifier.border(1.dp, border, RoundedCornerShape(50)) else Modifier)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(text, style = JrType.Caption, color = contentColor)
    }
}
