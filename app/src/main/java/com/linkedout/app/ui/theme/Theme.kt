package com.linkedout.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
fun LinkedOutTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    pureBlack: Boolean = false,
    textScale: Float = 1f,
    display: DisplayPrefs = DisplayPrefs(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colors = when {
        // minSdk is 31, so dynamic colour is always available. The flag exists
        // so the user can turn it off in Settings.
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> LinkedOutDarkColors
        else -> LinkedOutLightColors
    }.let { scheme ->
        if (darkTheme && pureBlack) {
            scheme.copy(
                background = Color.Black,
                surface = Color.Black,
                surfaceContainerLowest = Color.Black
            )
        } else {
            scheme
        }
    }

    val typography = remember(textScale) { LinkedOutTypography.scaled(textScale) }

    MaterialTheme(colorScheme = colors, typography = typography) {
        CompositionLocalProvider(LocalDisplayPrefs provides display, content = content)
    }
}
