package com.linkedout.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material's window width classes, computed from the space the app actually
 * has, so split screen and a folded or unfolded device are handled alike.
 * Kept here rather than pulled from the window size class library: three
 * thresholds do not justify a dependency.
 */
enum class WidthClass {
    COMPACT, MEDIUM, EXPANDED;

    /** A phone held upright keeps the dock at the bottom, anything wider has it on the left. */
    val usesSideDock: Boolean get() = this != COMPACT

    companion object {
        fun of(width: Dp): WidthClass = when {
            width < 600.dp -> COMPACT
            width < 840.dp -> MEDIUM
            else -> EXPANDED
        }
    }
}

/**
 * Past this width a line of text gets too long to read and pictures too big
 * to take in. Wider windows centre the content in a column of this size.
 */
val ReadableWidth: Dp = 720.dp

/** Centres [content] in a column no wider than [ReadableWidth]. A no-op on phones. */
@Composable
fun Readable(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = ReadableWidth).fillMaxSize()) {
            content()
        }
    }
}
