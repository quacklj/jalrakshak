package com.example.jalraksha.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.jalraksha.R

val Manrope = FontFamily(
    Font(R.font.manrope_light, FontWeight.Light),
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)

/**
 * The handful of type ramps the design actually uses. The design leans on ExtraBold + tight
 * negative tracking for anything display-sized, and SemiBold/Bold at small sizes for labels.
 */
object JrType {
    /** 40sp canvas headline. */
    val Display = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 40.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.03).em,
    )

    /** 30sp screen title ("Welcome back", "Choose your language"). */
    val Title = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 30.sp,
        lineHeight = 34.5.sp,
        letterSpacing = (-0.03).em,
    )

    /** 42sp average-score readout on the trends screen. */
    val Headline = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 42.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.04).em,
    )

    /** 22sp title on a screen that has a header rather than a hero. */
    val ScreenTitle = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.03).em,
    )

    /** 62sp water score readout. */
    val Score = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 62.sp,
        lineHeight = 62.sp,
        letterSpacing = (-0.05).em,
    )

    /** 22sp metric value on a parameter card. */
    val Metric = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.03).em,
    )

    /** 19sp village name. */
    val CardTitle = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 19.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.02).em,
    )

    /** 15–16sp section heading. */
    val Section = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 15.sp,
        lineHeight = 19.sp,
        letterSpacing = (-0.02).em,
    )

    /** 14sp supporting copy under a title. */
    val Body = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    )

    /** 12.5sp copy inside tinted panels. */
    val BodySmall = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
    )

    /** 12sp field label / caption. */
    val Label = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.02.em,
    )

    /** 11–11.5sp chip and tick text. */
    val Caption = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Bold,
        fontSize = 11.5.sp,
        lineHeight = 15.sp,
    )

    /** 13sp all-caps wordmark / eyebrow with wide tracking. */
    val Eyebrow = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.2.em,
    )

    /** 16sp primary button label. */
    val Button = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.01.em,
    )

    /** 16sp text typed into an input. */
    val Input = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
    )
}

internal val JalrakshaTypography = Typography(
    displayLarge = JrType.Display,
    headlineLarge = JrType.Title,
    titleLarge = JrType.CardTitle,
    titleMedium = JrType.Section,
    bodyLarge = JrType.Body,
    bodyMedium = JrType.BodySmall,
    labelLarge = JrType.Button,
    labelMedium = JrType.Label,
    labelSmall = JrType.Caption,
)
