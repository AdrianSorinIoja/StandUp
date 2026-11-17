package com.standup.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.standup.app.data.Store
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

@Composable
fun StatsScreen() {
    val s by Store.state.collectAsStateWithLifecycle()
    val max = (s.last7.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Statistics", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Completed today", style = MaterialTheme.typography.labelLarge)
                Text("${s.todayCount}", fontSize = 72.sp, fontWeight = FontWeight.Light)
                Text("${s.weekTotal} in the last 7 days", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Last 7 days", style = MaterialTheme.typography.titleMedium)
                Row(
                    Modifier.fillMaxWidth().height(200.dp).padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    s.last7.forEach { (date, n) ->
                        val barHeight by animateDpAsState((120f * n / max).dp, label = "bar")
                        Column(
                            Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            Text("$n", style = MaterialTheme.typography.labelMedium)
                            Box(
                                Modifier.width(24.dp)
                                    .height(maxOf(barHeight, 4.dp))
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (n > 0) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceContainerHighest
                                    )
                            )
                            Text(
                                date.dayOfWeek.getDisplayName(JTextStyle.SHORT, Locale.getDefault()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
