package com.example.jalraksha.ui.language

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.jalraksha.ServiceLocator
import com.example.jalraksha.data.SessionStore
import com.example.jalraksha.locale.AppLocales
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LanguageViewModel(private val session: SessionStore) : ViewModel() {

    /**
     * Read straight off DataStore rather than kept as local state, so the tile that looks selected
     * is always the one the whole app is actually rendering in.
     */
    val selected: StateFlow<String> = session.languageCode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppLocales.defaultCode(),
    )

    /**
     * Persists immediately on tap. The activity collects the same flow, so the language provider
     * swaps and every string on screen — including this screen's own — re-resolves on the next
     * frame. No activity recreation, so the tap does not flash.
     */
    fun select(code: String) {
        viewModelScope.launch { session.setLanguage(code) }
    }

    /**
     * Continue and Skip both land here. Skip is not "no answer" — it means "the one already
     * highlighted is fine", so the choice is written either way and onboarding never leaves a
     * null language behind.
     */
    fun commit(onDone: () -> Unit) {
        viewModelScope.launch {
            session.setLanguage(selected.value)
            onDone()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { LanguageViewModel(ServiceLocator.sessionStore) }
        }
    }
}
