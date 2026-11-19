package com.standup.app

import com.standup.app.core.Schedule
import com.standup.app.data.AppState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ScheduleTest {
    private fun at(day: Int, h: Int, m: Int): Long = Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, day, h, m, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private val s = AppState(intervalMin = 30, activeHoursEnabled = true, activeStartMin = 9 * 60, activeEndMin = 17 * 60)

    @Test fun insideWindowAddsInterval() = assertEquals(at(16, 10, 30), Schedule.computeNext(s, at(16, 10, 0)))

    @Test fun afterWindowRollsToNextMorning() = assertEquals(at(17, 9, 30), Schedule.computeNext(s, at(16, 16, 45)))

    @Test fun overnightWindow() {
        val o = s.copy(activeStartMin = 22 * 60, activeEndMin = 6 * 60)
        assertTrue(Schedule.isActive(o, at(16, 23, 0)))
        assertTrue(Schedule.isActive(o, at(16, 5, 59)))
        assertFalse(Schedule.isActive(o, at(16, 12, 0)))
    }

    @Test fun disabledMeansAlwaysActive() = assertTrue(Schedule.isActive(s.copy(activeHoursEnabled = false), at(16, 3, 0)))
}
