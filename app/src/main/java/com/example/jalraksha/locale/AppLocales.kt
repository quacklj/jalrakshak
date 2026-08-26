package com.example.jalraksha.locale

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

/**
 * The app's language, chosen in-app rather than taken from the device.
 *
 * A villager's handset is very often set to a language they did not pick and cannot read, so the
 * system locale is the wrong source of truth here. Everything user-visible resolves against the
 * language selected on screen 02 instead — including notifications, which are built outside any
 * Composable and would otherwise silently fall back to the device language.
 */
object AppLocales {

    /** BCP-47 codes the app ships translations for. Must match the `values-<code>` folders. */
    val supportedCodes = listOf("en", "hi", "mr", "bn", "te", "ta", "gu", "kn")

    const val DEFAULT_CODE = "en"

    /**
     * The language the app opens in before the user has chosen one: the device's language when we
     * have translations for it, English otherwise. A Marathi handset should not have to pick
     * Marathi.
     */
    fun defaultCode(deviceLocale: Locale = Locale.getDefault()): String =
        supportedCodes.firstOrNull { it == deviceLocale.language } ?: DEFAULT_CODE

    fun locale(languageCode: String): Locale = Locale.forLanguageTag(languageCode)

    /**
     * A [Context] whose resources resolve in [languageCode] regardless of the device setting.
     *
     * Used by the Compose provider and by anything that has to read a string off the main
     * composition — the messaging service, the notification channels.
     */
    fun context(base: Context, languageCode: String): Context {
        val locale = locale(languageCode)
        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return base.createConfigurationContext(configuration)
    }

    fun resources(base: Context, languageCode: String): Resources = context(base, languageCode).resources
}
