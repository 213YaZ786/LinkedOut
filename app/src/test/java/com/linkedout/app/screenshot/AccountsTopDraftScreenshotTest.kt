package com.linkedout.app.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.linkedout.app.di.appModule
import com.linkedout.app.navigation.LocalReadableInset
import com.linkedout.app.ui.component.Avatar
import com.linkedout.app.ui.component.BannerAction
import com.linkedout.app.ui.component.DropdownMenuGroup
import com.linkedout.app.ui.component.DropdownMenuItemContent
import com.linkedout.app.ui.component.GlassSearchField
import com.linkedout.app.ui.component.MenuPill
import com.linkedout.app.ui.component.SelectableDropdownMenuItem
import com.linkedout.app.ui.component.Zone
import com.linkedout.app.ui.component.relativeTime
import com.linkedout.app.ui.icon.LinkedOutIcons
import com.linkedout.app.ui.theme.zone
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
 * DRAFT for the user's yes (requests.md point 11), nothing of app/src/main changes: the top of Accounts as ONE row,
 * the shape of Bluesky's header (a slot at each side, the centre between them, the slots both always drawn so the
 * middle stays centred):
 * https://github.com/bluesky-social/social-app/blob/main/src/view/com/home/HomeHeaderLayoutMobile.tsx#HomeHeaderLayoutMobile
 * In the middle the folder pill (the MenuPill of Home) with "N followed" beside it, Folders at the right, then the
 * paste field unchanged and no FeedChips (the pill's menu stands for them). The rows are the account cards as
 * AccountsScreen draws them (private there, so a faithful copy of the zone):
 * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/accounts/AccountsScreen.kt#AccountCard
 * Set up as this app's AccountsScreenshotTest (Koin with the app's module, ScreenshotBase's capture).
 * d: the Folders button gone (both side slots empty), the pill's menu drawn open over the list as the Home and Jobs drafts
 * draw theirs (MenuOverScreen, JobsDraftScreenshotTest.kt, Robolectric draws no Popup): the folders as a group of choices,
 * then in a group of its own the action "Manage folders" (not selectable), the groups apart as DropdownMenuGroup does:
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/Menu.kt#DropdownMenuGroup
 * not ported: HomeHeaderLayoutMobile's MenuButton, the Logo, the Feeds link, haptics and soft reset, the hide on scroll,
 * StatusBarFade; AccountsScreen's view model, CleanLinkEffect, the candidate card, notice, HoldMenu and its actions, the
 * FolderDialog and unfollow dialog, EmptyState, the pill's menu itself (drawn closed)
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class AccountsTopDraftScreenshotTest : ScreenshotBase() {

    @Before
    fun startKoinOnce() {
        startKoin {
            androidContext(compose.activity.applicationContext)
            modules(appModule)
        }
    }

    @After
    fun stopKoinAgain() = stopKoin()

    private val now = System.currentTimeMillis()

    private class Sample(val name: String, val headline: String, val hoursAgo: Long, val unread: Int)

    private val samples = listOf(
        Sample("Satya Nadella", "Chairman and CEO at Microsoft", 2, 3),
        Sample("Microsoft", "Software Development", 36, 5),
        Sample("Sundar Pichai", "CEO at Google and Alphabet", 84, 0),
        Sample("Google DeepMind", "Research Services", 120, 2)
    )

    @Test
    fun c() = capture("accounts_top_c") {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            TopRow(followed = true)
            Field()
            List()
        }
    }

    @Test
    fun empty() = capture("accounts_top_empty") {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            TopRow(followed = false)
            Field()
        }
    }

    // d: no Folders button, the pill's menu open. The action row is DropdownMenuItemContent (the rows' own frame) with a
    // leading icon and a trailing chevron, no selected state.
    // https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/Menu.kt#DropdownMenuGroup
    // https://github.com/bluesky-social/social-app/blob/main/src/view/com/home/HomeHeaderLayoutMobile.tsx#HomeHeaderLayoutMobile
    @Test
    fun d() = capture("accounts_top_d") {
        var anchor by remember { mutableStateOf(IntOffset.Zero) }
        MenuOverScreen(
            open = true,
            anchor = anchor,
            screen = {
                Column(Modifier.fillMaxSize().systemBarsPadding()) {
                    TopRow(followed = true, folders = false, onPill = { anchor = it })
                    Field()
                    List()
                }
            }
        ) {
            DropdownMenuGroup(index = 0, count = 2) {
                SelectableDropdownMenuItem(selected = true, onClick = {}, text = "All")
                listOf("Clients", "Tech leaders", "Recruiters").forEach {
                    SelectableDropdownMenuItem(selected = false, onClick = {}, text = it)
                }
            }
            DropdownMenuGroup(index = 1, count = 2) {
                DropdownMenuItemContent(selected = false, interaction = Modifier) {
                    Icon(LinkedOutIcons.Tune, contentDescription = null, modifier = Modifier.size(24.dp))
                    Text(
                        "Manage folders",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        LinkedOutIcons.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // The header row: empty left slot, the centre taking the rest, Folders in the right slot. Nobody followed: the
    // centre is empty, Folders alone.
    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun TopRow(followed: Boolean, folders: Boolean = true, onPill: (IntOffset) -> Unit = {}) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = BannerActionSize + 16.dp)
                .padding(horizontal = 16.dp + LocalReadableInset.current, vertical = BannerGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(BannerActionSize))
            Box(Modifier.weight(1f).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                if (followed) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                        itemVerticalAlignment = Alignment.CenterVertically
                    ) {
                        MenuPill(
                            icon = LinkedOutIcons.Folder,
                            label = "All",
                            expanded = false,
                            trailingIcon = LinkedOutIcons.ExpandMore,
                            modifier = Modifier.onGloballyPositioned {
                                val at = it.positionInRoot()
                                onPill(IntOffset((at.x + it.size.width / 2).toInt(), (at.y + it.size.height).toInt()))
                            },
                            onClick = {}
                        )
                        Text(
                            "12 followed",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Box(Modifier.size(BannerActionSize), contentAlignment = Alignment.Center) {
                if (folders) BannerAction(icon = LinkedOutIcons.Folder, label = "Folders", onClick = {})
            }
        }
    }

    // The paste field as AccountsScreen builds it: search icon, "Paste" as a transparent target in the field's glass.
    @Composable
    private fun Field() {
        GlassSearchField(
            value = "",
            onValueChange = {},
            placeholder = "Profile address",
            leadingIcon = { Icon(LinkedOutIcons.Search, contentDescription = null) },
            trailingIcon = {
                Surface(
                    onClick = {},
                    shape = ButtonDefaults.textShape,
                    color = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Box(
                        modifier = Modifier.heightIn(min = ButtonDefaults.MinHeight).padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Paste", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp + LocalReadableInset.current)
        )
    }

    @Composable
    private fun List() {
        Column(
            Modifier.padding(start = 16.dp + LocalReadableInset.current, end = 16.dp + LocalReadableInset.current, top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            samples.forEach { AccountCard(it) }
        }
    }

    // AccountCard's zone, without its hold menu.
    @Composable
    private fun AccountCard(sample: Sample) {
        Zone(color = MaterialTheme.colorScheme.zone, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Avatar(url = null, name = sample.name, size = 44.dp)
                Column(Modifier.weight(1f)) {
                    Text(sample.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        sample.headline,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        relativeTime(now - sample.hoursAgo * 60 * 60 * 1000 - 30 * 60 * 1000),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (sample.unread > 0) {
                        Box(
                            modifier = Modifier
                                .widthIn(min = 28.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                sample.unread.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
        }
    }

    // ScreenBanner's BannerGap and BannerActionSize (private there).
    private val BannerGap = 8.dp
    private val BannerActionSize = 44.dp
}
