package com.standup.app.core

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.standup.app.MainActivity
import com.standup.app.R
import com.standup.app.data.AppState

object Notifications {
    /** Silent channel: the foreground service plays the audio itself. */
    const val CH_REMINDER = "reminder_active"
    /** Fallback channel: system plays an insistent sound if the service could not start. */
    const val CH_FALLBACK = "reminder_fallback"
    /** Mirrored to smartwatches: not ongoing, high importance, vibration only. */
    const val CH_WATCH = "reminder_watch"
    const val ID = 1001
    const val WATCH_ID = 1002
    private const val TEST_ID = 1003

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_REMINDER, "Active reminder", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Shown while a stand-up reminder is waiting for confirmation"
                setSound(null, null)
                enableVibration(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_FALLBACK, "Reminder (backup alert)", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Used when the app is not allowed to play audio in the background"
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                )
                enableVibration(true)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_WATCH, "Smartwatch alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Copy of the reminder that your smartwatch can show and vibrate for"
                setSound(null, null) // the app already plays the sound
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
            }
        )
    }

    private val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    private fun openIntent(ctx: Context) = PendingIntent.getActivity(
        ctx, 0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        flags
    )

    private fun doneIntent(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 1, Intent(ctx, ActionReceiver::class.java).setAction(ActionReceiver.ACTION_CONFIRM), flags
    )

    private fun snoozeIntent(ctx: Context, minutes: Int) = PendingIntent.getBroadcast(
        ctx, 2,
        Intent(ctx, ActionReceiver::class.java).setAction(ActionReceiver.ACTION_SNOOZE)
            .putExtra(ActionReceiver.EXTRA_MINUTES, minutes),
        flags
    )

    /** Phone-side notification (foreground service / fallback). Kept off the watch when watch alerts are on. */
    fun build(ctx: Context, s: AppState, channel: String): NotificationCompat.Builder {
        val b = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat_standup)
            .setContentTitle("Time to stand up!")
            .setContentText("Move around for a minute, then tap Done.")
            .setContentIntent(openIntent(ctx))
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            // Ongoing notifications are usually not mirrored; the dedicated watch alert is used instead.
            .setLocalOnly(s.watchEnabled)
            .addAction(0, "Done", doneIntent(ctx))
        if (s.snoozeEnabled) b.addAction(0, "Snooze ${s.snoozeMinutes} min", snoozeIntent(ctx, s.snoozeMinutes))
        return b
    }

    /**
     * Separate, non-ongoing notification that phone-to-watch bridging (Wear OS, Galaxy Watch,
     * Garmin, Fitbit, ...) will forward. Done / Snooze work from the watch. Re-posting with
     * [reAlert] = true makes the watch vibrate again.
     */
    @SuppressLint("MissingPermission")
    fun postWatchAlert(ctx: Context, s: AppState, reAlert: Boolean) {
        if (!s.watchEnabled) return
        val b = NotificationCompat.Builder(ctx, CH_WATCH)
            .setSmallIcon(R.drawable.ic_stat_standup)
            .setContentTitle("Time to stand up!")
            .setContentText(if (s.speakEnabled && s.speakText.isNotBlank()) s.speakText else "Stand up and move, then tap Done.")
            .setContentIntent(openIntent(ctx))
            .setOngoing(false)
            .setAutoCancel(false)
            .setOnlyAlertOnce(!reAlert)
            .setLocalOnly(false)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(0, 400, 200, 400))
            .addAction(NotificationCompat.Action.Builder(R.drawable.ic_action_done, "Done", doneIntent(ctx)).build())
            .extend(NotificationCompat.WearableExtender().setDismissalId("standup_reminder"))
        if (s.snoozeEnabled) {
            b.addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_action_snooze, "Snooze ${s.snoozeMinutes} min", snoozeIntent(ctx, s.snoozeMinutes)
                ).build()
            )
        }
        try {
            NotificationManagerCompat.from(ctx).notify(WATCH_ID, b.build())
        } catch (_: SecurityException) {
        }
    }

    /** One-off check that notification mirroring to the watch works. */
    @SuppressLint("MissingPermission")
    fun postWatchTest(ctx: Context) {
        val n = NotificationCompat.Builder(ctx, CH_WATCH)
            .setSmallIcon(R.drawable.ic_stat_standup)
            .setContentTitle("StandUp watch test")
            .setContentText("If you can read this on your watch, reminders will reach it.")
            .setContentIntent(openIntent(ctx))
            .setAutoCancel(true)
            .setTimeoutAfter(60_000L)
            .setLocalOnly(false)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(0, 400, 200, 400))
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(TEST_ID, n)
        } catch (_: SecurityException) {
        }
    }

    @SuppressLint("MissingPermission")
    fun showFallback(ctx: Context, s: AppState) {
        val n = build(ctx, s, CH_FALLBACK).setOnlyAlertOnce(false).build()
        n.flags = n.flags or Notification.FLAG_INSISTENT // system repeats the sound until dismissed
        try {
            NotificationManagerCompat.from(ctx).notify(ID, n)
        } catch (_: SecurityException) {
        }
        postWatchAlert(ctx, s, reAlert = true)
    }

    /** Removes the phone notification and the watch copy (the dismissal syncs to the watch). */
    fun cancel(ctx: Context) {
        val nm = NotificationManagerCompat.from(ctx)
        nm.cancel(ID)
        nm.cancel(WATCH_ID)
    }
}
