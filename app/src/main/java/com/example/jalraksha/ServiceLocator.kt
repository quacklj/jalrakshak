package com.example.jalraksha

import android.content.Context
import com.example.jalraksha.data.AccountRepository
import com.example.jalraksha.data.FakeAccountRepository
import com.example.jalraksha.data.FakeWaterRepository
import com.example.jalraksha.data.RemoteAccountRepository
import com.example.jalraksha.data.RemoteWaterRepository
import com.example.jalraksha.data.SessionStore
import com.example.jalraksha.data.WaterRepository
import com.example.jalraksha.data.auth.AuthRepository
import com.example.jalraksha.data.auth.FakeAuthRepository
import com.example.jalraksha.data.auth.FirebaseAuthRepository
import com.example.jalraksha.data.remote.JalrakshaApi
import com.example.jalraksha.locale.AppLocales
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Hand-rolled dependency graph.
 *
 * The app is four screens and three repositories; a DI framework would cost more to read than it
 * saves. Everything is created lazily and lives for the process.
 *
 * Which implementation you get depends on `BuildConfig.HAS_FIREBASE`, which is true only when
 * `app/google-services.json` exists at build time. Without it the app runs entirely on the fakes,
 * which serve the design's own sample numbers.
 */
object ServiceLocator {

    private lateinit var appContext: Context

    /**
     * The language the app is currently rendering in, mirrored out of DataStore by [JalrakshaApp]
     * so non-suspending callers — an OkHttp interceptor, a notification builder — can read it
     * without blocking. Composables read [com.example.jalraksha.locale.LocalAppLocale] instead.
     */
    @Volatile
    var currentLanguageCode: String = AppLocales.DEFAULT_CODE

    fun init(context: Context) {
        appContext = context.applicationContext
        currentLanguageCode = AppLocales.defaultCode()
    }

    val sessionStore: SessionStore by lazy { SessionStore(appContext) }

    val authRepository: AuthRepository by lazy {
        if (BuildConfig.HAS_FIREBASE) FirebaseAuthRepository() else FakeAuthRepository()
    }

    val waterRepository: WaterRepository by lazy {
        if (BuildConfig.HAS_FIREBASE) RemoteWaterRepository(api) else FakeWaterRepository()
    }

    val accountRepository: AccountRepository by lazy {
        if (BuildConfig.HAS_FIREBASE) RemoteAccountRepository(api) else FakeAccountRepository()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            // Free-text advisories are written by an officer and translated server-side; the header
            // is how Railway knows which language to send back. Keys the app resolves itself are
            // unaffected.
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Accept-Language", currentLanguageCode)
                        .build(),
                )
            }
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC },
                    )
                }
            }
            .build()
    }

    val api: JalrakshaApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(httpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(JalrakshaApi::class.java)
    }
}
