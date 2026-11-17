package com.standup.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/**
 * Single source of truth, persisted in SharedPreferences so receivers, the service and the UI
 * (all in one process) see the same state. Reads are from memory, writes flush asynchronously.
 */
object Store {
    private lateinit var prefs: SharedPreferences
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("standup_prefs", Context.MODE_PRIVATE)
        _state.value = read()
    }

    fun edit(block: SharedPreferences.Editor.() -> Unit) {
        val e = prefs.edit()
        e.block()
        e.apply()
        _state.value = read()
    }

    fun incrementDone() {
        val key = doneKey(LocalDate.now())
        val n = prefs.getInt(key, 0) + 1
        edit { putInt(key, n) }
    }

    private fun doneKey(d: LocalDate) = "done_$d"

    private inline fun <reified T : Enum<T>> enumOf(s: String?, def: T): T =
        runCatching { enumValueOf<T>(s ?: "") }.getOrDefault(def)

    private fun read(): AppState {
        val d = AppState()
        val today = LocalDate.now()
        return AppState(
            intervalMin = prefs.getInt("interval", d.intervalMin),
            running = prefs.getBoolean("running", false),
            ringing = prefs.getBoolean("ringing", false),
            nextTriggerAt = prefs.getLong("next", 0L),
            pausedRemainingMs = prefs.getLong("pausedRemaining", 0L),
            forceNext = prefs.getBoolean("force", false),
            soundSource = enumOf(prefs.getString("soundSource", null), SoundSource.BUILTIN),
            builtinUri = prefs.getString("builtinUri", "") ?: "",
            builtinName = prefs.getString("builtinName", "") ?: "",
            customUri = prefs.getString("customUri", "") ?: "",
            customName = prefs.getString("customName", "") ?: "",
            speakEnabled = prefs.getBoolean("speakEnabled", false),
            speakText = prefs.getString("speakText", d.speakText) ?: d.speakText,
            watchEnabled = prefs.getBoolean("watchEnabled", true),
            watchRepeat = prefs.getBoolean("watchRepeat", true),
            snoozeEnabled = prefs.getBoolean("snoozeEnabled", true),
            snoozeMinutes = prefs.getInt("snoozeMinutes", 5),
            activeHoursEnabled = prefs.getBoolean("activeEnabled", false),
            activeStartMin = prefs.getInt("activeStart", d.activeStartMin),
            activeEndMin = prefs.getInt("activeEnd", d.activeEndMin),
            themeMode = enumOf(prefs.getString("theme", null), ThemeMode.SYSTEM),
            last7 = (6 downTo 0).map { back ->
                val day = today.minusDays(back.toLong())
                day to prefs.getInt(doneKey(day), 0)
            },
        )
    }
}
