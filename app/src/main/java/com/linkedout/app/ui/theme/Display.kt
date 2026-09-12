package com.linkedout.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.isSpecified

/**
 * Display choices that shape how posts are drawn, provided once at the top
 * so no screen has to pass them down. Text size is not here, it goes
 * through the typography itself, see [scaled].
 */
data class DisplayPrefs(
    /** Tighter post cards, smaller avatars, shorter media. More posts per screen. */
    val compact: Boolean = false,
    /** Rounded squares instead of circles, as Nitter's own option does. */
    val squareAvatars: Boolean = false
)

val LocalDisplayPrefs = staticCompositionLocalOf { DisplayPrefs() }

/** Text size steps offered in Settings. Applied on top of Android's own font size. */
val TEXT_SCALES = listOf(0.9f, 1f, 1.15f, 1.3f)

fun textScaleLabel(scale: Float): String = when (scale) {
    0.9f -> "Small"
    1f -> "Default"
    1.15f -> "Large"
    1.3f -> "Largest"
    else -> "${(scale * 100).toInt()}%"
}

/**
 * Every style scaled by [factor], line height included so paragraphs keep
 * their rhythm. Scaling the typography rather than the density leaves icons,
 * padding and touch targets alone, only words grow.
 */
fun Typography.scaled(factor: Float): Typography {
    if (factor == 1f) return this
    fun TextStyle.s() = copy(
        fontSize = if (fontSize.isSpecified) fontSize * factor else fontSize,
        lineHeight = if (lineHeight.isSpecified) lineHeight * factor else lineHeight
    )
    return copy(
        displayLarge = displayLarge.s(),
        displayMedium = displayMedium.s(),
        displaySmall = displaySmall.s(),
        headlineLarge = headlineLarge.s(),
        headlineMedium = headlineMedium.s(),
        headlineSmall = headlineSmall.s(),
        titleLarge = titleLarge.s(),
        titleMedium = titleMedium.s(),
        titleSmall = titleSmall.s(),
        bodyLarge = bodyLarge.s(),
        bodyMedium = bodyMedium.s(),
        bodySmall = bodySmall.s(),
        labelLarge = labelLarge.s(),
        labelMedium = labelMedium.s(),
        labelSmall = labelSmall.s()
    )
}
