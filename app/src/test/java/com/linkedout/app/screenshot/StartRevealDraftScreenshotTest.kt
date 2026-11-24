package com.linkedout.app.screenshot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect as AndroidRect
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.linkedout.app.R
import com.linkedout.app.core.model.Post
import com.linkedout.app.core.model.PostStats
import com.linkedout.app.di.appModule
import com.linkedout.app.feature.settings.SettingsIcons
import com.linkedout.app.feature.welcome.StartCookie
import com.linkedout.app.navigation.LocalReadableInset
import com.linkedout.app.ui.component.AppBarWithSearch
import com.linkedout.app.ui.component.FolderSwitch
import com.linkedout.app.ui.component.PostCard
import com.linkedout.app.ui.component.SearchCapsuleHint
import com.linkedout.app.ui.component.SetupTile
import com.linkedout.app.ui.component.SetupTileGrid
import com.linkedout.app.ui.component.StartRevealOverlay
import com.linkedout.app.ui.component.StartRevealState
import com.linkedout.app.ui.component.startRevealHole
import com.linkedout.app.ui.glass.LocalGlass
import com.linkedout.app.ui.glass.glassGround
import com.linkedout.app.ui.glass.rememberGlassLook
import com.linkedout.app.ui.icon.LinkedOutIcons
import com.linkedout.app.ui.theme.LinkedOutTheme
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/**
 * DRAFT for the user's yes (requests.md point 44, MTGA's point 48), nothing of app/src/main changes: after the guide's Start
 * the app opens from the Start button outward to the screen's edges, with SystemUI's sparkle ripple drawn by its real AGSL
 * shader (android.graphics.RuntimeShader). A filmstrip as MTGA's draft makes it (the test clock stopped, advanced to each
 * instant, each frame captured with captureToImage, frames side by side with their time): 0, 150, 350, 600, 900, 1250,
 * 1750 ms, phone size, start_reveal_draft_light.png and start_reveal_draft_dark.png, and one full size frame at 600 ms,
 * start_reveal_draft_detail_light.png. The guide (WelcomeOpeningDraftScreenshotTest's layout: the mark, one line, the four
 * tiles, the 80 dp Start cookie, here the app's own StartCookie) lies over Home's feed (the capsule of
 * TopCapsuleDraftScreenshotTest and HomeTopDraftScreenshotTest's cards); the hole cut in the guide is centred on the Start
 * cookie and grows with the first ripple, Home shows through it.
 *
 * The ripple, the waves and the hole are the kit's own (ui/component/StartReveal.kt: StartRevealState.startRipple steps the
 * play time on the test clock through withFrameMillis, startRevealHole cuts the hole, StartRevealOverlay draws the three
 * ripples with RippleShader, MultiRippleController and RippleAnimation): this test calls them as MainTabs will, it copies
 * none of their code. The kit's classes RippleShader, MultiRippleController, RippleAnimation and their config are private
 * to that file and need no call from here. The label under each frame is the play time the state really reached (the
 * test clock steps in frames of 16 ms, so a frame lags a few ms behind its nominal instant).
 *
 * Ports:
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/StartRevealDraftScreenshotTest.kt#StartRevealDraftScreenshotTest
 * https://github.com/LineageOS/android_frameworks_base/blob/lineage-23.0/packages/SystemUI/animation/src/com/android/systemui/surfaceeffects/ripple/RippleShader.kt#RippleShader
 * https://github.com/LineageOS/android_frameworks_base/blob/lineage-23.0/packages/SystemUI/animation/src/com/android/systemui/surfaceeffects/ripple/MultiRippleController.kt#MultiRippleController
 * https://github.com/LineageOS/android_frameworks_base/blob/lineage-23.0/packages/SystemUI/animation/src/com/android/systemui/surfaceeffects/shaderutil/ShaderUtilLibrary.kt#ShaderUtilLibrary
 * (all three through the kit file, StartReveal.kt, which ports them whole)
 * not ported: the inline copy of RippleShader, ShaderUtilLibrary, SdfShaderLibrary, MultiRippleController, RippleAnimation,
 * RippleAnimationConfig and RippleView (MTGA's draft carried them before they became the kit file: StartReveal.kt replaces them)
 * not ported: Waves, MultiRippleView and RIPPLE_DURATION, WAVE_DELAYS, WAVE_TOTAL (the kit's StartRevealOverlay owns the waves
 * and StartRevealState the clock)
 * not ported: Reveal's Animatable clock and the Offscreen box with its BlendMode.Clear circle (StartRevealState.startRipple and
 * startRevealHole are the app's own)
 * not ported: MTGA's Guide with its LoadingMark and X reader tiles (the guide here is LinkedOut's, MarkStandIn stands for the
 * mark that Robolectric does not draw)
 */
