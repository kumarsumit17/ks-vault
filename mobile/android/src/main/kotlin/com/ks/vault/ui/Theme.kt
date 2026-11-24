package com.ks.vault.ui

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

private val Light = lightColorScheme(
    primary = Color(0xFF5B45C2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6DEFF),
    onPrimaryContainer = Color(0xFF1B0063),
    secondary = Color(0xFF9C3E78),
    secondaryContainer = Color(0xFFFFD8EA),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFCABEFF),
    onPrimary = Color(0xFF2E0C9A),
    primaryContainer = Color(0xFF4329A9),
    onPrimaryContainer = Color(0xFFE6DEFF),
    secondary = Color(0xFFFFAFD6),
    secondaryContainer = Color(0xFF7E275F),
)

/** Material 3, with the phone's own colours on Android 12 and later. */
@Composable
fun VaultTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
