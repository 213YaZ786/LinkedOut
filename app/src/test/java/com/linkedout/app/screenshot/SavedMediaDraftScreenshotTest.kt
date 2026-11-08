package com.linkedout.app.screenshot

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.linkedout.app.di.appModule
import com.linkedout.app.navigation.LocalReadableInset
import com.linkedout.app.ui.component.BannerAction
import com.linkedout.app.ui.component.BarTitle
import com.linkedout.app.ui.component.BoldButton
import com.linkedout.app.ui.component.BoldIconButton
import com.linkedout.app.ui.component.FloatingTopBar
import com.linkedout.app.ui.component.QuietButton
import com.linkedout.app.ui.component.ScreenBanner
import com.linkedout.app.ui.component.Zone
import com.linkedout.app.ui.component.ZoneGap
import com.linkedout.app.ui.component.plus
import com.linkedout.app.ui.component.scrolledFromTop
import com.linkedout.app.ui.component.statusBarTop
import com.linkedout.app.ui.component.underFloatingTopBar
import com.linkedout.app.ui.icon.LinkedOutIcons
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
 * DRAFT for the user's yes (requests.md point 18), nothing of app/src/main changes: Saved media as MTGA builds it.
 * saved_media_now is today's screen (feature/storage/SavedMediaScreen.kt: the banner, then a row per file with its name,
 * its size and a bin). saved_media_draft is MTGA's grid: the total at the top, per account a SavedHeader, per post a
 * SavedPostBar, the pictures as SavedCells in a LazyVerticalGrid with GridCells.Adaptive, under the FloatingTopBar.
 *
 * Ports: the screen, its grid and its row composables (AccountHeader, PostBar, Cell)
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/media/SavedMediaScreen.kt#SavedMediaScreen
 * and the row types with the pure build of the rows
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/media/SavedMediaViewModel.kt#SavedMediaViewModel
 * Today's screen is LinkedOut's own: the row of a file is copied from it.
 *
 * not ported: SavedMediaViewModel's init, deletePost, deleteAccount, loadImages with its MediaStore content observer,
 * onCleared, reload and registerObserver (they read the folder and Downloads, a picture has fixed data), and build's
 * second loop over the manual saves (ManualSaves is MTGA's own, LinkedOut has no saves made by hand), the empty and
 * loading text, the posts' open callbacks (nothing is tapped), AsyncImage for the cell's picture (Robolectric loads no
 * network or file picture, so a plain coloured box stands in), SavedMediaScreen's `viewModel`, and the previous
 * version's file rows of MTGA.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class SavedMediaDraftScreenshotTest : ScreenshotBase() {

    @Before
    fun startKoinOnce() {
        startKoin {
            androidContext(compose.activity.applicationContext)
            modules(appModule)
        }
    }

    @After
    fun stopKoinAgain() = stopKoin()

    // Two accounts, 2 and 3 posts, a video with a still and a video without, photos in between. Sizes in bytes.
    private val files = listOf(
        Auto("sundarpichai", "7392000000000000003", 0, "/files/media/sundarpichai/7392000000000000003_0.jpg", 612_000),
        Auto("sundarpichai", "7392000000000000003", 1, "/files/media/sundarpichai/7392000000000000003_1.jpg", 488_000),
        Auto("sundarpichai", "7392000000000000003", 2, "/files/media/sundarpichai/7392000000000000003_2.jpg", 530_000),
        Auto("sundarpichai", "7392000000000000002", 0, "/files/media/sundarpichai/7392000000000000002_0.mp4", 8_400_000),
        Auto("sundarpichai", "7392000000000000001", 0, "/files/media/sundarpichai/7392000000000000001_0.jpg", 745_000),
        Auto("sundarpichai", "7392000000000000001", 1, "/files/media/sundarpichai/7392000000000000001_1.jpg", 390_000),
        Auto("microsoft", "7391000000000000002", 0, "/files/media/microsoft/7391000000000000002_0.jpg", 902_000),
        Auto("microsoft", "7391000000000000002", 1, "/files/media/microsoft/7391000000000000002_1.mp4", 12_800_000),
        Auto("microsoft", "7391000000000000001", 0, "/files/media/microsoft/7391000000000000001_0.jpg", 455_000)
    )

    // The still of the first video only: the second shows the plain play mark, as a video without a still does.
    private val stills = mapOf("7392000000000000002/0" to "/files/media/stills/7392000000000000002_0.jpg")

    // Read at first use: build needs VIDEO_EXTENSIONS, declared further down and still null at this point of the class.
    private val state by lazy { build(files, stills) }

    @Test
    fun savedMediaNow() = capture("saved_media_now") {
        val context = LocalContext.current
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = LocalReadableInset.current,
                end = LocalReadableInset.current,
                top = statusBarTop(),
                bottom = 24.dp
            )
        ) {
            item(key = "banner") {
                ScreenBanner(
                    title = "Saved media",
                    subtitle = "${files.size} files, ${Formatter.formatShortFileSize(context, state.appBytes)}",
                    leading = { BannerAction(icon = LinkedOutIcons.ArrowBack, label = "Back", onClick = {}) },
                    trailing = { BannerAction(icon = LinkedOutIcons.Delete, label = "Delete every saved file", onClick = {}) }
                )
            }
            items(files, key = { it.path }) { file ->
                Zone(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = ZoneGap / 2)
                ) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                file.path.substringAfterLast('/'),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                Formatter.formatShortFileSize(context, file.bytes),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // The bin is a transparent tap target, what IconButton is (the guard refuses it in app code).
                        Surface(onClick = {}, shape = CircleShape, color = Color.Transparent) {
                            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                Icon(LinkedOutIcons.Delete, contentDescription = "Delete this file", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun savedMediaDraft() = capture("saved_media_draft") {
        val context = LocalContext.current
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                FloatingTopBar(
                    title = { BarTitle("Saved media") },
                    navigationIcon = {
                        Surface(onClick = {}, shape = CircleShape, color = Color.Transparent) {
                            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                Icon(LinkedOutIcons.ArrowBack, contentDescription = "Back", modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                )
            }
        ) { padding ->
            Box(Modifier.fillMaxSize()) {
                val gridState = rememberLazyGridState()
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(CELL_MIN),
                    modifier = Modifier.fillMaxSize().underFloatingTopBar(
                        barHeight = padding.calculateTopPadding(),
                        scrolled = gridState::scrolledFromTop
                    ),
                    contentPadding = PaddingValues(
                        start = LocalReadableInset.current + 16.dp,
                        end = LocalReadableInset.current + 16.dp,
                        bottom = 24.dp
                    ).plus(padding),
                    horizontalArrangement = Arrangement.spacedBy(GAP),
                    verticalArrangement = Arrangement.spacedBy(GAP)
                ) {
                    item(key = "total", span = { GridItemSpan(maxLineSpan) }, contentType = "total") {
                        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                            Text(
                                "${state.fileCount} files",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (state.appBytes > 0) {
                                Text(
                                    Formatter.formatShortFileSize(context, state.appBytes) + " in the app's folder",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    items(
                        state.rows,
                        key = { it.key },
                        span = { row ->
                            if (row is SavedCell) GridItemSpan(1) else GridItemSpan(maxLineSpan)
                        },
                        contentType = {
                            when (it) {
                                is SavedHeader -> "header"
                                is SavedPostBar -> "post"
                                is SavedCell -> "cell"
                            }
                        }
                    ) { row ->
                        when (row) {
                            is SavedHeader -> AccountHeader(row) {}
                            is SavedPostBar -> PostBar(row, {}) {}
                            is SavedCell -> Cell(row) {}
                        }
                    }
                }
            }
        }
    }

    /** An account's name and count above its cells, with the delete of its copies when it has any. */
    @Composable
    private fun AccountHeader(header: SavedHeader, onDelete: () -> Unit) {
        val context = LocalContext.current
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("@${header.handle}", style = MaterialTheme.typography.titleSmall)
                Text(
                    "${header.files} files" +
                        if (header.appFiles > 0) ", " + Formatter.formatShortFileSize(context, header.appBytes) + " in the app" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (header.appFiles > 0) {
                BoldButton(onClick = onDelete) { Text("Delete copies") }
            }
        }
    }

    /** A post's line: its file count opens the post, the bin deletes its copies in the app's folder. */
    @Composable
    private fun PostBar(bar: SavedPostBar, onOpen: () -> Unit, onDelete: () -> Unit) {
        val context = LocalContext.current
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            QuietButton(onClick = onOpen) {
                Text(
                    "${bar.files} file${if (bar.files == 1) "" else "s"}" +
                        if (bar.appBytes > 0) ", " + Formatter.formatShortFileSize(context, bar.appBytes) else ""
                )
            }
            BoldIconButton(onClick = onDelete) {
                Icon(LinkedOutIcons.Delete, contentDescription = "Delete this post's media")
            }
        }
    }

    /**
     * One saved media: a square, as wide as the grid gives, no text. The picture is a plain coloured box here, where
     * the app draws it with Coil at the cell's size.
     */
    @Composable
    private fun Cell(cell: SavedCell, onOpen: () -> Unit) {
        val stand = Palette[(cell.postId.takeLast(1).toInt() * 3 + cell.position) % Palette.size]
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            if (cell.thumb != null) {
                Box(Modifier.fillMaxSize().background(stand))
            }
            if (cell.video) {
                Icon(
                    LinkedOutIcons.Play,
                    contentDescription = "Video",
                    tint = if (cell.thumb != null) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }

    private val CELL_MIN = 104.dp
    // SMS's TileGap, as MTGA keeps it: 4 dp let the cells touch.
    private val GAP = 10.dp
    private val Palette = listOf(
        Color(0xFF5B8DB8), Color(0xFFB87A5B), Color(0xFF6FA37B), Color(0xFF9A7BB8), Color(0xFFB8A25B), Color(0xFF5BA3A8)
    )

    /** One line of the screen's grid: an account's heading or one saved media. */
    private sealed interface SavedRow {
        val key: String
    }

    /** An account's heading. [appFiles] are the copies in the app's own folder, the only ones this screen deletes. */
    private data class SavedHeader(
        val handle: String,
        val files: Int,
        val appFiles: Int,
        val appBytes: Long
    ) : SavedRow {
        override val key: String get() = "h/$handle"
    }

    /** A post's line above its cells: how many files, to open it or delete it. */
    private data class SavedPostBar(
        val postId: String,
        val files: Int,
        val appBytes: Long
    ) : SavedRow {
        override val key: String get() = "p/$postId"
    }

    /** One saved media. [thumb] is a picture to draw at the cell's size, null when a video has no still. */
    private data class SavedCell(
        val postId: String,
        val position: Int,
        val thumb: String?,
        val video: Boolean
    ) : SavedRow {
        override val key: String get() = "c/$postId/$position"
    }

    private data class SavedMediaUiState(
        val loading: Boolean = true,
        val rows: List<SavedRow> = emptyList(),
        val fileCount: Int = 0,
        val appBytes: Long = 0
    )

    /** One file of the app's folder with its size, read once. */
    private data class Auto(val handle: String, val postId: String, val position: Int, val path: String, val bytes: Long)

    private val VIDEO_EXTENSIONS = setOf("mp4", "m4v", "mov", "webm", "mkv")

    /**
     * The rows of the grid, pure. Accounts with the most files first, then each account's posts newest first (post ids
     * are snowflakes, so a bigger id is a later post), the media of a post in order. [stills] are keyed
     * "postId/position" like the store's own index.
     */
    private fun build(auto: List<Auto>, stills: Map<String, String>): SavedMediaUiState {
        class Item(val handle: String, val cell: SavedCell, val appBytes: Long?)

        val items = LinkedHashMap<String, Item>()
        for (file in auto) {
            val video = file.path.substringAfterLast('.').lowercase() in VIDEO_EXTENSIONS
            val thumb = if (video) stills["${file.postId}/${file.position}"] else file.path
            val cell = SavedCell(file.postId, file.position, thumb, video)
            items[cell.key] = Item(file.handle, cell, file.bytes)
        }

        val rows = ArrayList<SavedRow>(items.size + 16)
        items.values.groupBy { it.handle }
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, List<Item>>> { it.value.size }.thenBy { it.key })
            .forEach { (handle, ofAccount) ->
                val copies = ofAccount.filter { it.appBytes != null }
                rows += SavedHeader(handle, ofAccount.size, copies.size, copies.sumOf { it.appBytes ?: 0L })
                ofAccount
                    .sortedWith(
                        compareByDescending<Item> { it.cell.postId.length }
                            .thenByDescending { it.cell.postId }
                            .thenBy { it.cell.position }
                    )
                    .groupBy { it.cell.postId }
                    .forEach { (postId, ofPost) ->
                        rows += SavedPostBar(postId, ofPost.size, ofPost.sumOf { it.appBytes ?: 0L })
                        ofPost.forEach { rows += it.cell }
                    }
            }
        return SavedMediaUiState(
            loading = false,
            rows = rows,
            fileCount = items.size,
            appBytes = items.values.sumOf { it.appBytes ?: 0L }
        )
    }
}
