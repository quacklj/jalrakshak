package com.example.jalraksha.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.jalraksha.locale.AppLocales
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "jalraksha_session")

/** What the app remembers between launches: the chosen language, the bound village, the session. */
data class Session(
    /** Null until the user has actually picked one — [languageOrDefault] resolves it for display. */
    val languageCode: String? = null,
    val villageId: String? = null,
    val keepSignedIn: Boolean = true,
) {
    /** Onboarding is complete once both onboarding answers are on disk. */
    val isOnboarded: Boolean get() = languageCode != null && villageId != null

    /** The language to render in right now: the chosen one, else the device's, else English. */
    val languageOrDefault: String get() = languageCode ?: AppLocales.defaultCode()
}

/**
 * Local persistence for the two onboarding answers.
 *
 * Firestore is the source of truth for a user's village and language; this cache exists so the app
 * can route straight to the dashboard on a cold start, and so it can render the first frame in the
 * right language without waiting on the network.
 */
class SessionStore(private val context: Context) {

    val session: Flow<Session> = context.dataStore.data.map { prefs ->
        Session(
            languageCode = prefs[KeyLanguage],
            villageId = prefs[KeyVillage],
            keepSignedIn = prefs[KeyKeepSignedIn] ?: true,
        )
    }

    /** Just the language, for the many collectors that care about nothing else. */
    val languageCode: Flow<String> = session.map { it.languageOrDefault }

    suspend fun setLanguage(code: String) {
        context.dataStore.edit { it[KeyLanguage] = code }
    }

    suspend fun setVillage(villageId: String) {
        context.dataStore.edit { it[KeyVillage] = villageId }
    }

    suspend fun setKeepSignedIn(keep: Boolean) {
        context.dataStore.edit { it[KeyKeepSignedIn] = keep }
    }

    /** Clears the account binding on sign-out. The language survives — it is a device preference. */
    suspend fun clearAccountBinding() {
        context.dataStore.edit { it.remove(KeyVillage) }
    }

    private companion object {
        val KeyLanguage = stringPreferencesKey("language_code")
        val KeyVillage = stringPreferencesKey("village_id")
        val KeyKeepSignedIn = booleanPreferencesKey("keep_signed_in")
    }
}
