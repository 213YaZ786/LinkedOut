package com.linkedout.app.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.linkedout.app.core.model.JobCard
import com.linkedout.app.core.model.JobDetail
import com.linkedout.app.navigation.LocalReadableInset
import com.linkedout.app.ui.component.Avatar
import com.linkedout.app.ui.component.BannerAction
import com.linkedout.app.ui.component.BoldButton
import com.linkedout.app.ui.component.FloatingPane
import com.linkedout.app.ui.component.GlassSearchField
import com.linkedout.app.ui.component.ScreenBanner
import com.linkedout.app.ui.component.Zone
import com.linkedout.app.ui.component.ZoneGap
import com.linkedout.app.ui.component.segmentedShape
import com.linkedout.app.ui.icon.LinkedOutIcons
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// The menu's pieces, shared with HomeDraftScreenshotTest (point 10: Home's feeds in the Jobs menu), so both drafts
// draw one look.

// MenuDefaults.GroupSpacing = SegmentedMenuTokens.SegmentedGap, the gap between two groups.
// https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/MenuDefaults.kt#GroupSpacing
internal val GroupSpacing: Dp = 2.dp

internal val MenuWidth: Dp = 280.dp

// The screen with the menu over it: a scrim on the screen, then the menu under the pill that opened it (a Popup in the
// app, Menu.kt's DropdownMenu). [anchor] is the point under the pill's middle, in the window; the menu is kept 12 dp from the
// screen's sides.
// https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/Menu.kt#DropdownMenu
@Composable
internal fun MenuOverScreen(
    open: Boolean,
    anchor: IntOffset,
    screen: @Composable () -> Unit,
    menu: @Composable ColumnScope.() -> Unit
) {
    var origin by remember { mutableStateOf(IntOffset.Zero) }
    var width by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    Box(
        Modifier.fillMaxSize().onGloballyPositioned {
            val at = it.positionInRoot()
            origin = IntOffset(at.x.toInt(), at.y.toInt())
            width = it.size.width
        }
    ) {
        screen()
        if (open) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)))
            val menuWidth = with(density) { MenuWidth.roundToPx() }
            val margin = with(density) { 12.dp.roundToPx() }
            val gap = with(density) { 8.dp.roundToPx() }
            // The menu is centred under the pill: [anchor].x is the pill's middle.
            val x = (anchor.x - origin.x - menuWidth / 2).coerceIn(margin, (width - menuWidth - margin).coerceAtLeast(margin))
            Column(
                Modifier.offset { IntOffset(x, anchor.y - origin.y + gap) }.width(MenuWidth),
                verticalArrangement = Arrangement.spacedBy(GroupSpacing),
                content = menu
            )
        }
    }
}

// DropdownMenuGroup: a container for a group of items, its shape by the group's place in the menu (MenuDefaults.groupShape:
// leading, middle, trailing, or standalone for one group; here segmentedShape, ExtraLarge outside and ExtraSmall facing the
// next group), drawn as a glass pane. The rows fill it edge to edge (the user, 2026-11-07, "bordures blanche autour du vert
// de selection nul"): no padding inside the group, and the group's shape clips the rows, so a selected row follows the
// group's corners where it touches them and is square in the middle.
// https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/Menu.kt#DropdownMenuGroup
// not ported: DropdownMenuGroupContentPadding (2 dp above and below, the very margin the user refused), the shape morph on
// hover (shapeByInteraction, the inactive shape, the interaction source), the tonal and shadow elevation and the border (the
// glass pane draws its own rim and shadow)
@Composable
internal fun DropdownMenuGroup(index: Int, count: Int, content: @Composable () -> Unit) {
    val shape: Shape = segmentedShape(index, count)
    FloatingPane(shape = shape, modifier = Modifier.fillMaxWidth()) {
        // Robolectric draws no blur: this veil stands for the frosted body the phone's glass gives, so the list behind does
        // not cross the words in the picture.
        Column(Modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)) {
            content()
        }
    }
}

