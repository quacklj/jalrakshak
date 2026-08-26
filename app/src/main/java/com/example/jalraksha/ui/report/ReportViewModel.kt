package com.example.jalraksha.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.jalraksha.ServiceLocator
import com.example.jalraksha.data.AccountRepository
import com.example.jalraksha.data.SessionStore
import com.example.jalraksha.data.WaterRepository
import com.example.jalraksha.data.model.PastReport
import com.example.jalraksha.data.model.ReportDraft
import com.example.jalraksha.data.model.ReportSeverity
import com.example.jalraksha.data.model.Village
import com.example.jalraksha.data.model.WaterIssue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How the last submit attempt ended. Drives the one inline message slot on the screen. */
enum class SubmitOutcome { None, Sent, Failed }

data class ReportUiState(
    val village: Village? = null,
    val issue: WaterIssue = WaterIssue.Taste,
    val severity: ReportSeverity = ReportSeverity.Concerning,
    val note: String = "",
    val hasPhoto: Boolean = false,
    val sourceKey: String = "",
    val sourceNumber: Int = 0,
    val recent: List<PastReport> = emptyList(),
    val submitting: Boolean = false,
    val outcome: SubmitOutcome = SubmitOutcome.None,
) {
    /**
     * An issue is always selected, so the only thing that can block a report is an empty note on
     * "Other" — where the tag alone says nothing a field tester could act on.
     */
    val canSubmit: Boolean
        get() = !submitting && (issue != WaterIssue.Other || note.isNotBlank())
}

class ReportViewModel(
    private val account: AccountRepository,
    private val water: WaterRepository,
    private val session: SessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ReportUiState())
    val state: StateFlow<ReportUiState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val villageId = session.session.first().villageId
            val village = water.villages().getOrNull()?.firstOrNull { it.id == villageId }
            val profile = account.loadProfile().getOrNull()
            val recent = account.recentReports().getOrNull().orEmpty()
            _state.update {
                it.copy(
                    village = village,
                    recent = recent,
                    sourceKey = profile?.sourceKey.orEmpty(),
                    sourceNumber = profile?.sourceNumber ?: 0,
                )
            }
        }
    }

    fun selectIssue(issue: WaterIssue) =
        _state.update { it.copy(issue = issue, outcome = SubmitOutcome.None) }

    fun selectSeverity(severity: ReportSeverity) =
        _state.update { it.copy(severity = severity, outcome = SubmitOutcome.None) }

    fun onNoteChange(note: String) =
        _state.update { it.copy(note = note.take(NOTE_MAX_LENGTH), outcome = SubmitOutcome.None) }

    /**
     * Stands in for the camera and picker, which are outside this screen's scope. The flag is what
     * the report carries either way, so the rest of the flow is real.
     */
    fun togglePhoto() = _state.update { it.copy(hasPhoto = !it.hasPhoto) }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(submitting = true, outcome = SubmitOutcome.None) }
        viewModelScope.launch {
            account.submitReport(
                ReportDraft(
                    issueKey = current.issue.wire,
                    severityKey = current.severity.wire,
                    note = current.note,
                    sourceKey = current.sourceKey,
                    sourceNumber = current.sourceNumber,
                    hasPhoto = current.hasPhoto,
                ),
            )
                .onSuccess { filed ->
                    // Clear the form but keep the draft's issue selected — a villager reporting a
                    // second problem is usually reporting the same kind again.
                    _state.update {
                        it.copy(
                            note = "",
                            hasPhoto = false,
                            submitting = false,
                            outcome = SubmitOutcome.Sent,
                            recent = listOf(filed) + it.recent,
                        )
                    }
                }
                .onFailure {
                    // The typed note stays on screen; losing it would be worse than the failure.
                    _state.update { it.copy(submitting = false, outcome = SubmitOutcome.Failed) }
                }
        }
    }

    companion object {
        const val NOTE_MAX_LENGTH = 300

        val Factory = viewModelFactory {
            initializer {
                ReportViewModel(
                    ServiceLocator.accountRepository,
                    ServiceLocator.waterRepository,
                    ServiceLocator.sessionStore,
                )
            }
        }
    }
}
