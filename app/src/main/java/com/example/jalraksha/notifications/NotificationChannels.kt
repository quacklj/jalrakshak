package com.example.jalraksha.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService
import com.example.jalraksha.R
import com.example.jalraksha.locale.AppLocales

/**
 * The two notification channels, named in the user's chosen language.
 *
 * Android caches a channel's name and description at creation, and those strings show up in the
 * system Settings app — outside anything Compose controls. Re-creating a channel with the same id
 * updates its name in place, so [ensure] is called again whenever the language changes; otherwise
 * a Kannada reader would find two English rows in their notification settings.
 */
object NotificationChannels {

    /** Urgent water-safety warnings pushed by the central dashboard. */
    const val ADVISORY = "jalraksha_advisory"

    /** Daily score changes and scheduled tanker checks. */
    const val UPDATES = "jalraksha_updates"

    fun ensure(context: Context, languageCode: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return
        val res = AppLocales.resources(context, languageCode)

        manager.createNotificationChannel(
            NotificationChannel(
                ADVISORY,
                res.getString(R.string.channel_advisory),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = res.getString(R.string.channel_advisory_description) },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                UPDATES,
                res.getString(R.string.channel_updates),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = res.getString(R.string.channel_updates_description) },
        )
    }
}
