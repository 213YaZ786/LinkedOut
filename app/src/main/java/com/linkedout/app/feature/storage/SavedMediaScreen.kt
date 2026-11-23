package com.linkedout.app.feature.storage

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.linkedout.app.ui.component.ZoneAlertDialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.linkedout.app.feature.BarAction
import com.linkedout.app.navigation.LocalReadableInset
import com.linkedout.app.ui.component.BarTitle
import com.linkedout.app.ui.component.BoldButton
import com.linkedout.app.ui.component.BoldIconButton
import com.linkedout.app.ui.component.FloatingTopBar
import com.linkedout.app.ui.component.QuietButton
import com.linkedout.app.ui.component.plus
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.linkedout.app.ui.icon.LinkedOutIcons
import org.koin.androidx.compose.koinViewModel

/**
 * What the app has saved so posts can be read with no network.
 *
 * Ported from MTGA's SavedMediaScreen
 * (https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/media/SavedMediaScreen.kt#SavedMediaScreen).
 * A lazy grid of small pictures, an account's heading above its media: with thousands of saved videos only the
 * cells in view exist, each picture is decoded at the size of its cell (Coil reads the size from the layout), and
 * a video shows its saved still, never a frame decoded for the grid. A tap opens the post.
 *
 * The copies sit in the app's own folder, which Android 11 and later keep out of the Files app. The account's
 * "Delete copies" and the post's bin delete part of it, the bar's bin (LinkedOut's own, MTGA has none) deletes
 * every file after a confirmation.
 * not ported: the manual saves note (LinkedOut has no saves made by hand, so every copy is the app's own)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedMediaScreen(
    onBack: () -> Unit,
    onOpenPost: (postId: String, handle: String) -> Unit,
    viewModel: SavedMediaViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var confirmAll by remember { mutableStateOf(false) }

    // LinkedOut's previous screen, kept as it was: nothing else in the app clears the offline copies
    // (https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/storage/SavedMediaScreen.kt#L72-L95).
    if (confirmAll) {
        ZoneAlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text("Delete every saved file?") },
            text = {
                Text(
                    "The posts stay. Their pictures and videos are fetched again the next " +
                        "time you look at them, or saved again on the next refresh."
                )
            },
            confirmButton = {
                BoldButton(filled = true, onClick = {
                    confirmAll = false
                    viewModel.deleteAll()
                }) { Text("Delete") }
            },
            dismissButton = { QuietButton(onClick = { confirmAll = false }) { Text("Cancel") } }
        )
    }

    // The bar folds away going down and returns going up, the grid full screen, as MTGA's SavedMediaScreen
    // (https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/media/SavedMediaScreen.kt#SavedMediaScreen).
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // The page's ground is painted under the whole app, see MainActivity.
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            FloatingTopBar(
                scrollBehavior = scrollBehavior,
                title = { BarTitle("Saved media") },
                navigationIcon = { BarAction(LinkedOutIcons.ArrowBack, "Back", onBack) },
                actions = {
                    if (state.rows.isNotEmpty()) {
                        BarAction(LinkedOutIcons.Delete, "Delete all", { confirmAll = true })
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            if (state.rows.isEmpty()) {
                Text(
                    if (state.loading) {
                        "Reading the folder"
                    } else {
                        "Nothing saved yet. Turn on automatic saving in Settings, Media."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp)
                )
                return@Box
            }

            val gridState = rememberLazyGridState()
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(CELL_MIN),
                modifier = Modifier.fillMaxSize(),
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
                        is SavedHeader -> AccountHeader(row) { viewModel.deleteAccount(row.handle) }
                        is SavedPostBar -> PostBar(row, { onOpenPost(row.postId, row.handle) }) { viewModel.deletePost(row.postId) }
                        is SavedCell -> Cell(row) { onOpenPost(row.postId, row.handle) }
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
 * One saved media. Its picture is drawn by Coil at the cell's size through the isolated decoder like every picture
 * of the app. The cell has a shape (a square, as wide as the grid gives) and no text, so nothing in it can break.
 */
@Composable
private fun Cell(cell: SavedCell, onOpen: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onOpen),
        contentAlignment = Alignment.Center
    ) {
        if (cell.thumb != null) {
            AsyncImage(
                model = cell.thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
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
// SMS's TileGap (PersonActions.kt), the gap Connection check and Folders took: 4 dp let the cells touch.
private val GAP = 10.dp
