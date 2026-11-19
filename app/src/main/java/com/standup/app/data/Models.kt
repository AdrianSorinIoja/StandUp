package com.standup.app.data

import android.media.AudioAttributes
import android.media.AudioManager

enum class SoundSource { BUILTIN, CUSTOM, SILENT }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Which Android volume channel the reminder uses. Alarm plays even in silent mode; the others obey it. */
enum class AudioChannel(val label: String, val stream: Int, val usage: Int) {
    ALARM("Alarm", AudioManager.STREAM_ALARM, AudioAttributes.USAGE_ALARM),
    MEDIA("Media", AudioManager.STREAM_MUSIC, AudioAttributes.USAGE_MEDIA),
    NOTIFICATION("Notification", AudioManager.STREAM_NOTIFICATION, AudioAttributes.USAGE_NOTIFICATION),
}

data class AppState(
    val intervalMin: Int = 30,
    val running: Boolean = false,
    /** True while an alert sequence (sound/speech repeats) is playing. */
    val ringing: Boolean = false,
    val nextTriggerAt: Long = 0L,
    val pausedRemainingMs: Long = 0L,
    /** Set by the "test reminder" button so it ignores active hours once. */
    val forceNext: Boolean = false,
    val soundSource: SoundSource = SoundSource.BUILTIN,
    val builtinUri: String = "",
    val builtinName: String = "",
    val customUri: String = "",
    val customName: String = "",
    val audioChannel: AudioChannel = AudioChannel.ALARM,
    val speakEnabled: Boolean = false,
    val speakText: String = "Time to stand up and move!",
    /** Repeat the alert: play [repeatCount] times in total, [repeatSeconds] apart. If false it plays once. */
    val repeatEnabled: Boolean = true,
    val repeatSeconds: Int = 5,
    val repeatCount: Int = 3,
    val watchEnabled: Boolean = true,
    val watchRepeat: Boolean = true,
    val snoozeEnabled: Boolean = true,
    val snoozeMinutes: Int = 5,
    val activeHoursEnabled: Boolean = false,
    val activeStartMin: Int = 9 * 60,
    val activeEndMin: Int = 17 * 60,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)
