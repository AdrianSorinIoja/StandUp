package com.standup.app.core

import com.standup.app.data.AppState
import java.util.Calendar

/** Pure time logic for active hours and next-trigger computation. */
object Schedule {
    fun minuteOfDay(millis: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    fun isActive(s: AppState, millis: Long): Boolean {
        if (!s.activeHoursEnabled || s.activeStartMin == s.activeEndMin) return true
        val m = minuteOfDay(millis)
        return if (s.activeStartMin < s.activeEndMin) {
            m >= s.activeStartMin && m < s.activeEndMin
        } else { // overnight window, e.g. 22:00 -> 06:00
            m >= s.activeStartMin || m < s.activeEndMin
        }
    }

    /** Earliest moment >= [from] inside the active window. */
    fun nextActiveStart(s: AppState, from: Long): Long {
        if (isActive(s, from)) return from
        val c = Calendar.getInstance().apply {
            timeInMillis = from
            set(Calendar.HOUR_OF_DAY, s.activeStartMin / 60)
            set(Calendar.MINUTE, s.activeStartMin % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= from) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    /** Next reminder time after [now]: now + interval, pushed to the next active window if needed. */
    fun computeNext(s: AppState, now: Long): Long {
        val interval = s.intervalMin * 60_000L
        val candidate = now + interval
        if (isActive(s, candidate)) return candidate
        val start = nextActiveStart(s, candidate)
        val afterStart = start + interval
        return if (isActive(s, afterStart)) afterStart else start
    }
}
