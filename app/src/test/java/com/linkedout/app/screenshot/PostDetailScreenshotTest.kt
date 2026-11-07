package com.linkedout.app.screenshot

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.linkedout.app.core.model.Conversation
import com.linkedout.app.core.model.Post
import com.linkedout.app.core.model.PostStats
import com.linkedout.app.di.appModule
import com.linkedout.app.feature.post.ConversationView
import com.linkedout.app.feature.post.ThreadState
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
 * The post page as drawn on a phone and a tablet, by day and by night, with fixed data: the profile page's banner
 * (a round glass Back, "Post" as plain text in the accent, Share) as the list's first row, the post in full and
 * one reply. The real ConversationView is drawn, not a copy: the screen around it only adds the ViewModel.
 * Written as PostCardScreenshotTest (Koin started with the app's module, since a card asks for the post marks)
 * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/test/java/com/linkedout/app/screenshot/PostCardScreenshotTest.kt#PostCardScreenshotTest
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class PostDetailScreenshotTest : ScreenshotBase() {

    @Before
    fun startKoinOnce() {
        startKoin {
            androidContext(compose.activity.applicationContext)
            modules(appModule)
        }
    }

    @After
    fun stopKoinAgain() = stopKoin()

    // A reply's card writes its age against the clock, so it is dated from it: the words stay "1d".
    private val now = System.currentTimeMillis()

    private val main = Post(
        id = "7392000000000000001",
        authorHandle = "sundarpichai",
        authorName = "Sundar Pichai",
        text = "Excited to share what the team has been building this year. More soon. " +
            "Thank you to everyone who tested early versions and sent us their notes.",
        // The page writes the full date of the post, so this one is fixed (2025-11-03, 10:00 UTC), never the clock.
        publishedAtMillis = 1_762_164_000_000L,
        permalink = "https://www.linkedin.com/posts/sundarpichai_activity-7392000000000000001-abcd",
        stats = PostStats(replies = 128, reposts = 412, likes = 9_300)
    )

    private val reply = Post(
        id = "7392000000000000002",
        authorHandle = "microsoft",
        authorName = "Microsoft",
        text = "Three things we learned shipping accessibility features to a billion people.",
        publishedAtMillis = now - 26 * 60 * 60 * 1000,
        permalink = "https://www.linkedin.com/posts/microsoft_activity-7392000000000000002-efgh",
        stats = PostStats(replies = 41, reposts = 97, likes = 2_100)
    )

    @Test
    fun page() = capture("post_detail") {
        ConversationView(
            onBack = {},
            onShare = {},
            post = main,
            thread = ThreadState.Ready(
                Conversation(
                    ancestors = emptyList(),
                    main = main,
                    continuation = emptyList(),
                    replies = listOf(listOf(reply)),
                    host = "www.linkedin.com"
                )
            ),
            onOpenProfile = {},
            onOpenPost = {},
            onRetry = {},
            shareLink = main.permalink
        )
    }
}
