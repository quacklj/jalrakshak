package com.example.jalraksha

import android.app.Application
import com.example.jalraksha.notifications.NotificationChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn

class JalrakshaApp : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)

        // One place watches the chosen language for everything outside the composition: the
        // notification channel names the system Settings app shows, and the Accept-Language header
        // Railway reads when it translates a free-text advisory.
        ServiceLocator.sessionStore.languageCode
            .distinctUntilChanged()
            .onEach { code ->
                ServiceLocator.currentLanguageCode = code
                NotificationChannels.ensure(this, code)
            }
            .launchIn(scope)
    }
}
