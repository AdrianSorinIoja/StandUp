package com.standup.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/** Shows permission / battery status. With showAll=false only unresolved items are listed. */
@Composable
fun SetupChecklist(showAll: Boolean) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }

    val notif = remember(tick) { Perms.notificationsGranted(ctx) }
    val exact = remember(tick) { Perms.exactAlarmsGranted(ctx) }
    val battery = remember(tick) { Perms.batteryUnrestricted(ctx) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        tick++
        if (!granted) Perms.safeStart(ctx, Perms.notificationSettingsIntent(ctx))
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showAll || !notif) Item(
            Icons.Filled.Notifications, "Notifications", notif,
            "Needed to show the Done / Snooze buttons.", "Allow"
        ) {
            if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else Perms.safeStart(ctx, Perms.notificationSettingsIntent(ctx))
        }
        Perms.exactAlarmIntent(ctx)?.let { intent ->
            if (showAll || !exact) Item(
                Icons.Filled.Alarm, "Exact alarms", exact,
                "Without it, reminders can be late by several minutes while the phone is idle.", "Allow"
            ) { Perms.safeStart(ctx, intent) }
        }
        if (showAll || !battery) Item(
            Icons.Filled.BatteryAlert, "Battery optimisation", battery,
            "Set StandUp to Unrestricted / Not optimised so the phone does not delay or kill reminders.", "Open"
        ) { Perms.safeStart(ctx, Perms.batteryIntent()) }
    }
}

@Composable
private fun Item(icon: ImageVector, title: String, ok: Boolean, desc: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        if (ok) Icon(Icons.Filled.CheckCircle, "OK", tint = MaterialTheme.colorScheme.primary)
        else Button(onClick = onClick) { Text(action) }
    }
}
