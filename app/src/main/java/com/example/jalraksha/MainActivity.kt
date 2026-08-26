package com.example.jalraksha

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.jalraksha.data.Session
import com.example.jalraksha.locale.ProvideAppLocale
import com.example.jalraksha.ui.JalrakshaNavHost
import com.example.jalraksha.ui.Routes
import com.example.jalraksha.ui.theme.JalrakshaTheme
import com.example.jalraksha.ui.theme.JrColor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /**
     * Where to open, and in which language. Null until the auth state and the stored session have
     * been read; the splash screen stays up for exactly that long, so the user never sees the
     * sign-in screen flash — or an English frame before the Kannada one.
     */
    private var startup by mutableStateOf<Startup?>(null)

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Advisories are best-effort. If the user declines, the dashboard's notice banner still
            // carries the same message the next time they open the app.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { startup == null }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            startup = resolveStartup()
            requestNotificationPermissionIfNeeded()
        }

        setContent {
            val resolved = startup
            if (resolved == null) {
                Box(Modifier.fillMaxSize().background(JrColor.Surface))
                return@setContent
            }

            // After the first frame the language comes from DataStore, so tapping a tile on screen
            // 02 re-renders the whole tree in that language on the next frame.
            val session by ServiceLocator.sessionStore.session
                .collectAsStateWithLifecycle(initialValue = resolved.session)

            ProvideAppLocale(session.languageOrDefault) {
                JalrakshaTheme {
                    JalrakshaNavHost(startDestination = resolved.startDestination)
                }
            }
        }
    }

    /**
     * A returning user with a village already bound lands on the dashboard; anyone mid-onboarding
     * resumes at the first step they have not answered.
     */
    private suspend fun resolveStartup(): Startup {
        val signedIn = ServiceLocator.authRepository.currentUser.first() != null
        val session = ServiceLocator.sessionStore.session.first()
        val destination = when {
            !signedIn -> Routes.SIGN_IN
            session.languageCode == null -> Routes.LANGUAGE
            session.villageId == null -> Routes.VILLAGE
            else -> Routes.MAIN
        }
        return Startup(destination, session)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private data class Startup(val startDestination: String, val session: Session)
}
