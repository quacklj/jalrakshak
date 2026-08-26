package com.example.jalraksha.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val JalrakshaColorScheme = lightColorScheme(
    primary = JrColor.Primary,
    onPrimary = JrColor.Surface,
    primaryContainer = JrColor.ChipFill,
    onPrimaryContainer = JrColor.Deep,
    secondary = JrColor.Blue,
    onSecondary = JrColor.Surface,
    background = JrColor.SurfaceMuted,
    onBackground = JrColor.Ink,
    surface = JrColor.Surface,
    onSurface = JrColor.Ink,
    surfaceVariant = JrColor.SurfaceMuted,
    onSurfaceVariant = JrColor.Muted,
    outline = JrColor.Border,
    outlineVariant = JrColor.BorderSoft,
)

/** Corner radii used across the design: 14/18/20–22/24–26 dp. */
private val JalrakshaShapes = Shapes(
    extraSmall = RoundedCornerShape(11.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

/**
 * Jalraksha is a light-only design — the palette carries meaning (blue = safe water) and there is
 * no dark counterpart in the design file, so we deliberately do not follow the system theme.
 */
@Composable
fun JalrakshaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JalrakshaColorScheme,
        typography = JalrakshaTypography,
        shapes = JalrakshaShapes,
        content = content,
    )
}
