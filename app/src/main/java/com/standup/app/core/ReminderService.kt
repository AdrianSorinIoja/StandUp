package com.standup.app.core

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import com.standup.app.data.Store

/**
 * Foreground service that plays the reminder when it fires and, if repeating is enabled, again every
 * N seconds (user setting) until the user confirms or snoozes. Settings are re-read every second, so
 * changing them while a reminder is ringing takes effect immediately.
 */
class ReminderService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var audio: ReminderAudio
    private var wakeLock: PowerManager.WakeLock? = null
    private var cycles = 0
    private var lastPlayAt = 0L

    private val ticker = object : Runnable {
        override fun run() {
            val s = Store.state.value
            if (!s.ringing) { stopSelf(); return }
            val now = SystemClock.elapsedRealtime()
            // Keep the CPU awake while a reminder is waiting so repeat timing stays accurate.
            wakeLock?.acquire(10_000L)
            val due = if (cycles == 0) true
            else s.repeatEnabled && now - lastPlayAt >= s.repeatSeconds * 1000L
            if (due) {
                audio.playConfigured(s)
                // First watch alert was sent by the controller; later cycles re-buzz the watch.
                if (cycles > 0 && s.watchRepeat) Notifications.postWatchAlert(this@ReminderService, s, reAlert = true)
                cycles++
                lastPlayAt = now
            }
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        audio = ReminderAudio(this)
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "standup:ringing").apply { setReferenceCounted(false) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val s = Store.state.value
        try {
            ServiceCompat.startForeground(
                this, Notifications.ID,
                Notifications.build(this, s, Notifications.CH_REMINDER).build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } catch (e: Exception) {
            Notifications.showFallback(this, s)
            stopSelf()
            return START_NOT_STICKY
        }
        handler.removeCallbacks(ticker)
        cycles = 0
        if (!s.ringing) { stopSelf(); return START_NOT_STICKY }
        handler.post(ticker)
        return START_STICKY // if killed while ringing, restart and keep going until confirmed
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        audio.release() // never leave audio playing
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object { private const val TICK_MS = 1_000L }
}
