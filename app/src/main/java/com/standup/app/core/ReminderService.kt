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
 * Foreground service that plays the alert when a reminder fires. With repeating enabled it plays
 * [repeatCount] times in total, [repeatSeconds] apart, then stops by itself. Nothing needs confirming.
 * Settings are re-read every second, so changes apply immediately.
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
            val maxPlays = if (s.repeatEnabled) s.repeatCount else 1
            val now = SystemClock.elapsedRealtime()
            wakeLock?.acquire(10_000L) // keeps repeat timing accurate while the alert is active
            if (cycles == 0 || (cycles < maxPlays && now - lastPlayAt >= s.repeatSeconds * 1000L)) {
                audio.playConfigured(s)
                if (cycles > 0 && s.watchRepeat) Notifications.postWatchAlert(this@ReminderService, s, reAlert = true)
                cycles++
                lastPlayAt = now
            } else if (cycles >= maxPlays && now - lastPlayAt >= LAST_SOUND_GRACE_MS) {
                stopSelf() // finished: let the last sound/speech play out, then end
                return
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
            ReminderController.alertFinished()
            Notifications.showFallback(this, s)
            stopSelf()
            return START_NOT_STICKY
        }
        handler.removeCallbacks(ticker)
        audio.stop()
        cycles = 0 // a new reminder restarts the sequence
        if (!s.ringing) { stopSelf(); return START_NOT_STICKY }
        handler.post(ticker)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        audio.release() // never leave audio playing
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        ReminderController.alertFinished()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TICK_MS = 1_000L
        /** Time given to the final sound / spoken message to finish before the service ends. */
        private const val LAST_SOUND_GRACE_MS = 8_000L
    }
}
