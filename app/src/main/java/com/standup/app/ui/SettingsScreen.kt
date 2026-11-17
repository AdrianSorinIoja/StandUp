package com.standup.app.ui

import android.app.TimePickerDialog
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.standup.app.core.Notifications
import com.standup.app.core.ReminderAudio
import com.standup.app.core.ReminderController
import com.standup.app.data.SoundSource
import com.standup.app.data.Store
import com.standup.app.data.ThemeMode
import kotlin.math.roundToInt

private data class BuiltinSound(val title: String, val uri: String)

private fun loadBuiltinSounds(ctx: Context): List<BuiltinSound> {
    val out = mutableListOf(
        BuiltinSound("Default notification", RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION).toString()),
        BuiltinSound("Default alarm", RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM).toString()),
        BuiltinSound("Default ringtone", RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE).toString()),
    )
    runCatching {
        val rm = RingtoneManager(ctx).apply { setType(RingtoneManager.TYPE_NOTIFICATION or RingtoneManager.TYPE_ALARM) }
        rm.cursor.use { c ->
            while (c.moveToNext()) {
                val title = c.getString(RingtoneManager.TITLE_COLUMN_INDEX)
                val uri = ContentUris.withAppendedId(
                    Uri.parse(c.getString(RingtoneManager.URI_COLUMN_INDEX)),
                    c.getLong(RingtoneManager.ID_COLUMN_INDEX)
                ).toString()
                out += BuiltinSound(title, uri)
            }
        }
    }
    return out
}

private fun displayName(ctx: Context, uri: Uri): String =
    runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: (uri.lastPathSegment ?: "Audio file")

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onChange)
    }
}

