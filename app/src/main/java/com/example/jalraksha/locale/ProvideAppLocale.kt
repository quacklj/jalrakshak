package com.example.jalraksha.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.core.text.layoutDirection
import java.util.Locale

/**
 * The [Locale] the current composition is being read in. Provide it to anything that formats a
 * number or a date, so figures follow the chosen language rather than the device's.
 */
val LocalAppLocale = staticCompositionLocalOf { Locale.ENGLISH }

/**
 * Renders [content] entirely in [languageCode].
 *
 * `stringResource` reads whichever of [LocalResources], [LocalConfiguration] and [LocalContext]
 * the Compose version it was compiled against happens to use, so all three are replaced together.
 * Changing [languageCode] invalidates the composition and every string re-resolves — no activity
 * recreation, so the switch lands on the next frame with no flash and no lost scroll position.
 */
@Composable
fun ProvideAppLocale(languageCode: String, content: @Composable () -> Unit) {
    val base = LocalContext.current

    val localized = remember(base, languageCode) { AppLocales.context(base, languageCode) }
    val locale = remember(languageCode) { AppLocales.locale(languageCode) }
    val layoutDirection = remember(locale) {
        // None of the eight languages is right-to-left today, but reading it off the locale keeps
        // the door open for Urdu or Kashmiri without a second pass over every screen.
        if (locale.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL) {
            LayoutDirection.Rtl
        } else {
            LayoutDirection.Ltr
        }
    }

    @Suppress("DEPRECATION")
    CompositionLocalProvider(
        LocalContext provides localized,
        LocalResources provides localized.resources,
        LocalConfiguration provides localized.resources.configuration,
        LocalLayoutDirection provides layoutDirection,
        LocalAppLocale provides locale,
        content = content,
    )
}
