package com.standup.app.core

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.standup.app.data.Store

/** All reminder state transitions live here so UI, notification buttons and receivers behave identically. */
object ReminderController {
    const val ACTION_ALARM = "com.standup.app.ALARM"
    private const val TAG = "StandUp"

    fun start(ctx: Context) {
        val s = Store.state.value
        val now = System.currentTimeMillis()
        val next = if (s.pausedRemainingMs > 0) now + s.pausedRemainingMs else Schedule.computeNext(s, now)
        Store.edit {
            putBoolean("running", true); putBoolean("ringing", false)
            putLong("next", next); putLong("pausedRemaining", 0L)
        }
        Scheduler.schedule(ctx, next)
    }

    fun pause(ctx: Context) {
        val s = Store.state.value
        val remaining = if (s.ringing) 0L else (s.nextTriggerAt - System.currentTimeMillis()).coerceAtLeast(1000L)
        Store.edit { putBoolean("running", false); putBoolean("ringing", false); putLong("pausedRemaining", remaining) }
        Scheduler.cancel(ctx)
        silence(ctx)
    }

    /** Alarm fired. */
    fun onAlarm(ctx: Context) {
        val s = Store.state.value
        if (!s.running || s.ringing) return
        val now = System.currentTimeMillis()
        if (!s.forceNext && !Schedule.isActive(s, now)) { // late delivery outside active hours
            scheduleFromNow(ctx); return
        }
        Store.edit { putBoolean("ringing", true); putBoolean("force", false) }
        Notifications.postWatchAlert(ctx, Store.state.value, reAlert = true)
        try {
            ContextCompat.startForegroundService(ctx, Intent(ctx, ReminderService::class.java))
        } catch (e: Exception) {
            Log.w(TAG, "Could not start foreground service, using notification fallback", e)
            Notifications.showFallback(ctx, Store.state.value)
        }
    }

    /** User confirmed (app button or notification). Stops audio first, then restarts the interval. */
    fun confirm(ctx: Context) {
        val s = Store.state.value
        Store.edit { putBoolean("ringing", false) } // service ticker sees this and stops
        silence(ctx)
        if (s.ringing) {
            Store.incrementDone()
            if (s.running) scheduleFromNow(ctx)
        }
    }

    fun snooze(ctx: Context, minutes: Int) {
        val s = Store.state.value
        Store.edit { putBoolean("ringing", false) }
        silence(ctx)
        if (s.running) {
            val next = System.currentTimeMillis() + minutes * 60_000L
            Store.edit { putLong("next", next) }
            Scheduler.schedule(ctx, next)
        }
    }

    fun scheduleFromNow(ctx: Context) {
        val next = Schedule.computeNext(Store.state.value, System.currentTimeMillis())
        Store.edit { putLong("next", next) }
        Scheduler.schedule(ctx, next)
    }

    /** Interval or active hours changed. */
    fun onScheduleSettingsChanged(ctx: Context) {
        val s = Store.state.value
        if (s.running && !s.ringing) scheduleFromNow(ctx)
    }

    /** Re-arm the alarm (app resume, time change, exact-alarm permission change). */
    fun refreshSchedule(ctx: Context) {
        val s = Store.state.value
        if (!s.running || s.ringing) return
        var next = s.nextTriggerAt
        val now = System.currentTimeMillis()
        if (next <= now) { // alarm was lost (reboot, update, force-stop): fire shortly
            next = now + 1000L
            Store.edit { putLong("next", next) }
        }
        Scheduler.schedule(ctx, next)
    }

    /** After reboot / app update. A foreground service cannot be started from BOOT_COMPLETED on Android 15. */
    fun restoreAfterRestart(ctx: Context) {
        val s = Store.state.value
        if (s.running && s.ringing) Notifications.showFallback(ctx, s) else refreshSchedule(ctx)
    }

    fun testNow(ctx: Context) {
        val next = System.currentTimeMillis() + 5000L
        Store.edit {
            putBoolean("running", true); putBoolean("ringing", false)
            putBoolean("force", true); putLong("next", next); putLong("pausedRemaining", 0L)
        }
        Scheduler.schedule(ctx, next)
    }

    /** Stop service (audio) and remove the notification. */
    private fun silence(ctx: Context) {
        ctx.stopService(Intent(ctx, ReminderService::class.java))
        Notifications.cancel(ctx)
    }
}
