package com.example.jalraksha.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The Jalraksha palette, lifted verbatim from `Jalraksha Mobile.dc.html`.
 *
 * Names describe the role a colour plays in the design, not its hue, so a screen never has to
 * reason about which particular blue it wants.
 */
object JrColor {
    /** Page behind the phone in the design canvas; also the pull-to-refresh backdrop. */
    val Canvas = Color(0xFFE7EEFA)

    /** Cards, sheets, the sign-in / onboarding screens. */
    val Surface = Color(0xFFFFFFFF)

    /** Scrolling background of the dashboard, and every input field. */
    val SurfaceMuted = Color(0xFFF6F9FF)

    /** Primary text. */
    val Ink = Color(0xFF0A1834)

    /** Body copy on tinted panels. */
    val Slate = Color(0xFF43557A)

    /** Secondary text, captions, labels. */
    val Muted = Color(0xFF6B7C9E)

    /** Axis ticks, disabled nav items, placeholders. */
    val Faint = Color(0xFF9AAAC6)

    /** Brand blue: primary buttons, active states, links. */
    val Primary = Color(0xFF1D4ED8)

    /** Deepest brand blue: pressed states, dark banners, emphasis chips. */
    val Deep = Color(0xFF14307A)

    /** Mid blue, used in the layered droplet mark and gauge gradient. */
    val Blue = Color(0xFF3B82F6)

    /** Light blue, top layer of the droplet mark. */
    val Sky = Color(0xFF8FB2FB)

    /** Palest droplet layer / tint fills. */
    val Tint = Color(0xFFD9E4FE)

    /** Trailing stop of the gauge gradient. */
    val GaugeTail = Color(0xFF7FA8FB)

    /** Body text on [Deep] backgrounds. */
    val OnDeepMuted = Color(0xFFAFC6F7)

    /** Hairline around cards and the phone bezel. */
    val Border = Color(0xFFDCE6F8)

    /** Softer hairline used on inputs and most cards. */
    val BorderSoft = Color(0xFFE6EDFA)

    /** Border on the selected/status chip. */
    val ChipBorder = Color(0xFFD3E0FC)

    /** Fill of the selected/status chip and the active nav pill. */
    val ChipFill = Color(0xFFEAF1FF)

    /** Unfilled part of a progress bar. */
    val Track = Color(0xFFEDF2FC)

    /** Unfilled part of the score gauge arc. */
    val GaugeTrack = Color(0xFFE9EFFA)

    /** Unfilled part of the small conic parameter rings. */
    val RingTrack = Color(0xFFE4EBF9)

    /** Dotted inner ring of the gauge. */
    val GaugeTicks = Color(0xFFE1E9F8)

    /** Dashed gridline behind the score history chart. */
    val GridLine = Color(0xFFC9D8F6)

    /** Outline of an unselected radio. */
    val RadioBorder = Color(0xFFCBD8F0)

    /** Fill of a chip carrying no change — "steady" on the trends screen. */
    val TrackQuiet = Color(0xFFF1F5FD)

    /** Older bars in a parameter-movement row, behind the highlighted recent ones. */
    val BarQuiet = Color(0xFFD4E1FA)

    /** Unhighlighted bars in the monthly safe-days chart. */
    val BarSoft = Color(0xFFCFDEFA)

    /** Chevron on a settings row, quieter than a border. */
    val ChevronQuiet = Color(0xFFC2D0EA)

    /** Destructive text — the only warm colour in the palette, used once, on Sign out. */
    val Danger = Color(0xFFB0405A)

    /** Hover/pressed ground under [Danger]. */
    val DangerWash = Color(0xFFFDF2F5)
}
