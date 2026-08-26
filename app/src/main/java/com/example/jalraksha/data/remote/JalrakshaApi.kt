package com.example.jalraksha.data.remote

import com.example.jalraksha.data.model.PastReport
import com.example.jalraksha.data.model.Profile
import com.example.jalraksha.data.model.ReportDraft
import com.example.jalraksha.data.model.TrendsReport
import com.example.jalraksha.data.model.Village
import com.example.jalraksha.data.model.WaterReport
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The central dashboard's HTTP API, served from Railway.
 *
 * Firebase holds identity and raw sensor documents; Railway does the scoring, the history rollups
 * and the fan-out of advisories, so the app reads its dashboard numbers from here rather than
 * recomputing them on-device.
 */
interface JalrakshaApi {

    /** Villages the user may bind to. Backs screen 03. */
    @GET("v1/villages")
    suspend fun villages(): List<Village>

    /** The scored dashboard payload for one village. Backs screen 04. */
    @GET("v1/villages/{villageId}/report")
    suspend fun report(@Path("villageId") villageId: String): WaterReport

    /** The scored trend window for screen 05. [range] is `7D`, `30D` or `1Y`. */
    @GET("v1/villages/{villageId}/trends")
    suspend fun trends(
        @Path("villageId") villageId: String,
        @Query("range") range: String,
    ): TrendsReport

    /** The signed-in user's account, for screen 07. */
    @GET("v1/profile")
    suspend fun profile(): Profile

    /** Turns unsafe-water alerts on or off for this account. */
    @PATCH("v1/profile/alerts")
    suspend fun updateAlerts(@Body body: AlertPreference)

    /** Reports this user has already filed, newest first. Backs screen 06's history list. */
    @GET("v1/reports")
    suspend fun reports(): List<PastReport>

    /** Files a new report. Returns it as stored, with its id and status. */
    @POST("v1/reports")
    suspend fun submitReport(@Body draft: ReportDraft): PastReport

    /**
     * Registers this install's FCM token so the dashboard can push an advisory to exactly the
     * villages affected. Called on sign-in and whenever FCM rotates the token.
     */
    @POST("v1/devices")
    suspend fun registerDevice(@Body body: DeviceRegistration)
}

/** Body of [JalrakshaApi.updateAlerts]. */
@Serializable
data class AlertPreference(
    @SerialName("unsafe_alerts") val unsafeAlerts: Boolean,
)

@Serializable
data class DeviceRegistration(
    val uid: String,
    @SerialName("fcm_token") val fcmToken: String,
    @SerialName("village_id") val villageId: String?,
    @SerialName("language_code") val languageCode: String,
    val platform: String = "android",
)
