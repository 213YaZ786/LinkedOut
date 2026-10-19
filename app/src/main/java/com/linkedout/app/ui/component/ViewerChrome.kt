package com.linkedout.app.ui.component

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Whether a media viewer shows its chrome: the close and action buttons, the
 * player controls and the system bars. One state per viewer, provided by its
 * frame; a tap on the media flips it, so a single tap leaves the bare media
 * and the next brings everything back, as Google Photos, Apple Photos,
 * LineageOS's Glimpse and ReFra do. Null outside a viewer: a player then
 * keeps its own controls state.
 *
 * Where it comes from: ReFra (Apache-2.0, https://github.com/IacobIonut01/ReFra
 * at ba329cb, app/feature/viewer) and Glimpse (Apache-2.0,
 * https://github.com/LineageOS/android_packages_apps_Glimpse), whose viewers
 * hold one visibility flag that the tap flips and the bars follow.
 */
val LocalViewerChrome = compositionLocalOf<MutableState<Boolean>?> { null }

/**
 * Hides the system bars while [shown] is false and brings them back when it is
 * true or when the viewer leaves. Immersive mode as Android's guide gives it
 * (https://developer.android.com/develop/ui/views/layout/immersive):
 * WindowInsetsControllerCompat.hide(systemBars) with
 * BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE, so an edge swipe shows them for a
 * moment. Works on the window the composable is in: inside a Compose Dialog
 * that is the dialog's window, else the activity's. Gallery's ViewerScreen
 * does the same by its own DisposableEffect(chrome).
 */
@Composable
fun ViewerChromeEffect(shown: Boolean) {
    val view = LocalView.current
    val context = LocalContext.current
    DisposableEffect(shown, view) {
        val window = windowOf(view, context)
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (shown) {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

/**
 * The window a composable is in: a Compose Dialog's own, else the activity's. The lookup ViewerChromeEffect already had,
 * moved here so GlassVideo's brightness drag shares it. Source: androidx.compose.ui.window.DialogWindowProvider
 * (https://developer.android.com/reference/kotlin/androidx/compose/ui/window/DialogWindowProvider), whose window is the
 * dialog's, and the Context unwrapping of androidx.activity's ComponentActivity lookups
 * (https://github.com/androidx/androidx/blob/androidx-main/activity/activity-compose/src/main/java/androidx/activity/compose/LocalActivity.kt).
 */
internal fun windowOf(view: View, context: Context): Window? =
    (view.parent as? DialogWindowProvider)?.window
        ?: (view as? DialogWindowProvider)?.window
        ?: context.findActivity()?.window

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