// DropdownMenuItemContent's Row: the whole row is the tap target, the container colour fills the item's whole width and
// height, flush to the group's edges, the content sits 16 dp from the side and 12 dp above and below, centred, at least 48 dp
// high (MenuListItemContainerHeight). The shape is the group's, not the row's.
// https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/Menu.kt#DropdownMenuItemContent
@Composable
internal fun MenuItemRow(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

// SelectableDropdownMenuItem: a choice among several. Selected, the container is the theme's selected container
// (StandardMenuTokens.ItemSelectedContainerColor is tertiaryContainer; the app's accent is its primary, so primaryContainer
// with label and icon onPrimaryContainer, as the chosen pill "Filters, 2") and the leading
// icon is the selectedLeadingIcon, a check; the others keep that icon's width blank so the labels line up.
// Role.RadioButton as the source.
// https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/Menu.kt#SelectableDropdownMenuItem
// not ported: supportingText, trailingContent, the colours and shapes parameters (MenuItemShapes, SelectableMenuItemColors:
// one standard set from the theme), enabled, interactionSource
@Composable
internal fun SelectableDropdownMenuItem(selected: Boolean, onClick: () -> Unit, text: String) {
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    MenuItemRow(selected = selected, onClick = onClick, modifier = Modifier.semantics { role = Role.RadioButton }) {
        if (selected) {
            Icon(LinkedOutIcons.Check, contentDescription = null, modifier = Modifier.size(24.dp), tint = content)
        } else {
            Spacer(Modifier.size(24.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, color = content, modifier = Modifier.weight(1f))
    }
}

/**
 * DRAFT for the user's yes (requests.md points 7 and 8), nothing of app/src/main changes: the Jobs screen with
 * one Filters pill that opens a grouped glass menu, the result count and the cards' badges; the offer page with the
 * company as a link, the applicants and Save. The pieces are the screens' own, copied from
 * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/jobs/JobsScreen.kt#JobsScreen
 * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/jobs/JobsScreen.kt#JobCardZone
 * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/feature/jobs/JobDetailScreen.kt#Head
 * The menu is Material 3 Expressive's grouped menu drawn in our glass (the user refused seven chips, 2026-11-07), inline
 * for the picture where the app will open a Popup:
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/Menu.kt#DropdownMenuGroup
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/Menu.kt#SelectableDropdownMenuItem
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/MenuDefaults.kt#GroupSpacing
 * The icons: Save
 * https://github.com/google/material-design-icons/blob/master/symbols/android/bookmark/materialsymbolsoutlined/bookmark_24px.xml
 * Filters
 * https://github.com/google/material-design-icons/blob/master/symbols/android/tune/materialsymbolsoutlined/tune_24px.xml
 * and the chevron
 * https://github.com/google/material-design-icons/blob/master/symbols/android/chevron_right/materialsymbolsoutlined/chevron_right_24px.xml
 * not ported: JobsScreen's error, loading, hint and "More offers" rows (not part of the draft)
 * not ported: JobDetailScreen's loading and error rows, and its ViewModel (fixed data here)
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class JobsDraftScreenshotTest : ScreenshotBase() {

    private val cards = listOf(
        Triple(
            JobCard(
                id = "1",
                title = "Ingénieur en développement de micropuces pour analyse biomédicale H/F",
                company = "Inserm",
                location = "Marseille",
                url = ""
            ),
            "5 days ago",
            "Actively hiring"
        ),
        Triple(
            JobCard(
                id = "2",
                title = "Ingénieur R&D Instruments Chirurgicaux",
                company = "eCential Robotics",
                location = "Gières",
                url = ""
            ),
            "5 days ago",
            "Be an early applicant"
        ),
        Triple(
            JobCard(
                id = "3",
                title = "Senior Research Engineer Surgical Algorithms",
                company = "Cochlear",
                location = "Vallauris",
                url = ""
            ),
            "5 days ago",
            null
        )
    )

    private val detail = JobDetail(
        id = "1",
        title = "Ingénieur en développement de micropuces pour analyse biomédicale H/F",
        company = "Inserm",
        location = "Marseille",
        postedAgo = "5 days ago",
        description = "L'Inserm recrute un ingénieur pour concevoir et caractériser des micropuces dédiées à l'analyse " +
            "biomédicale, au sein d'une équipe de recherche pluridisciplinaire.\n\n" +
            "Vous participerez à la conception des dispositifs, à leur fabrication en salle blanche, aux essais sur " +
            "échantillons biologiques et à la rédaction des résultats avec les chercheurs de l'équipe.",
        criteria = listOf(
            "Seniority level" to "Entry level",
            "Employment type" to "Full-time",
            "Industries" to "Research Services"
        ),
        url = ""
    )

    @Test
    fun jobs() = capture("jobs_draft") { JobsWithMenu(FiltersMenu.Closed) }

    @Test
    fun filters() = capture("jobs_filters_draft") { JobsWithMenu(FiltersMenu.Filters) }

    @Test
    fun filtersDate() = capture("jobs_filters_date_draft") { JobsWithMenu(FiltersMenu.Date) }

    @Test
    fun detail() = capture("job_detail_draft") { JobDetailDraft() }

    private enum class FiltersMenu { Closed, Filters, Date }

    // The screen with the menu over it: a scrim on the list, then the menu under the Filters pill (a Popup in the app).
    @Composable
    private fun JobsWithMenu(menu: FiltersMenu) {
        var anchor by remember { mutableStateOf(IntOffset.Zero) }
        MenuOverScreen(open = menu != FiltersMenu.Closed, anchor = anchor, screen = { JobsDraft(onFiltersPill = { anchor = it }) }) {
            if (menu == FiltersMenu.Filters) FiltersMenuContent() else DateMenuContent()
        }
    }

    @Composable
    private fun JobsDraft(onFiltersPill: (IntOffset) -> Unit) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = LocalReadableInset.current, end = LocalReadableInset.current, bottom = 16.dp)
        ) {
            item { ScreenBanner(title = "Jobs") }
            item {
                Zone(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = ZoneGap)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassSearchField(
                            value = "ingénieur biomédical",
                            onValueChange = {},
                            placeholder = "Job title, skill or company",
                            modifier = Modifier.fillMaxWidth()
                        )
                        GlassSearchField(
                            value = "France",
                            onValueChange = {},
                            placeholder = "City, region or country",
                            modifier = Modifier.fillMaxWidth()
                        )
                        BoldButton(onClick = {}, filled = true, modifier = Modifier.fillMaxWidth()) {
                            Icon(LinkedOutIcons.Search, contentDescription = null)
                            Text("Search", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
            item { FilterPills(filtersSet = 3, onFiltersPill = onFiltersPill) }
            item {
                Text(
                    "Over 2,000 offers",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp)
                )
            }
            cards.forEach { (card, posted, badge) ->
                item(key = card.id) { JobCardZone(card, posted, badge) }
            }
        }
    }

    // Two centred glass pills in place of the seven chips: Filters (in the accent while filters are set, with their count) and
    // Saved. [onFiltersPill] gives the point under the middle of the Filters pill, where the menu opens.
    @Composable
    private fun FilterPills(filtersSet: Int, onFiltersPill: (IntOffset) -> Unit) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            FloatingPane(
                shape = CircleShape,
                accent = filtersSet > 0,
                onClick = {},
                modifier = Modifier.onGloballyPositioned {
                    val at = it.positionInRoot()
                    onFiltersPill(IntOffset((at.x + it.size.width / 2).toInt(), (at.y + it.size.height).toInt()))
                }
            ) {
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Tune, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(if (filtersSet > 0) "Filters, $filtersSet" else "Filters", style = MaterialTheme.typography.labelLarge)
                }
            }
            FloatingPane(shape = CircleShape, onClick = {}) {
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Bookmark, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("Saved", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    // The menu: three groups apart by GroupSpacing. A row that has choices shows its value and a chevron and opens them in place.
    @Composable
    private fun FiltersMenuContent() {
        DropdownMenuGroup(index = 0, count = 3) {
            ValueMenuItem("Date posted", "Past week")
            ValueMenuItem("Workplace", "Remote")
            ValueMenuItem("Experience", "Any")
            ValueMenuItem("Job type", "Any")
        }
        DropdownMenuGroup(index = 1, count = 3) {
            CheckableDropdownMenuItem(checked = true, onCheckedChange = {}, text = "Easy Apply")
            CheckableDropdownMenuItem(checked = false, onCheckedChange = {}, text = "Under 10 applicants")
        }
        DropdownMenuGroup(index = 2, count = 3) {
            DropdownMenuItem(text = "Clear filters", color = MaterialTheme.colorScheme.error, onClick = {})
        }
    }

    // The submenu in place of the menu: the row it came from as a header with Back, then its choices, one selected.
    @Composable
    private fun DateMenuContent() {
        DropdownMenuGroup(index = 0, count = 2) {
            DropdownMenuItem(
                text = "Date posted",
                leadingIcon = {
                    Icon(
                        LinkedOutIcons.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                style = MaterialTheme.typography.titleSmall,
                onClick = {}
            )
        }
        DropdownMenuGroup(index = 1, count = 2) {
            listOf("Any time", "Past 24 hours", "Past week", "Past month").forEach { choice ->
                SelectableDropdownMenuItem(selected = choice == "Past week", onClick = {}, text = choice)
            }
        }
    }

    // DropdownMenuItem: text and an optional leading icon, the colours from the theme (label onSurface).
    @Composable
    private fun DropdownMenuItem(
        text: String,
        onClick: () -> Unit,
        color: Color = MaterialTheme.colorScheme.onSurface,
        leadingIcon: (@Composable () -> Unit)? = null,
        style: TextStyle = MaterialTheme.typography.bodyLarge
    ) {
        MenuItemRow(selected = false, onClick = onClick, modifier = Modifier.semantics { role = Role.Button }) {
            leadingIcon?.invoke()
            Text(text, style = style, color = color, modifier = Modifier.weight(1f))
        }
    }

    // A row with a choice behind it: the label, its current value in onSurfaceVariant (the trailing content) and a chevron.
    @Composable
    private fun ValueMenuItem(label: String, value: String) {
        MenuItemRow(selected = false, onClick = {}, modifier = Modifier.semantics { role = Role.Button }) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    // CheckableDropdownMenuItem (Menu.kt, next to SelectableDropdownMenuItem): an on and off setting drawn as the source does
    // (selected = checked, onClick toggles, selectedLeadingIcon = checkedLeadingIcon): no box; on, the check at the start and the
    // row in the selected container, as the chosen row of a submenu (and iOS 26's menu toggles); off, the label alone with the
    // same 24 dp slot blank so the labels line up. Role.Checkbox as the source.
    // not ported: leadingIcon, trailingContent, supportingText, enabled, colours and shapes, interactionSource
    @Composable
    private fun CheckableDropdownMenuItem(checked: Boolean, onCheckedChange: (Boolean) -> Unit, text: String) {
        val content = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        MenuItemRow(
            selected = checked,
            onClick = { onCheckedChange(!checked) },
            modifier = Modifier.semantics { role = Role.Checkbox }
        ) {
            if (checked) {
                Icon(LinkedOutIcons.Check, contentDescription = null, modifier = Modifier.size(24.dp), tint = content)
            } else {
                Spacer(Modifier.size(24.dp))
            }
            Text(text, style = MaterialTheme.typography.bodyLarge, color = content, modifier = Modifier.weight(1f))
        }
    }

    @Composable
    private fun JobCardZone(card: JobCard, posted: String, badge: String?) {
        Zone(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = ZoneGap),
            onClick = {}
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Avatar(url = card.logoUrl, name = card.company.ifBlank { card.title }, size = 48.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(card.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(card.company, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(card.location, posted).joinToString(", "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // NEW: the badge LinkedIn gives the offer, one line in the accent.
                    badge?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                    }
                }
            }
        }
    }

    @Composable
    private fun JobDetailDraft() {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = LocalReadableInset.current, end = LocalReadableInset.current, bottom = 24.dp)
        ) {
            item {
                ScreenBanner(
                    title = "Offer",
                    leading = { BannerAction(icon = LinkedOutIcons.ArrowBack, label = "Back", onClick = {}) },
                    // NEW: Save, the bookmark.
                    trailing = { BannerAction(icon = Bookmark, label = "Save", onClick = {}) }
                )
            }
            item { Head(detail, applicants = "75 applicants") }
            item { Criteria(detail.criteria) }
            item { Description(detail.description) }
        }
    }

    @Composable
    private fun Head(detail: JobDetail, applicants: String) {
        Zone(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = ZoneGap)) {
            Column(
                Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(detail.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                // NEW: the company is a link to its page, a small zone of its own so that it reads as a tap.
                Zone(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    onClick = {}
                ) {
                    Text(
                        detail.company,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                // NEW: the applicants joined to the place and the age by commas.
                Text(
                    listOfNotNull(detail.location.takeIf { it.isNotBlank() }, detail.postedAgo, applicants).joinToString(", "),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                BoldButton(onClick = {}, filled = true, modifier = Modifier.padding(top = 8.dp)) {
                    Text("View and apply on LinkedIn")
                }
            }
        }
    }

    @Composable
    private fun Criteria(criteria: List<Pair<String, String>>) {
        Zone(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = ZoneGap)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                criteria.forEach { (label, value) ->
                    Column {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }

    @Composable
    private fun Description(text: String) {
        Zone(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = ZoneGap)) {
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
        }
    }

    // bookmark_24px.xml, Material Symbols outlined: viewport 960, the path as the file gives it.
    private val Bookmark: ImageVector = ImageVector.Builder(
        name = "Bookmark",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f
    ).addPath(
        pathData = PathParser().parsePathString(
            "M200,840L200,200Q200,167 223.5,143.5Q247,120 280,120L680,120Q713,120 736.5,143.5Q760,167 760,200L760,840L480,720L200,840ZM280,718L480,632L680,718L680,200Q680,200 680,200Q680,200 680,200L280,200Q280,200 280,200Q280,200 280,200L280,718ZM280,200L280,200Q280,200 280,200Q280,200 280,200L680,200Q680,200 680,200Q680,200 680,200L680,200L480,200L280,200Z"
        ).toNodes(),
        fill = SolidColor(Color.White)
    ).build()

    // tune_24px.xml, Material Symbols outlined: viewport 960, the path as the file gives it.
    private val Tune: ImageVector = ImageVector.Builder(
        name = "Tune",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f
    ).addPath(
        pathData = PathParser().parsePathString(
            "M440,840L440,600L520,600L520,680L840,680L840,760L520,760L520,840L440,840ZM120,760L120,680L360,680L360,760L120,760ZM280,600L280,520L120,520L120,440L280,440L280,360L360,360L360,600L280,600ZM440,520L440,440L840,440L840,520L440,520ZM600,360L600,120L680,120L680,200L840,200L840,280L680,280L680,360L600,360ZM120,280L120,200L520,200L520,280L120,280Z"
        ).toNodes(),
        fill = SolidColor(Color.White)
    ).build()

    // chevron_right_24px.xml, Material Symbols outlined: viewport 960, the path as the file gives it (the file is auto mirrored).
    private val ChevronRight: ImageVector = ImageVector.Builder(
        name = "ChevronRight",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f,
        autoMirror = true
    ).addPath(
        pathData = PathParser().parsePathString("M504,480L320,296L376,240L616,480L376,720L320,664L504,480Z").toNodes(),
        fill = SolidColor(Color.White)
    ).build()
}
