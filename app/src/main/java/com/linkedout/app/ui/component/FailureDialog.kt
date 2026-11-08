package com.linkedout.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.linkedout.app.core.common.AppError
import com.linkedout.app.core.common.Blame

/**
 * One message, centred over the screen blurred behind it, naming where the trouble comes from. No
 * profile is listed: with many follows a list helps nobody, and nothing can be done per profile.
 * The dialog is ZoneAlertDialog's (Material's BasicAlertDialog, `onDismissRequest`, `content`:
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/AlertDialog.kt#BasicAlertDialog ),
 * its pane in the post card's shape. [site] is the network the app reads, named in the titles.
 * not ported: BasicAlertDialog (ZoneAlertDialog, the glass kit's dialog, is used as it is)
 * not ported: AlertDialogContent (ZoneAlertDialog lays the icon, title, text and buttons out; only its slots are filled here)
 */
@Composable
fun FailureDialog(
    failed: List<AppError>,
    followed: Int,
    silentDownloads: Boolean,
    site: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val (title, text) = failureMessage(failed, followed, site) ?: (SILENT_DOWNLOADS_TITLE to SILENT_DOWNLOADS_TEXT)
    ZoneAlertDialog(
        onDismissRequest = onDismiss,
        shape = ZoneShape,
        icon = if (failed.isEmpty()) null else ({ FailureIcon() }),
        title = { Text(title) },
        text = { FailureContent(text, silentDownloads && failed.isNotEmpty()) },
        confirmButton = {
            if (failed.isEmpty()) {
                QuietButton(onClick = onDismiss) { Text("Close") }
            } else {
                BoldButton(onClick = onRetry, filled = true) { Text("Try again") }
            }
        },
        dismissButton = {
            if (failed.isNotEmpty()) QuietButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

// The icon slot of Material's AlertDialog (icon, `iconContentColor`):
// https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/AlertDialog.kt#AlertDialogContent
@Composable
private fun FailureIcon() {
    Icon(
        FailureIcons.Warning,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error,
        modifier = Modifier.size(24.dp)
    )
}

// The text slot of Material's AlertDialog (`text`, `textContentColor`; ZoneAlertContent centres it):
// https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/AlertDialog.kt#AlertDialogContent
@Composable
private fun FailureContent(text: String, silentDownloads: Boolean) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text)
        if (silentDownloads) Text(SILENT_DOWNLOADS_TEXT)
    }
}

internal const val SILENT_DOWNLOADS_TITLE = "Downloads have no progress bar"
internal const val SILENT_DOWNLOADS_TEXT =
    "Media is being saved for offline reading, but Android is not letting " +
        "LinkedOut show the progress. The files still arrive. Allow " +
        "notifications for LinkedOut in Android to see how far they are."

// Bluesky's PostFeedErrorMessage reads an error, picks a KnownError, then maps each KnownError to
// its message: https://github.com/bluesky-social/social-app/blob/main/src/view/com/posts/PostFeedErrorMessage.tsx#detectKnownError
// (MIT). Here the KnownError is the origin (AppError.blame) of one failed profile.
// Not ported: Block (no counterpart), FeedgenErrorMessage and the feedgen parsing,
// safeParseFeedgenUri, the remove feed prompt, onViewProfile, PostFeedErrorMessage's ErrorMessage.
enum class KnownError {
    Device,
    Network,
    Host,
    Upstream,
    App
}

internal fun detectKnownError(error: AppError?): KnownError? {
    if (error == null) {
        return null
    }
    // check the blame, as detectKnownError checks the name and the status code
    if (error.blame == Blame.DEVICE) {
        return KnownError.Device
    }
    if (error.blame == Blame.NETWORK) {
        return KnownError.Network
    }
    if (error.blame == Blame.HOST) {
        return KnownError.Host
    }
    if (error.blame == Blame.UPSTREAM) {
        return KnownError.Upstream
    }
    return KnownError.App
}

// The KnownError to message map of FeedgenErrorMessage (`msg`, one message per KnownError, indexed by
// the knownError), here a title and a text; [rawError] is the sentence counting the profiles, [site] the network named:
// https://github.com/bluesky-social/social-app/blob/main/src/view/com/posts/PostFeedErrorMessage.tsx#detectKnownError
internal fun FeedgenErrorMessage(knownError: KnownError, rawError: String, site: String): Pair<String, String> {
    val msg = mapOf(
        KnownError.Device to ("You are offline" to
            "Saved posts stay readable. New ones load when you are back online."),
        KnownError.Network to ("Your connection does not reach $site" to
            "$rawError Their saved posts stay readable."),
        KnownError.Host to ("$site is holding back guests" to
            "$rawError Their saved posts stay readable. Try again in a few minutes."),
        KnownError.Upstream to ("Some profiles are not public" to
            "$rawError Their saved posts stay readable."),
        KnownError.App to ("LinkedOut could not read some pages" to
            "$rawError Their saved posts stay readable.")
    )[knownError]
    return msg!!
}

// PostFeedErrorMessage's detectKnownError over every error, the knownError shared by most winning,
// then its msg ([feedDesc] is how many profiles are followed):
// https://github.com/bluesky-social/social-app/blob/main/src/view/com/posts/PostFeedErrorMessage.tsx#detectKnownError
internal fun failureMessage(error: List<AppError>, feedDesc: Int, site: String): Pair<String, String>? {
    val knownError = KnownError.entries.maxByOrNull { kind -> error.count { detectKnownError(it) == kind } }
    if (error.isEmpty() || knownError == null) {
        return null
    }
    val msg = if (knownError == KnownError.Upstream) {
        if (error.size == 1) "shows nothing to a guest." else "show nothing to a guest."
    } else {
        if (error.size == 1) "was not updated." else "were not updated."
    }
    val rawError = if (feedDesc >= error.size) {
        "${error.size} of $feedDesc profiles $msg"
    } else if (error.size == 1) {
        "1 profile $msg"
    } else {
        "${error.size} profiles $msg"
    }
    return FeedgenErrorMessage(knownError, rawError, site)
}

// The failure mark. A triangle reads as "attention" at 22 dp, a circle does not. Material Symbols Rounded, fill 1
// (width and height 24dp, viewportWidth and viewportHeight 960, path fillColor white): rounded corners.
// https://github.com/google/material-design-icons/blob/master/symbols/android/warning/materialsymbolsrounded/warning_fill1_24px.xml
private object FailureIcons {
    val Warning: ImageVector by lazy {
        val pathData = "M109,840Q98,840 89,834.5Q80,829 75,820Q70,811 69.5,800.5Q69,790 75,780L445,140Q451,130 460.5,125" +
            "Q470,120 480,120Q490,120 499.5,125Q509,130 515,140L885,780Q891,790 890.5,800.5Q890,811 885,820" +
            "Q880,829 871,834.5Q862,840 851,840L109,840ZM480,720Q497,720 508.5,708.5Q520,697 520,680Q520,663 508.5,651.5" +
            "Q497,640 480,640Q463,640 451.5,651.5Q440,663 440,680Q440,697 451.5,708.5Q463,720 480,720Z" +
            "M480,600Q497,600 508.5,588.5Q520,577 520,560L520,440Q520,423 508.5,411.5Q497,400 480,400Q463,400 451.5,411.5" +
            "Q440,423 440,440L440,560Q440,577 451.5,588.5Q463,600 480,600Z"
        ImageVector.Builder("Warning", 24.dp, 24.dp, viewportWidth = 960f, viewportHeight = 960f)
            .addPath(pathData = PathParser().parsePathString(pathData).toNodes(), fill = SolidColor(Color.White))
            .build()
    }
}
