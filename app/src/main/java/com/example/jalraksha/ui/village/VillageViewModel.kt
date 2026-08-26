package com.example.jalraksha.ui.village

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.jalraksha.ServiceLocator
import com.example.jalraksha.data.SessionStore
import com.example.jalraksha.data.WaterRepository
import com.example.jalraksha.data.model.Village
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VillageUiState(
    val villages: List<Village> = emptyList(),
    val selectedId: String? = null,
    val loading: Boolean = true,
    /**
     * Whether the last load failed. A flag rather than a message — the sentence to show lives in
     * `strings.xml` so it follows the chosen language, and this class has no locale.
     */
    val failed: Boolean = false,
) {
    val selected: Village? get() = villages.firstOrNull { it.id == selectedId }
}

class VillageViewModel(
    private val water: WaterRepository,
    private val session: SessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(VillageUiState())
    val state: StateFlow<VillageUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            val remembered = session.session.first().villageId
            water.villages()
                .onSuccess { villages ->
                    _state.update {
                        it.copy(
                            villages = villages,
                            loading = false,
                            // Default to whatever is already bound, else the first village listed.
                            selectedId = remembered?.takeIf { id -> villages.any { v -> v.id == id } }
                                ?: villages.firstOrNull()?.id,
                        )
                    }
                }
                .onFailure {
                    _state.update { it.copy(loading = false, failed = true) }
                }
        }
    }

    fun select(id: String) = _state.update { it.copy(selectedId = id) }

    /** Binds the account to the selected village, then hands control back for navigation. */
    fun confirm(onDone: () -> Unit) {
        val id = _state.value.selectedId ?: return
        viewModelScope.launch {
            session.setVillage(id)
            onDone()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                VillageViewModel(ServiceLocator.waterRepository, ServiceLocator.sessionStore)
            }
        }
    }
}
