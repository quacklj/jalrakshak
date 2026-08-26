package com.example.jalraksha.ui.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.jalraksha.ServiceLocator
import com.example.jalraksha.data.SessionStore
import com.example.jalraksha.data.WaterRepository
import com.example.jalraksha.data.model.TrendRange
import com.example.jalraksha.data.model.TrendsReport
import com.example.jalraksha.data.model.Village
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrendsUiState(
    val village: Village? = null,
    val report: TrendsReport? = null,
    val range: TrendRange = TrendRange.Week,
    val loading: Boolean = true,
    val failed: Boolean = false,
)

class TrendsViewModel(
    private val water: WaterRepository,
    private val session: SessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(TrendsUiState())
    val state: StateFlow<TrendsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            val villageId = session.session.first().villageId ?: return@launch
            val villages = water.villages().getOrNull().orEmpty()
            water.trends(villageId, _state.value.range)
                .onSuccess { report ->
                    _state.update {
                        it.copy(
                            village = villages.firstOrNull { v -> v.id == villageId },
                            report = report,
                            loading = false,
                        )
                    }
                }
                .onFailure { _state.update { it.copy(loading = false, failed = true) } }
        }
    }

    /** Switching range keeps the current chart on screen while the new window loads. */
    fun selectRange(range: TrendRange) {
        if (range == _state.value.range) return
        _state.update { it.copy(range = range) }
        load()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                TrendsViewModel(ServiceLocator.waterRepository, ServiceLocator.sessionStore)
            }
        }
    }
}
