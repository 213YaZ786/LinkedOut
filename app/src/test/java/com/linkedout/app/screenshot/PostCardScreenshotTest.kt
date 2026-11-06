package com.linkedout.app.screenshot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.linkedout.app.core.model.Post
import com.linkedout.app.core.model.PostStats
import com.linkedout.app.di.appModule
import com.linkedout.app.ui.component.PostCard
import com.linkedout.app.ui.component.ScreenBanner
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Home's banner and two post cards as drawn on a phone and a tablet, by day and by night, with fixed posts:
 * the first one arrived since the last visit (the dot at the end of its name line), the second one read.
 * Written as SettingsScreenshotTest (Koin started with the app's module, since a card asks for the post marks)
 * https://github.com/213YaZ786/Language/blob/main/app/src/test/java/com/yaz/language/screenshot/WelcomeScreenshotTest.kt#WelcomeScreenshotTest
 * (fixed data handed to the screen, the capture itself ScreenshotBase's).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class PostCardScreenshotTest : ScreenshotBase() {

    @Before
    fun startKoinOnce() {
        startKoin {
            androidContext(compose.activity.applicationContext)
            modules(appModule)
        }
    }

    @After
    fun stopKoinAgain() = stopKoin()

    // The card writes the age against the clock, so the posts are dated from it: the words stay "2h" and "1d".
    private val now = System.currentTimeMillis()

    private val unread = Post(
        id = "7392000000000000001",
        authorHandle = "sundarpichai",
        authorName = "Sundar Pichai",
        text = "Excited to share what the team has been building this year. More soon.",
        publishedAtMillis = now - 2 * 60 * 60 * 1000,
        permalink = "https://www.linkedin.com/posts/sundarpichai_activity-7392000000000000001-abcd",
        stats = PostStats(replies = 128, reposts = 412, likes = 9_300)
    )

    private val read = Post(
        id = "7392000000000000002",
        authorHandle = "microsoft",
        authorName = "Microsoft",
        text = "Three things we learned shipping accessibility features to a billion people.",
        publishedAtMillis = now - 26 * 60 * 60 * 1000,
        permalink = "https://www.linkedin.com/posts/microsoft_activity-7392000000000000002-efgh",
        stats = PostStats(replies = 41, reposts = 97, likes = 2_100)
    )

    @Test
    fun cards() = capture("home_cards") {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            ScreenBanner(title = "Home", subtitle = "All accounts")
            Spacer(Modifier.height(8.dp))
            PostCard(post = unread, onClick = {}, onOpenLink = {}, onDownload = {}, unread = true)
            PostCard(post = read, onClick = {}, onOpenLink = {}, onDownload = {})
        }
    }
}