private fun pickTime(ctx: Context, minutes: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(ctx, { _, h, m -> onPicked(h * 60 + m) }, minutes / 60, minutes % 60, DateFormat.is24HourFormat(ctx)).show()
}

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val app = ctx.applicationContext
    val s by Store.state.collectAsStateWithLifecycle()

    val audio = remember { ReminderAudio(app) }
    DisposableEffect(Unit) { onDispose { audio.release() } }

    var showPicker by remember { mutableStateOf(false) }
    var slider by remember(s.intervalMin) { mutableFloatStateOf(s.intervalMin.toFloat()) }
    var speakText by remember { mutableStateOf(s.speakText) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val name = displayName(ctx, uri)
            Store.edit {
                putString("customUri", uri.toString()); putString("customName", name)
                putString("soundSource", SoundSource.CUSTOM.name)
            }
        }
    }

    fun setInterval(min: Int) {
        Store.edit { putInt("interval", min) }
        ReminderController.onScheduleSettingsChanged(app)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        Section("Reminder interval") {
            Text("Every ${slider.roundToInt()} minutes", style = MaterialTheme.typography.titleLarge)
            Slider(
                value = slider, onValueChange = { slider = it }, valueRange = 1f..120f,
                onValueChangeFinished = { setInterval(slider.roundToInt()) }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 45, 60).forEach { m ->
                    FilterChip(selected = s.intervalMin == m, onClick = { setInterval(m) }, label = { Text("$m") })
                }
            }
        }

        Section("Active hours") {
            SwitchRow("Only remind me during these hours", s.activeHoursEnabled) {
                Store.edit { putBoolean("activeEnabled", it) }
                ReminderController.onScheduleSettingsChanged(app)
            }
            if (s.activeHoursEnabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = {
                        pickTime(ctx, s.activeStartMin) {
                            Store.edit { putInt("activeStart", it) }; ReminderController.onScheduleSettingsChanged(app)
                        }
                    }) { Text("From ${fmtMin(s.activeStartMin)}") }
                    OutlinedButton(onClick = {
                        pickTime(ctx, s.activeEndMin) {
                            Store.edit { putInt("activeEnd", it) }; ReminderController.onScheduleSettingsChanged(app)
                        }
                    }) { Text("To ${fmtMin(s.activeEndMin)}") }
                }
                Text(
                    "Overnight ranges (e.g. 22:00 to 06:00) work too.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Section("Sound") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(s.soundSource == SoundSource.BUILTIN, { Store.edit { putString("soundSource", SoundSource.BUILTIN.name) } }, { Text("Built-in") })
                FilterChip(s.soundSource == SoundSource.CUSTOM, { Store.edit { putString("soundSource", SoundSource.CUSTOM.name) } }, { Text("My file") })
                FilterChip(s.soundSource == SoundSource.SILENT, { Store.edit { putString("soundSource", SoundSource.SILENT.name) } }, { Text("Voice only") })
            }
            when (s.soundSource) {
                SoundSource.BUILTIN -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.builtinName.ifBlank { "Default notification" }, Modifier.weight(1f))
                    OutlinedButton(onClick = { showPicker = true }) { Text("Choose") }
                }
                SoundSource.CUSTOM -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.customName.ifBlank { "No file chosen (default sound plays)" }, Modifier.weight(1f))
                    OutlinedButton(onClick = { filePicker.launch(arrayOf("audio/*")) }) {
                        Icon(Icons.Filled.FolderOpen, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Browse")
                    }
                }
                SoundSource.SILENT -> Text(
                    "Only the spoken message is played. Enable it below.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { audio.playConfigured(Store.state.value) }) {
                    Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Preview")
                }
                OutlinedButton(onClick = { audio.stop() }) {
                    Icon(Icons.Filled.Stop, null); Spacer(Modifier.width(6.dp)); Text("Stop")
                }
            }
        }

        Section("Spoken message (text-to-speech)") {
            SwitchRow("Speak a message after the sound", s.speakEnabled) { Store.edit { putBoolean("speakEnabled", it) } }
            if (s.speakEnabled) {
                OutlinedTextField(
                    value = speakText,
                    onValueChange = { speakText = it; Store.edit { putString("speakText", it) } },
                    label = { Text("Message") }, modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Section("Snooze") {
            SwitchRow("Allow snooze", s.snoozeEnabled) { Store.edit { putBoolean("snoozeEnabled", it) } }
            if (s.snoozeEnabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10).forEach { m ->
                        FilterChip(s.snoozeMinutes == m, { Store.edit { putInt("snoozeMinutes", m) } }, { Text("$m minutes") })
                    }
                }
            }
        }

        Section("Smartwatch") {
            SwitchRow("Send reminders to my smartwatch", s.watchEnabled) { Store.edit { putBoolean("watchEnabled", it) } }
            if (s.watchEnabled) {
                SwitchRow("Buzz the watch on every 5-second repeat", s.watchRepeat) { Store.edit { putBoolean("watchRepeat", it) } }
                Button(onClick = { Notifications.postWatchTest(app) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Send test to watch")
                }
                OutlinedButton(
                    onClick = { Perms.safeStart(ctx, Perms.notificationSettingsIntent(ctx)) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Notification settings for StandUp") }
                Text(
                    "Your watch shows phone notifications through its companion app (Wear OS, Galaxy Wearable, " +
                        "Garmin Connect, Fitbit...). Make sure that app is allowed to show notifications from StandUp, " +
                        "and that the watch is not in Do Not Disturb or set to mute alerts while the phone is in use. " +
                        "Done and Snooze buttons work directly from the watch.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Section("Appearance") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        s.themeMode == mode, { Store.edit { putString("theme", mode.name) } },
                        { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }
        }

        Section("Background reliability") {
            SetupChecklist(showAll = true)
            Button(onClick = { ReminderController.testNow(app) }, modifier = Modifier.fillMaxWidth()) {
                Text("Test reminder in 5 seconds")
            }
            Text(
                "Tip: tap the test, then lock the phone to check that sound repeats every 5 seconds until you confirm.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showPicker) {
        val sounds = remember { loadBuiltinSounds(ctx) }
        val close = { showPicker = false; audio.stop() }
        AlertDialog(
            onDismissRequest = close,
            confirmButton = { TextButton(onClick = close) { Text("Done") } },
            title = { Text("Built-in sounds") },
            text = {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(sounds) { snd ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                Store.edit { putString("builtinUri", snd.uri); putString("builtinName", snd.title) }
                                audio.playSound(Uri.parse(snd.uri))
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = s.builtinUri == snd.uri, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(snd.title)
                        }
                    }
                }
            }
        )
    }
}
