package com.standup.app.data

import java.time.LocalDate

enum class SoundSource { BUILTIN, CUSTOM, SILENT }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppState(
    val intervalMin: Int = 30,
    val running: Boolean = false,
    /** True from the moment a reminder fires until the user confirms or snoozes. */
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
    val speakEnabled: Boolean = false,
    val speakText: String = "Time to stand up and move!",
    /** Keep repeating the alert until confirmed. If false, it plays once and the notification stays. */
    val repeatEnabled: Boolean = true,
    val repeatSeconds: Int = 5,
    val watchEnabled: Boolean = true,
    val watchRepeat: Boolean = true,
    val snoozeEnabled: Boolean = true,
    val snoozeMinutes: Int = 5,
    val activeHoursEnabled: Boolean = false,
    val activeStartMin: Int = 9 * 60,
    val activeEndMin: Int = 17 * 60,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Last 7 days, oldest first, today last. */
    val last7: List<Pair<LocalDate, Int>> = emptyList(),
) {
    val todayCount: Int get() = last7.lastOrNull()?.second ?: 0
    val weekTotal: Int get() = last7.sumOf { it.second }
}
