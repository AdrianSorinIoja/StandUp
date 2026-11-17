package com.standup.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.standup.app.core.ReminderController
import com.standup.app.data.Store
import kotlinx.coroutines.delay

fun formatDuration(ms: Long): String {
    val total = (ms + 999) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

fun fmtMin(m: Int) = "%02d:%02d".format(m / 60, m % 60)

@Composable
fun DashboardScreen() {
    val ctx = LocalContext.current.applicationContext
    val s by Store.state.collectAsStateWithLifecycle()
    val now by produceState(System.currentTimeMillis(), s.running) {
        while (s.running) { value = System.currentTimeMillis(); delay(250) }
    }

    val totalMs = s.intervalMin * 60_000L
    val remaining = when {
        s.ringing -> 0L
        s.running -> (s.nextTriggerAt - now).coerceAtLeast(0L)
        s.pausedRemainingMs > 0 -> s.pausedRemainingMs
        else -> totalMs
    }
    val target = (remaining.toFloat() / maxOf(totalMs, remaining).toFloat()).coerceIn(0f, 1f)
    val progress by animateFloatAsState(target, tween(300, easing = LinearEasing), label = "progress")

    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        1f, 1.04f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "pulseScale"
    )

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("StandUp", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        Box(
            Modifier.size(300.dp).scale(if (s.ringing) pulse else 1f),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { if (s.ringing) 1f else progress },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 14.dp,
                strokeCap = StrokeCap.Round,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                color = if (s.ringing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    when {
                        s.ringing -> "Time to move!"
                        s.running -> "Next stand-up in"
                        s.pausedRemainingMs > 0 -> "Paused"
                        else -> "Ready"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!s.ringing) {
                    Text(formatDuration(remaining), fontSize = 64.sp, fontWeight = FontWeight.Light)
                }
            }
        }

        AnimatedContent(
            targetState = s.ringing,
            transitionSpec = { (fadeIn(tween(250)) + slideInVertically { it / 4 }) togetherWith (fadeOut(tween(150)) + slideOutVertically { -it / 4 }) },
            label = "controls"
        ) { ringing ->
            if (ringing) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { ReminderController.confirm(ctx) },
                        modifier = Modifier.fillMaxWidth().height(72.dp)
                    ) {
                        Icon(Icons.Filled.Check, null)
                        Spacer(Modifier.width(8.dp))
                        Text("I stood up!", fontSize = 20.sp)
                    }
                    if (s.snoozeEnabled) {
                        OutlinedButton(
                            onClick = { ReminderController.snooze(ctx, s.snoozeMinutes) },
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Icon(Icons.Filled.Snooze, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Snooze ${s.snoozeMinutes} min")
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { ReminderController.start(ctx) },
                        enabled = !s.running,
                        modifier = Modifier.weight(1f).height(56.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (s.pausedRemainingMs > 0) "Resume" else "Start")
                    }
                    FilledTonalButton(
                        onClick = { ReminderController.pause(ctx) },
                        enabled = s.running,
                        modifier = Modifier.weight(1f).height(56.dp)
                    ) {
                        Icon(Icons.Filled.Pause, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Pause")
                    }
                }
            }
        }

        Card(
            Modifier.fillMaxWidth().animateContentSize(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Today", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text("${s.todayCount} stand-ups completed", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Every ${s.intervalMin} min" +
                        if (s.activeHoursEnabled) " · active ${fmtMin(s.activeStartMin)}–${fmtMin(s.activeEndMin)}" else " · all day",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SetupChecklist(showAll = false)
    }
}
