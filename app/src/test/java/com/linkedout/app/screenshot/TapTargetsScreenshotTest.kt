package com.linkedout.app.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.activity.compose.setContent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.linkedout.app.core.common.AppError
import com.linkedout.app.core.common.ChallengeKind
import com.linkedout.app.core.common.present
import com.linkedout.app.core.model.AccountKind
import com.linkedout.app.core.model.Post
import com.linkedout.app.core.model.PostStats
import com.linkedout.app.data.accounts.AccountStore
import com.linkedout.app.di.appModule
import com.linkedout.app.feature.accounts.FoldersScreen
import com.linkedout.app.navigation.ReadableScroll
import com.linkedout.app.ui.component.BoldButton
import com.linkedout.app.ui.component.PostCard
import com.linkedout.app.ui.component.QuietButton
import com.linkedout.app.ui.component.ZoneAlertDialog
import com.linkedout.app.ui.glass.LocalGlass
import com.linkedout.app.ui.glass.glassGround
import com.linkedout.app.ui.glass.rememberGlassLook
import com.linkedout.app.ui.theme.LinkedOutTheme
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What a tap opens today, drawn as it is (nothing of app/src/main changes), for the user to see:
 * folders_now is the real FoldersScreen (fed through the app's AccountStore, as AccountsScreenshotTest feeds Accounts),
 * folders_open_now the same with the first folder's Open tapped; alert_now is Home's red triangle: FailureDialog with
 * two failures, drawn over Home's cards. The dialog is its own window, so it is captured as Roborazzi's WindowCaptureTest
 * does, captureScreenRoboImage drawing the dialog's window over the activity's:
 * https://github.com/takahirom/roborazzi/blob/main/sample-android/src/test/java/com/github/takahirom/roborazzi/sample/WindowCaptureTest.kt#WindowCaptureTest
 * not ported: WindowCaptureTest's composeBottomSheet, androidDialog and the other window tests (a bottom sheet, a
 * Material dialog and a DialogFragment: this app has none of them), its createComposeRule and NexusOne qualifiers (the
 * ScreenshotBase rule and its phone and tablet sizes are used)
 * FailureDialog is private to TimelineScreen.kt, so the test holds a faithful local copy of it (same texts, same
 * buttons), its reasons coming from the real AppError.present():
 * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/timeline/TimelineScreen.kt#FailureDialog
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class TapTargetsScreenshotTest : ScreenshotBase() {

    @Before
    fun startKoinOnce() {
        startKoin {
            androidContext(compose.activity.applicationContext)
            modules(appModule)
        }
    }

    @After
    fun stopKoinAgain() = stopKoin()

    private fun follow(handle: String, name: String, kind: AccountKind, folder: String) {
        val store = GlobalContext.get().get<AccountStore>()
        store.add(handle, kind)
        store.updateDisplayName(handle, name)
        store.createFolder(folder)
        store.setFolder(handle, folder)
    }

    private fun seedFolders() {
        listOf("Satya Nadella", "Microsoft", "Sundar Pichai", "Google").forEachIndexed { i, name ->
            follow(name.lowercase().replace(' ', '-'), name,
                if (i % 2 == 1) AccountKind.COMPANY else AccountKind.PERSON, "Clients")
        }
        listOf("Jensen Huang", "Lisa Su", "Dario Amodei", "Demis Hassabis", "Fei-Fei Li").forEach {
            follow(it.lowercase().replace(' ', '-'), it, AccountKind.PERSON, "Tech leaders")
        }
        listOf("Marie Dupont", "Karim Benali", "Sofia Rossi").forEach {
            follow(it.lowercase().replace(' ', '-'), it, AccountKind.PERSON, "Recruiters")
        }
    }

    @Test
    fun foldersNow() {
        seedFolders()
        capture("folders_now") { FoldersScreen(onBack = {}) }
    }

    @Test
    fun foldersOpenNow() {
        seedFolders()
        capture("folders_open_now", interact = {
            compose.onAllNodesWithText("Open")[0].performClick()
            compose.waitForIdle()
        }) { FoldersScreen(onBack = {}) }
    }

    // The cards are dated from the clock, as HomeTopDraftScreenshotTest does.
    private val now = System.currentTimeMillis()

    private fun post(n: Int, handle: String, name: String, text: String, hoursAgo: Long) = Post(
        id = "739200000000000000$n",
        authorHandle = handle,
        authorName = name,
        text = text,
        publishedAtMillis = now - hoursAgo * 60 * 60 * 1000,
        permalink = "https://www.linkedin.com/posts/${handle}_activity-739200000000000000$n-abcd",
        stats = PostStats(replies = 30 * n, reposts = 90 * n, likes = 1_000 * n)
    )

    private val failed = listOf(
        "satyanadella" to AppError.ChallengeRequired(
            host = "www.linkedin.com",
            url = "https://www.linkedin.com/in/satyanadella",
            kind = ChallengeKind.JS_INTERSTITIAL,
            status = 200
        ),
        "sundarpichai" to AppError.Offline
    )

    // ScreenshotBase.capture frames the screen and takes onRoot(), which leaves a dialog's window out: the same frame,
    // then captureScreenRoboImage as WindowCaptureTest.composeDialog.
    @Test
    fun alertNow() {
        val options = RoborazziOptions(
            compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0f),
            recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5)
        )
        var picture = 0
        listOf("phone" to "w411dp-h914dp-420dpi", "tablet" to "w1280dp-h800dp-320dpi").forEach { (device, qualifiers) ->
            RuntimeEnvironment.setQualifiers(qualifiers)
            listOf(false, true).forEach { night ->
                val id = picture++
                compose.activity.setContent {
                    CompositionLocalProvider(LocalInspectionMode provides true) {
                        LinkedOutTheme(darkTheme = night, dynamicColor = false) {
                            val look = rememberGlassLook(MaterialTheme.colorScheme, true, quiet = true)
                            CompositionLocalProvider(LocalGlass provides look, LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                                key(id) {
                                    Box(Modifier.fillMaxSize().glassGround(look, MaterialTheme.colorScheme.background)) {
                                        ReadableScroll {
                                            HomeBehind()
                                            FailureDialog(failed = failed, silentDownloads = false, onRetry = {}, onDismiss = {})
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                compose.waitForIdle()
                captureScreenRoboImage(
                    "src/test/screenshots/alert_now_${device}_${if (night) "dark" else "light"}.png",
                    roborazziOptions = options
                )
            }
        }
    }

    @Composable
    private fun HomeBehind() {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text("Home", style = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.primary))
            }
            PostCard(
                post = post(3, "sundarpichai", "Sundar Pichai", "Excited to share what the team has been building this year. More soon.", 2),
                onClick = {}, onOpenLink = {}, onDownload = {}, unread = true
            )
            PostCard(
                post = post(2, "microsoft", "Microsoft", "Three things we learned shipping accessibility features to a billion people.", 26),
                onClick = {}, onOpenLink = {}, onDownload = {}
            )
        }
    }

    // A faithful copy of FailureDialog (TimelineScreen.kt, private), the texts and buttons as they are.
    @Composable
    private fun FailureDialog(
        failed: List<Pair<String, AppError>>,
        silentDownloads: Boolean,
        onRetry: () -> Unit,
        onDismiss: () -> Unit
    ) {
        ZoneAlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    when {
                        failed.isEmpty() -> "Downloads have no progress bar"
                        failed.size == 1 -> "1 profile could not be updated"
                        else -> "${failed.size} profiles could not be updated"
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (failed.isNotEmpty()) {
                        Text(
                            "Their saved posts are still in the stream.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    failed.forEach { (handle, error) ->
                        val reason = error.present()
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("in/$handle", style = MaterialTheme.typography.titleSmall)
                            Text(
                                reason.explanation,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (silentDownloads) {
                        Text(
                            "Media is being saved for offline reading, but Android is not letting " +
                                "LinkedOut show the progress. The files still arrive. Allow " +
                                "notifications for LinkedOut in Android to see how far they are.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
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
}