@OptIn(ExperimentalRoborazziApi::class, ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class StartRevealDraftScreenshotTest : ScreenshotBase() {

    // PostCard reads the post marks through Koin.
    @Before
    fun startKoinOnce() {
        startKoin {
            androidContext(compose.activity.applicationContext)
            modules(appModule)
        }
    }

    @After
    fun stopKoinAgain() = stopKoin()

    private val times = listOf(0, 150, 350, 600, 900, 1250, 1750)

    private val options = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0f)
    )

    // The cards write the age against the clock, so the posts are dated from it.
    private val now = System.currentTimeMillis()

    // HomeTopDraftScreenshotTest's three posts.
    private val posts = listOf(
        Post(
            id = "7392000000000000001",
            authorHandle = "sundarpichai",
            authorName = "Sundar Pichai",
            text = "Excited to share what the team has been building this year. More soon.",
            publishedAtMillis = now - 2 * 60 * 60 * 1000,
            permalink = "https://www.linkedin.com/posts/sundarpichai_activity-7392000000000000001-abcd",
            stats = PostStats(replies = 128, reposts = 412, likes = 9_300)
        ),
        Post(
            id = "7392000000000000002",
            authorHandle = "microsoft",
            authorName = "Microsoft",
            text = "Three things we learned shipping accessibility features to a billion people.",
            publishedAtMillis = now - 26 * 60 * 60 * 1000,
            permalink = "https://www.linkedin.com/posts/microsoft_activity-7392000000000000002-efgh",
            stats = PostStats(replies = 41, reposts = 97, likes = 2_100)
        ),
        Post(
            id = "7392000000000000003",
            authorHandle = "satyanadella",
            authorName = "Satya Nadella",
            text = "A good week for the people building with our tools. Thank you to every team.",
            publishedAtMillis = now - 3 * 24 * 60 * 60 * 1000,
            permalink = "https://www.linkedin.com/posts/satyanadella_activity-7392000000000000003-ijkl",
            stats = PostStats(replies = 20, reposts = 64, likes = 1_450)
        )
    )

    private fun tile(id: String, name: String, icon: androidx.compose.ui.graphics.vector.ImageVector, state: String, done: Boolean) =
        SetupTile(
            id = id, name = name, title = name, icon = icon, state = state, summary = "", done = done,
            required = false, error = false, onClick = {}
        )

    // WelcomeOpeningDraftScreenshotTest's first run, nothing refused.
    private val tiles = listOf(
        tile("media", "Save media", LinkedOutIcons.Download, "Never", done = true),
        tile("updates", "Updates", SettingsIcons.Update, "Install", done = true),
        tile("notify", "New posts", LinkedOutIcons.Bell, "Off", done = true),
        tile("links", "LinkedIn links", SettingsIcons.Link, "Off", done = false)
    )

    // One test per mode, each a Roborazzi test as in https://github.com/takahirom/roborazzi/blob/main/sample-android/src/test/java/com/github/takahirom/roborazzi/sample/ManualTest.kt#captureRoboImage
    // (a second setContent in the same test kept the first row's state).
    @Test
    fun filmstripLight() = filmstrip(false)

    @Test
    fun filmstripDark() = filmstrip(true)

    private fun filmstrip(night0: Boolean) {
        RuntimeEnvironment.setQualifiers("w411dp-h914dp-420dpi")
        listOf(night0).forEach { night ->
            val played = mutableListOf<Int>()
            val frames = row(night, played)
            sheet(night, frames, played)
            if (!night) {
                frames[times.indexOf(600)].captureRoboImage(
                    "src/test/screenshots/start_reveal_draft_detail_light.png",
                    roborazziOptions = options
                )
            }
        }
    }

    // One row: the guide over Home, the reveal started ([go] true), a frame at each instant on the stopped clock.
    private fun row(night: Boolean, played: MutableList<Int>): List<Bitmap> {
        val go = mutableStateOf(false)
        val state = StartRevealState()
        compose.mainClock.autoAdvance = false
        compose.activity.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                LinkedOutTheme(darkTheme = night, dynamicColor = false) {
                    val look = rememberGlassLook(MaterialTheme.colorScheme, true, quiet = true)
                    CompositionLocalProvider(
                        LocalGlass provides look,
                        LocalContentColor provides MaterialTheme.colorScheme.onBackground
                    ) {
                        Box(
                            Modifier
                                .testTag("stage")
                                .fillMaxSize()
                                .clipToBounds()
                                .glassGround(look, MaterialTheme.colorScheme.background)
                        ) { Reveal(go.value, state) }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        compose.runOnIdle { go.value = true }
        val frames = mutableListOf<Bitmap>()
        var elapsed = 0
        times.forEach { t ->
            compose.mainClock.advanceTimeBy((t - elapsed).toLong())
            elapsed = t
            played += compose.runOnIdle { state.currentPlayTime.roundToInt() }
            frames += compose.onNodeWithTag("stage").captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
        }
        compose.mainClock.autoAdvance = true
        return frames
    }

    // The frames joined, the ms under each.
    private fun sheet(night: Boolean, frames: List<Bitmap>, played: List<Int>) {
        val scale = 0.6f
        val w = (frames[0].width * scale).toInt()
        val h = (frames[0].height * scale).toInt()
        val ms = 40
        val gap = 12
        val sheet = Bitmap.createBitmap((w + gap) * times.size + gap, h + ms + gap, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(if (night) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (night) 0xFFFFFFFF.toInt() else 0xFF000000.toInt(); textSize = 26f }
        val smooth = Paint(Paint.FILTER_BITMAP_FLAG)
        frames.forEachIndexed { i, frame ->
            val left = gap + i * (w + gap)
            canvas.drawBitmap(frame, null, AndroidRect(left, gap, left + w, gap + h), smooth)
            canvas.drawText("${played[i]} ms", left + 4f, gap + h + 28f, text)
        }
        sheet.captureRoboImage(
            "src/test/screenshots/start_reveal_draft_${if (night) "dark" else "light"}.png",
            roborazziOptions = options
        )
    }

    // Home under, the guide over it with the ripple's hole, the ripple above both: the three layers of MainTabs.
    @Composable
    private fun Reveal(go: Boolean, state: StartRevealState) {
        LaunchedEffect(go) {
            if (go) state.startRipple {}
        }
        Box(Modifier.fillMaxSize()) {
            Feed()
            Box(
                Modifier
                    .fillMaxSize()
                    .startRevealHole(state)
            ) {
                Guide(onStartCentre = { state.origin = it })
            }
            StartRevealOverlay(state)
        }
    }

    // Home as TopCapsuleDraftScreenshotTest's capsule and HomeTopDraftScreenshotTest's cards draw it.
    @Composable
    private fun Feed() {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = LocalReadableInset.current)
        ) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                AppBarWithSearch(
                    inputField = { SearchCapsuleHint(LinkedOutIcons.Search, listOf("Search saved posts"), onClick = {}) },
                    navigationIcon = {
                        FolderSwitch(
                            icon = LinkedOutIcons.Folder,
                            trailingIcon = LinkedOutIcons.ExpandMore,
                            label = "All",
                            expanded = false,
                            onClick = {}
                        )
                    }
                )
            }
            posts.forEachIndexed { i, post ->
                PostCard(post = post, onClick = {}, onOpenLink = {}, onDownload = {}, unread = i == 0)
            }
        }
    }

    // WelcomeOpeningDraftScreenshotTest's WelcomeOpeningDraft (private to that file) with the app's own StartCookie, on the
    // page's ground, reporting the cookie's centre.
    @Composable
    private fun Guide(onStartCentre: (androidx.compose.ui.geometry.Offset) -> Unit) {
        BoxWithConstraints(Modifier.fillMaxSize().glassGround(LocalGlass.current, MaterialTheme.colorScheme.background)) {
            val screen = maxHeight
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .widthIn(max = 720.dp).fillMaxWidth().heightIn(min = screen)
                    .windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 8.dp)
            ) {
                MarkStandIn()
                Spacer(Modifier.height(16.dp))
                Text(
                    "LinkedIn reader",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(24.dp))
                SetupTileGrid(tiles) {}
                Spacer(Modifier.height(40.dp))
                StartCookie(
                    description = "Start",
                    onClick = {},
                    modifier = Modifier.onGloballyPositioned { onStartCentre(it.boundsInRoot().center) }
                )
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }

    // LoadingMark does not draw on Robolectric (its modulated bitmaps): its two bitmaps as plain images at 160 dp.
    @Composable
    private fun MarkStandIn() {
        val body = ImageBitmap.imageResource(R.drawable.lo_mark_body)
        val shine = ImageBitmap.imageResource(R.drawable.lo_mark_shine)
        Box(Modifier.size(160.dp)) {
            Image(body, contentDescription = null, modifier = Modifier.fillMaxSize())
            Image(shine, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
    }
}
