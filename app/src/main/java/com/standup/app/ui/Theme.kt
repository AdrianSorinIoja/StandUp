package com.standup.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.standup.app.data.ThemeMode

private val Light = lightColorScheme(
    primary = Color(0xFF006C4C), onPrimary = Color.White,
    primaryContainer = Color(0xFF89F8C7), onPrimaryContainer = Color(0xFF002114),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF6CDBAC), onPrimary = Color(0xFF003826),
    primaryContainer = Color(0xFF005138), onPrimaryContainer = Color(0xFF89F8C7),
)

@Composable
fun resolveDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun StandUpTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = resolveDark(mode)
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
