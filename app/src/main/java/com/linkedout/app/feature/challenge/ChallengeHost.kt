package com.linkedout.app.feature.challenge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.webkit.WebView
import com.linkedout.app.core.web.ChallengeDriver
import com.linkedout.app.core.web.ChallengeSolver
import org.koin.compose.koinInject

/**
 * Hosts the offscreen check. Composed underneath the whole app, so the WebView
 * is attached to a live window and runs like a real browser, while the opaque
 * app surface above it hides it and swallows every touch. The reader never
 * sees it.
 */
@Composable
fun ChallengeBackstage(solver: ChallengeSolver = koinInject()) {
    DisposableEffect(solver) {
        solver.attachHost()
        onDispose { solver.detachHost() }
    }

    val task by solver.task.collectAsState()
    val current = task ?: return
    if (current.interactive) return

    key(current) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
            ChallengeWebView(
                task = current,
                solver = solver,
                // A phone sized viewport, some checks look at it.
                modifier = Modifier.requiredSize(width = 360.dp, height = 640.dp)
            )
        }
    }
}

/**
 * Drawn above the app. A small status pill while a background check runs,
 * and the full screen check when the user asked to complete one by hand.
 */
@Composable
fun ChallengeOverlay(solver: ChallengeSolver = koinInject()) {
    val task by solver.task.collectAsState()
    val current = task ?: return

    key(current) {
        if (current.interactive) {
            InteractiveCheck(current, solver)
        } else {
            Box(
                modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 96.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    tonalElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.inverseOnSurface
                        )
                        Text(
                            "Checking access to ${current.host}",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InteractiveCheck(task: ChallengeSolver.Task, solver: ChallengeSolver) {
    Dialog(
        onDismissRequest = { solver.complete(task, ChallengeSolver.Result.Cancelled) },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Quick check", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${task.host} wants to make sure a real person is reading. This closes by itself once done.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = { solver.complete(task, ChallengeSolver.Result.Cancelled) }
                    ) { Text("Close") }
                }
                ChallengeWebView(
                    task = task,
                    solver = solver,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ChallengeWebView(
    task: ChallengeSolver.Task,
    solver: ChallengeSolver,
    modifier: Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context -> ChallengeDriver(context, task, solver).view },
        onRelease = { view: WebView -> (view.tag as? ChallengeDriver)?.release() }
    )
}
