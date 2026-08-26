package com.example.jalraksha.data

import com.example.jalraksha.data.model.TrendRange
import com.example.jalraksha.data.model.TrendsReport
import com.example.jalraksha.data.model.Village
import com.example.jalraksha.data.model.WaterReport
import com.example.jalraksha.data.remote.JalrakshaApi
import kotlinx.coroutines.delay

/** Villages and their scored water reports. */
interface WaterRepository {
    suspend fun villages(): Result<List<Village>>
    suspend fun report(villageId: String): Result<WaterReport>
    suspend fun trends(villageId: String, range: TrendRange): Result<TrendsReport>
}

/** Talks to the Railway-hosted central dashboard. */
class RemoteWaterRepository(private val api: JalrakshaApi) : WaterRepository {

    override suspend fun villages(): Result<List<Village>> = runCatching { api.villages() }

    override suspend fun report(villageId: String): Result<WaterReport> =
        runCatching { api.report(villageId) }

    override suspend fun trends(villageId: String, range: TrendRange): Result<TrendsReport> =
        runCatching { api.trends(villageId, range.wire) }
}

/**
 * Serves [SampleData] so the dashboard renders the design's own numbers before the Railway service
 * is deployed. The delay is there so loading states are exercised rather than skipped.
 */
class FakeWaterRepository : WaterRepository {

    override suspend fun villages(): Result<List<Village>> {
        delay(NETWORK_DELAY_MS)
        return Result.success(SampleData.villages)
    }

    override suspend fun report(villageId: String): Result<WaterReport> {
        delay(NETWORK_DELAY_MS)
        return Result.success(SampleData.reportFor(villageId))
    }

    override suspend fun trends(villageId: String, range: TrendRange): Result<TrendsReport> {
        delay(NETWORK_DELAY_MS)
        return Result.success(SampleTrends.forRange(range).copy(villageId = villageId))
    }

    private companion object {
        const val NETWORK_DELAY_MS = 500L
    }
}
