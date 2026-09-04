package com.nikac.guider.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nikac.guider.MainActivity
import com.nikac.guider.R
import com.nikac.guider.domain.sleep.ActiveSleepSession
import com.nikac.guider.util.LocalizedFormatters

class HibernationNotificationManager(private val context: Context) {
    fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val trackingChannel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.sleep_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.sleep_notification_channel_description)
            enableVibration(false)
            setSound(null, null)
        }
        val wakePromptChannel = NotificationChannel(
            WAKE_PROMPT_CHANNEL_ID,
            context.getString(R.string.wake_prompt_notification_channel),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.wake_prompt_notification_channel_description)
            enableVibration(false)
            setSound(null, null)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(
            listOf(trackingChannel, wakePromptChannel),
        )
    }

    fun showActiveSession(session: ActiveSleepSession) {
        if (!canPostNotifications()) return

        val sleepStart = LocalizedFormatters.formatShortTime(session.sleepStartsAtEpochMillis)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.sleep_nav_ic)
            .setContentTitle(context.getString(R.string.hibernation_notification_title))
            .setContentText(context.getString(R.string.hibernation_notification_text, sleepStart))
            .setContentIntent(openAppPendingIntent())
            .addAction(
                0,
                context.getString(R.string.hibernation_notification_action),
                wakeUpActionPendingIntent(),
            )
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()

        post(notification)
    }

    fun showWakePrompt() {
        if (!canPostNotifications()) return

        val notification = NotificationCompat.Builder(context, WAKE_PROMPT_CHANNEL_ID)
            .setSmallIcon(R.drawable.sleep_nav_ic)
            .setContentTitle(context.getString(R.string.wake_prompt_notification_title))
            .setContentText(context.getString(R.string.wake_prompt_notification_text))
            .setContentIntent(openAppPendingIntent())
            .addAction(
                0,
                context.getString(R.string.hibernation_notification_action),
                wakeUpActionPendingIntent(),
            )
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        post(notification)
    }

    fun cancelActiveSession() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun wakeUpActionPendingIntent(): PendingIntent {
        val intent = Intent(context, WakeUpReceiver::class.java).apply {
            action = WakeUpReceiver.ACTION_CONFIRM_WAKE_UP
        }
        return PendingIntent.getBroadcast(
            context,
            WAKE_UP_ACTION_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openAppPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = WakeUpReceiver.ACTION_CONFIRM_WAKE_UP
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            OPEN_APP_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun post(notification: android.app.Notification) {
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission can be revoked between the explicit check and this call.
        }
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val CHANNEL_ID = "sleep_tracking"
        const val WAKE_PROMPT_CHANNEL_ID = "sleep_wake_prompt"
        const val NOTIFICATION_ID = 2401
        const val WAKE_UP_ACTION_REQUEST_CODE = 2402
        const val OPEN_APP_REQUEST_CODE = 2403
    }
}
