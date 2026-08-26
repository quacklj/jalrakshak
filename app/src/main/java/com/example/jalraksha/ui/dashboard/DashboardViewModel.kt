package com.example.jalraksha.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.jalraksha.ServiceLocator
import com.example.jalraksha.data.SessionStore
import com.example.jalraksha.data.WaterRepository
import com.example.jalraksha.data.model.Village
import com.example.jalraksha.data.model.WaterReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardUiState(
    val village: Village? = null,
    val report: WaterReport? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    /** The last load failed. The sentence to show lives in `strings.xml`, not here. */
    val failed: Boolean = false,
    /** No village is bound, so the navigator has to send the user back to screen 03. */
    val needsVillage: Boolean = false,
    val hasUnreadNotice: Boolean = true,
)

class DashboardViewModel(
    private val water: WaterRepository,
    private val session: SessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load(isRefresh: Boolean = false) {
        _state.update { it.copy(loading = !isRefresh, refreshing = isRefresh, failed = false) }
        viewModelScope.launch {
            val villageId = session.session.first().villageId
            if (villageId == null) {
                // Onboarding was skipped or the binding was cleared; the navigator sends the user
                // back to screen 03 rather than showing an empty dashboard.
                _state.update { it.copy(loading = false, refreshing = false, needsVillage = true) }
                return@launch
            }

            val villages = water.villages().getOrNull().orEmpty()
            water.report(villageId)
                .onSuccess { report ->
                    _state.update {
                        it.copy(
                            village = villages.firstOrNull { v -> v.id == villageId },
                            report = report,
                            loading = false,
                            refreshing = false,
                        )
                    }
                }
                .onFailure {
                    _state.update { it.copy(loading = false, refreshing = false, failed = true) }
                }
        }
    }

    fun markNoticesRead() = _state.update { it.copy(hasUnreadNotice = false) }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                DashboardViewModel(ServiceLocator.waterRepository, ServiceLocator.sessionStore)
            }
        }
    }
}
