package com.linkedout.app.screenshot

import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.linkedout.app.core.model.AccountKind
import com.linkedout.app.core.model.Feed
import com.linkedout.app.core.model.Post
import com.linkedout.app.data.accounts.AccountStore
import com.linkedout.app.data.cache.FeedCache
import com.linkedout.app.data.read.ReadPosts
import com.linkedout.app.di.appModule
import com.linkedout.app.feature.accounts.AccountsScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.TimeUnit

/**
 * Accounts as drawn on a phone and a tablet, by day and by night: empty, then with the four accounts of the
 * validated picture. Written as this app's SettingsScreenshotTest (Koin started with the app's own module, the
 * capture being ScreenshotBase's):
 * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/test/java/com/linkedout/app/screenshot/SettingsScreenshotTest.kt#SettingsScreenshotTest
 * The accounts, their cached feeds and the read marks are the app's own stores filled with fixed data before the
 * screen is drawn, so the view model reads them as it does on a phone.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class AccountsScreenshotTest : ScreenshotBase() {

    @Before
    fun startKoinOnce() {
        startKoin {
            androidContext(compose.activity.applicationContext)
            modules(appModule)
        }
    }

    @After
    fun stopKoinAgain() = stopKoin()

    private inline fun <reified T : Any> app(): T = GlobalContext.get().get()

    // Ages sit in the middle of their hour or day, so the picture does not move between recording and comparing.
    private fun ago(hours: Long) = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(hours) - TimeUnit.MINUTES.toMillis(30)

    private fun posts(handle: String, name: String, count: Int, hoursAgo: Long) = List(count) { index ->
        Post(
            id = "$handle-$index",
            authorHandle = handle,
            authorName = name,
            text = "A post by $name",
            publishedAtMillis = ago(hoursAgo) - index * 1000L,
            permalink = "https://www.linkedin.com/feed/update/urn:li:activity:$handle$index"
        )
    }

    private fun follow(
        handle: String,
        name: String,
        kind: AccountKind,
        folder: String,
        headline: String,
        posts: List<Post>
    ) {
        val store = app<AccountStore>()
        store.add(handle, kind)
        store.updateDisplayName(handle, name)
        store.createFolder(folder)
        store.setFolder(handle, folder)
        runBlocking {
            app<FeedCache>().write(
                Feed(
                    handle = handle,
                    displayName = name,
                    posts = posts,
                    fetchedFromHost = "www.linkedin.com",
                    fetchedAtMillis = System.currentTimeMillis(),
                    headline = headline.takeIf { kind == AccountKind.PERSON },
                    currentCompany = headline.takeIf { kind != AccountKind.PERSON }
                )
            )
        }
    }

    @Test
    fun empty() = capture("accounts_empty") {
        AccountsScreen(onOpenFeed = { _, _ -> }, onOpenFolders = {})
    }

    @Test
    fun filled() {
        follow("sundarpichai", "Sundar Pichai", AccountKind.PERSON, "Main", "CEO at Google and Alphabet",
            posts("sundarpichai", "Sundar Pichai", 3, 2))
        follow("microsoft", "Microsoft", AccountKind.COMPANY, "Tech", "Software Development",
            posts("microsoft", "Microsoft", 5, 36))
        follow("marie-dupont", "Marie Dupont", AccountKind.PERSON, "Health", "Directrice des soins, CHU de Nantes",
            posts("marie-dupont", "Marie Dupont", 3, 84))
        // Never read: a page was fetched once but it showed no posts, so no age and no count.
        follow("chu-nantes", "CHU de Nantes", AccountKind.COMPANY, "Health", "Hospitals and Health Care", emptyList())
        // Everything unread until the read marks are back from the disk.
        runBlocking { withTimeout(5_000) { app<ReadPosts>().state.first { it.ready } } }
        capture("accounts_filled", interact = {
            compose.waitUntil(5_000) { compose.onAllNodesWithText("5").fetchSemanticsNodes().isNotEmpty() }
        }) {
            AccountsScreen(onOpenFeed = { _, _ -> }, onOpenFolders = {})
        }
    }
}
