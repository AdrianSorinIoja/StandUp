package com.standup.app.core

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.standup.app.data.Store

/**
 * All reminder state transitions live here so UI, notification buttons and receivers behave identically.
 *
 * Flow: when the alarm fires, the next interval is scheduled immediately and the alert sequence
 * (sound / speech, optionally repeated) plays on its own. Nothing has to be confirmed.
 */
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
        val remaining = (s.nextTriggerAt - System.currentTimeMillis()).coerceAtLeast(1000L)
        Store.edit { putBoolean("running", false); putBoolean("ringing", false); putLong("pausedRemaining", remaining) }
        Scheduler.cancel(ctx)
        silence(ctx)
    }

    /** Alarm fired: restart the countdown right away, then play the alert. */
    fun onAlarm(ctx: Context) {
        val s = Store.state.value
        if (!s.running) return
        val now = System.currentTimeMillis()
        if (!s.forceNext && !Schedule.isActive(s, now)) { // late delivery outside active hours
            scheduleFromNow(ctx); return
        }
        Store.edit { putBoolean("force", false) }
        scheduleFromNow(ctx) // the interval restarts automatically, no confirmation needed
        Store.edit { putBoolean("ringing", true) }
        Notifications.postWatchAlert(ctx, Store.state.value, reAlert = true)
        try {
            // If a previous alert is still repeating, the service simply restarts the sequence.
            ContextCompat.startForegroundService(ctx, Intent(ctx, ReminderService::class.java))
        } catch (e: Exception) {
            Log.w(TAG, "Could not start foreground service, using notification fallback", e)
            Store.edit { putBoolean("ringing", false) }
            Notifications.showFallback(ctx, Store.state.value)
        }
    }

    /** Called by the service when the alert sequence has finished. */
    fun alertFinished() {
        if (Store.state.value.ringing) Store.edit { putBoolean("ringing", false) }
    }

    /** Optional: stop the current alert early (notification / app button). The countdown is not affected. */
    fun stopAlert(ctx: Context) {
        Store.edit { putBoolean("ringing", false) }
        silence(ctx)
    }

    /** Optional: stop the alert and push the next reminder back by [minutes]. */
    fun snooze(ctx: Context, minutes: Int) {
        Store.edit { putBoolean("ringing", false) }
        silence(ctx)
        if (Store.state.value.running) {
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
        if (Store.state.value.running) scheduleFromNow(ctx)
    }

    /** Re-arm the alarm (app resume, time change, exact-alarm permission change). */
    fun refreshSchedule(ctx: Context) {
        val s = Store.state.value
        if (!s.running) return
        var next = s.nextTriggerAt
        val now = System.currentTimeMillis()
        if (next <= now) { // alarm was lost (reboot, update, force-stop): fire shortly
            next = now + 1000L
            Store.edit { putLong("next", next) }
        }
        Scheduler.schedule(ctx, next)
    }

    /** After reboot / app update: nothing can still be playing, so clear the flag and re-arm the alarm. */
    fun restoreAfterRestart(ctx: Context) {
        if (Store.state.value.ringing) Store.edit { putBoolean("ringing", false) }
        refreshSchedule(ctx)
    }

    fun testNow(ctx: Context) {
        val next = System.currentTimeMillis() + 5000L
        Store.edit {
            putBoolean("running", true); putBoolean("ringing", false)
            putBoolean("force", true); putLong("next", next); putLong("pausedRemaining", 0L)
        }
        Scheduler.schedule(ctx, next)
    }

    /** Stop service (audio) and remove the notifications. */
    private fun silence(ctx: Context) {
        ctx.stopService(Intent(ctx, ReminderService::class.java))
        Notifications.cancel(ctx)
    }
}
