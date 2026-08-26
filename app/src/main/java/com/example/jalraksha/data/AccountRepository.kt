package com.example.jalraksha.data

import com.example.jalraksha.data.model.PastReport
import com.example.jalraksha.data.model.Profile
import com.example.jalraksha.data.model.ReportDraft
import com.example.jalraksha.data.remote.AlertPreference
import com.example.jalraksha.data.remote.JalrakshaApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The account behind screens 06 and 07: who the user is, and what they have reported. */
interface AccountRepository {

    val profile: StateFlow<Profile?>

    suspend fun loadProfile(): Result<Profile>

    suspend fun setUnsafeAlerts(enabled: Boolean): Result<Unit>

    suspend fun recentReports(): Result<List<PastReport>>

    suspend fun submitReport(draft: ReportDraft): Result<PastReport>
}

/** Talks to the Railway-hosted central dashboard. */
class RemoteAccountRepository(private val api: JalrakshaApi) : AccountRepository {

    private val _profile = MutableStateFlow<Profile?>(null)
    override val profile: StateFlow<Profile?> = _profile.asStateFlow()

    override suspend fun loadProfile(): Result<Profile> = runCatching {
        api.profile().also { _profile.value = it }
    }

    override suspend fun setUnsafeAlerts(enabled: Boolean): Result<Unit> = runCatching {
        api.updateAlerts(AlertPreference(enabled))
        // Reflect it locally straight away; the toggle should not wait on a round trip.
        _profile.value = _profile.value?.copy(unsafeAlerts = enabled)
    }

    override suspend fun recentReports(): Result<List<PastReport>> = runCatching { api.reports() }

    override suspend fun submitReport(draft: ReportDraft): Result<PastReport> =
        runCatching { api.submitReport(draft) }
}

/**
 * In-memory account used until the Railway service exists. Submitting a report prepends it to the
 * list, so the screen's own behaviour is exercised rather than faked.
 */
class FakeAccountRepository : AccountRepository {

    private val _profile = MutableStateFlow<Profile?>(null)
    override val profile: StateFlow<Profile?> = _profile.asStateFlow()

    private var reports = SampleAccount.pastReports

    override suspend fun loadProfile(): Result<Profile> {
        delay(NETWORK_DELAY_MS)
        val profile = _profile.value ?: SampleAccount.profile
        _profile.value = profile
        return Result.success(profile)
    }

    override suspend fun setUnsafeAlerts(enabled: Boolean): Result<Unit> {
        _profile.value = (_profile.value ?: SampleAccount.profile).copy(unsafeAlerts = enabled)
        return Result.success(Unit)
    }

    override suspend fun recentReports(): Result<List<PastReport>> {
        delay(NETWORK_DELAY_MS)
        return Result.success(reports)
    }

    override suspend fun submitReport(draft: ReportDraft): Result<PastReport> {
        delay(NETWORK_DELAY_MS)
        val filed = PastReport(
            id = "local-${System.currentTimeMillis()}",
            issueKey = draft.issueKey,
            note = draft.note,
            createdAt = System.currentTimeMillis() / 1000,
            sourceKey = draft.sourceKey,
            sourceNumber = draft.sourceNumber,
            statusKey = "open",
        )
        reports = listOf(filed) + reports
        _profile.value = (_profile.value ?: SampleAccount.profile)
            .let { it.copy(reportsFiled = it.reportsFiled + 1) }
        return Result.success(filed)
    }

    private companion object {
        const val NETWORK_DELAY_MS = 500L
    }
}
