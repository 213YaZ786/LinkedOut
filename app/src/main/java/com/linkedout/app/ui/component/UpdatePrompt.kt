package com.linkedout.app.ui.component

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.linkedout.app.core.update.Release
import com.linkedout.app.core.update.UpdateMode
import com.linkedout.app.core.update.Updates
import kotlinx.coroutines.launch

/** Once per launch of the process, not on every return to the screen. */
private var checkedThisLaunch = false

private enum class Step { ASK, DOWNLOADING, FAILED }

/**
 * Asks GitHub once, when the app opens, whether a newer version is out, and
 * says so in a dialog. [mode] NOTIFY offers the release page; INSTALL
 * downloads, checks and installs it without a dialog, Android confirming
 * only when it must (see UpdatedReceiver for what follows).
 * Nothing is shown when the app is up to date or GitHub cannot be reached.
 */
@Composable
fun UpdatePrompt(mode: UpdateMode, currentVersion: String) {
    if (mode == UpdateMode.OFF) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var release by remember { mutableStateOf<Release?>(null) }
    var step by remember { mutableStateOf(Step.ASK) }

    LaunchedEffect(Unit) {
        if (checkedThisLaunch) return@LaunchedEffect
        checkedThisLaunch = true
        val newer = Updates.latest()?.takeIf { Updates.isNewer(it.version, currentVersion) } ?: return@LaunchedEffect
        // Install mode asks nothing: the update downloads and installs by
        // itself, Android confirming only when it must. The dialog shows
        // only when installing cannot go ahead, or failed.
        if (mode == UpdateMode.INSTALL && newer.apk != null && Updates.canInstall(context)) {
            if (!Updates.install(context, newer)) {
                step = Step.FAILED
                release = newer
            }
        } else {
            release = newer
        }
    }

    val shown = release ?: return
    val openPage = {
        release = null
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(shown.page)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    ZoneAlertDialog(
        onDismissRequest = { if (step != Step.DOWNLOADING) release = null },
        title = { Text("New version available") },
        text = {
            when (step) {
                Step.ASK -> Text("LinkedOut ${shown.version}")
                Step.DOWNLOADING -> Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LoadingMark(size = 28.dp)
                    Text("Downloading")
                }
                Step.FAILED -> Text("The update could not be installed.")
            }
        },
        confirmButton = {
            when {
                step == Step.DOWNLOADING -> Unit
                mode == UpdateMode.NOTIFY || step == Step.FAILED || shown.apk == null ->
                    QuietButton(onClick = openPage) { Text("Download") }
                else -> QuietButton(onClick = {
                    if (!Updates.canInstall(context)) {
                        Updates.allowInstalls(context)
                    } else {
                        step = Step.DOWNLOADING
                        scope.launch {
                            if (Updates.install(context, shown)) release = null else step = Step.FAILED
                        }
                    }
                }) { Text("Install") }
            }
        },
        dismissButton = {
            if (step != Step.DOWNLOADING) QuietButton(onClick = { release = null }) { Text("Later") }
        }
    )
}
