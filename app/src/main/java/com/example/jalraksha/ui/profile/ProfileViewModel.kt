package com.example.jalraksha.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.jalraksha.ServiceLocator
import com.example.jalraksha.data.AccountRepository
import com.example.jalraksha.data.SessionStore
import com.example.jalraksha.data.WaterRepository
import com.example.jalraksha.data.auth.AuthRepository
import com.example.jalraksha.data.model.Profile
import com.example.jalraksha.data.model.Village
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val profile: Profile? = null,
    val village: Village? = null,
    val languageCode: String = "en",
    val loading: Boolean = true,
    val signedOut: Boolean = false,
)

class ProfileViewModel(
    private val account: AccountRepository,
    private val water: WaterRepository,
    private val auth: AuthRepository,
    private val session: SessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val stored = session.session.first()
            val villages = water.villages().getOrNull().orEmpty()
            val profile = account.loadProfile().getOrNull()
            _state.update {
                it.copy(
                    profile = profile,
                    village = villages.firstOrNull { v -> v.id == stored.villageId },
                    languageCode = stored.languageOrDefault,
                    loading = false,
                )
            }
        }
    }

    /**
     * Flips the switch straight away and persists behind it. A toggle that waits on the network
     * feels broken on a village connection, and the repository re-reads the truth on next load.
     */
    fun setUnsafeAlerts(enabled: Boolean) {
        _state.update { it.copy(profile = it.profile?.copy(unsafeAlerts = enabled)) }
        viewModelScope.launch { account.setUnsafeAlerts(enabled) }
    }

    fun signOut() {
        viewModelScope.launch {
            auth.signOut()
            // The village binding goes; the language stays, because it is how the sign-in screen
            // will be read on the way back in.
            session.clearAccountBinding()
            _state.update { it.copy(signedOut = true) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                ProfileViewModel(
                    ServiceLocator.accountRepository,
                    ServiceLocator.waterRepository,
                    ServiceLocator.authRepository,
                    ServiceLocator.sessionStore,
                )
            }
        }
    }
}
