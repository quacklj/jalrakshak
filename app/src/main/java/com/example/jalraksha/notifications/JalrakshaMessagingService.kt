package com.example.jalraksha.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.jalraksha.MainActivity
import com.example.jalraksha.R
import com.example.jalraksha.ServiceLocator
import com.example.jalraksha.data.remote.DeviceRegistration
import com.example.jalraksha.locale.AppLocales
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Receives pushes from the central dashboard.
 *
 * The dashboard sends *data* messages (not `notification` ones) so the app controls the channel
 * and the tap target even when it is in the foreground — and, just as importantly, so it can pick
 * the right translation. A `notification` message would be rendered by the system in the device
 * language, which is exactly the language the user told us they cannot read.
 *
 * Payload keys: `channel` (`advisory` or `updates`), and then either a `key` naming a message the
 * app has translations for, or a `title`/`body` pair Railway already translated using the
 * `language_code` this device registered.
 */
@SuppressLint("MissingFirebaseInstanceTokenRefresh") // onRegistered supersedes onNewToken.
class JalrakshaMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Replaces the deprecated `onNewToken`; fires on first registration and on every rotation. */
    override fun onRegistered(token: String) {
        // FCM rotates tokens on reinstall and on restore-to-new-device. Railway keeps the mapping
        // of token -> village so an advisory reaches only the villages it applies to, and the
        // language so free-text advisories arrive already translated.
        scope.launch {
            val user = ServiceLocator.authRepository.currentUser.first() ?: return@launch
            val session = ServiceLocator.sessionStore.session.first()
            runCatching {
                ServiceLocator.api.registerDevice(
                    DeviceRegistration(
                        uid = user.uid,
                        fcmToken = token,
                        villageId = session.villageId,
                        languageCode = session.languageOrDefault,
                    ),
                )
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val channelId = when (message.data["channel"]) {
            "advisory" -> NotificationChannels.ADVISORY
            else -> NotificationChannels.UPDATES
        }

        val known = message.data["key"]?.let(::localizedMessage)
        val title = known?.first
            ?: message.data["title"]
            ?: message.notification?.title
            ?: return
        val body = known?.second
            ?: message.data["body"]
            ?: message.notification?.body.orEmpty()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // Android 13+ and the user hasn't granted notifications. Nothing to do — the dashboard
            // banner on screen 04 still carries the same advisory next time the app opens.
            return
        }

        val tapIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .setPriority(
                if (channelId == NotificationChannels.ADVISORY) {
                    NotificationCompat.PRIORITY_HIGH
                } else {
                    NotificationCompat.PRIORITY_DEFAULT
                },
            )
            .build()

        NotificationManagerCompat.from(this).notify(message.messageId.hashCode(), notification)
    }

    /**
     * Recurring pushes the app has its own translations for. Anything else arrives as free text
     * that Railway translated for the language this device registered.
     */
    private fun localizedMessage(key: String): Pair<String, String>? {
        val res = AppLocales.resources(this, ServiceLocator.currentLanguageCode)
        val ids = when (key) {
            "tanker_check" ->
                R.string.notice_tanker_check_title to R.string.notice_tanker_check_body
            else -> return null
        }
        return res.getString(ids.first) to res.getString(ids.second)
    }
}
