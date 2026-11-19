package com.standup.app.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.standup.app.data.Store

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        Store.init(app)
        ReminderController.onAlarm(app)
    }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        Store.init(app)
        when (intent.action) {
            ACTION_CONFIRM -> ReminderController.stopAlert(app) // "Stop" button
            ACTION_SNOOZE -> ReminderController.snooze(
                app, intent.getIntExtra(EXTRA_MINUTES, Store.state.value.snoozeMinutes)
            )
        }
    }

    companion object {
        const val ACTION_CONFIRM = "com.standup.app.CONFIRM"
        const val ACTION_SNOOZE = "com.standup.app.SNOOZE"
        const val EXTRA_MINUTES = "minutes"
    }
}

/** Reboot, app update, clock/timezone change, exact-alarm permission change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        Store.init(app)
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED ->
                ReminderController.restoreAfterRestart(app)
            else -> ReminderController.refreshSchedule(app)
        }
    }
}
