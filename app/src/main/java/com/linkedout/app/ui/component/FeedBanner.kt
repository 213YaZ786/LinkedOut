package com.linkedout.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.sp
import com.linkedout.app.ui.glass.LocalGlassBackdrop
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A feed screen's top row (Home, Accounts), one header row as Bluesky's HomeHeaderLayoutMobile: a slot, the centre
 * taking the rest (flex_1, align_center), a slot. The centre is the feed pill (or the plain title) with the subtitle
 * beside it, 12 dp apart, the pair centred between the two action slots (both slots keep their room, so the pair is
 * centred on the screen). A pair too long for the line drops the subtitle under the centre instead of cutting it.
 * https://github.com/bluesky-social/social-app/blob/main/src/view/com/home/HomeHeaderLayoutMobile.tsx#HomeHeaderLayoutMobile
 * https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/ui/component/ScreenBanner.kt#ScreenBanner
 * not ported: HomeHeaderLayoutMobile (React Native's file; FeedBanner is its row in Compose, the centre open to a composable)
 * not ported: ScreenBanner (the title row stays in its own file; FeedBanner is the same row with a free centre)
 * not ported: BannerAction (used as it is), the title's two lines with TextAutoSize (one line, ellipsis), BannerGap and
 * BannerActionSize (private there, copied below), StatusBarFade, the logo and the hide on scroll (Bluesky's header only)
 */
@Composable
fun FeedBanner(
    centre: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = BannerActionSize + 16.dp)
            .padding(horizontal = 16.dp, vertical = BannerGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Both action slots keep their room, so the pair stays centred.
        Box(Modifier.size(BannerActionSize), contentAlignment = Alignment.Center) { leading?.invoke() }
        Layout(
            content = {
                centre()
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
        ) { measurables, constraints ->
            val gap = 12.dp.roundToPx()
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val centreItem = measurables[0].measure(loose)
            val subtitleText = measurables.getOrNull(1)?.measure(loose)
            if (subtitleText == null || centreItem.width + gap + subtitleText.width <= constraints.maxWidth) {
                // One line: the pill and the age centred on each other (a pill has no text baseline to share), the pair centred.
                val height = maxOf(centreItem.height, subtitleText?.height ?: 0)
                val width = centreItem.width + (subtitleText?.let { gap + it.width } ?: 0)
                layout(constraints.maxWidth, height) {
                    val start = (constraints.maxWidth - width) / 2
                    centreItem.place(start, (height - centreItem.height) / 2)
                    subtitleText?.place(start + centreItem.width + gap, (height - subtitleText.height) / 2)
                }
            } else {
                // Too narrow: the subtitle under the centre, each centred.
                val under = 4.dp.roundToPx()
                layout(constraints.maxWidth, centreItem.height + under + subtitleText.height) {
                    centreItem.place((constraints.maxWidth - centreItem.width) / 2, 0)
                    subtitleText.place((constraints.maxWidth - subtitleText.width) / 2, centreItem.height + under)
                }
            }
        }
        Box(Modifier.size(BannerActionSize), contentAlignment = Alignment.Center) { trailing?.invoke() }
    }
}

/**
 * FeedBanner on one line always: the pill and the age side by side, never the age under. The pill keeps its icon,
 * chevron and label (a label over 12 characters ends with an ellipsis); the age gives way first, through
 * [subtitleForms] from the longest to the shortest ("Updated 3d ago", "3d ago", "3d"); only when the shortest age
 * still does not fit is the label cut further, to 3 characters at the least. Both action slots keep their room.
 * [pill] draws the centre for the label it is given; its candidates are composed to be measured, with
 * [LocalBannerProbe] true, so a menu opened from the pill belongs in `if (!LocalBannerProbe.current)`.
 *
 * The pair is measured as candidates (SubcomposeLayout), as FeedBannerDraft in the draft of point 22.
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/ui/component/FeedBanner.kt#FeedBanner
 * not ported: the first FeedBanner's under-the-centre branch (the line is one line always), its subtitle in two lines
 * (one line, the age is shortened instead)
 */
@Composable
fun FeedBanner(
    label: String,
    subtitleForms: List<String>,
    pill: @Composable (shownLabel: String) -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = BannerActionSize + 16.dp)
            .padding(horizontal = 16.dp, vertical = BannerGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Both action slots keep their room, so the pair stays centred.
        Box(Modifier.size(BannerActionSize), contentAlignment = Alignment.Center) { leading?.invoke() }
        SubcomposeLayout(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) { constraints ->
            val gap = 12.dp.roundToPx()
            val unbounded = Constraints()
            val centres = HashMap<String, Int>()
            val subtitles = HashMap<String, Placeable>()
            // Each candidate is composed and measured once, at the width it asks for.
            fun centreWidth(text: String) = centres.getOrPut(text) {
                subcompose("centre:$text") {
                    CompositionLocalProvider(LocalBannerProbe provides true) { pill(text) }
                }.first().measure(unbounded).width
            }
            fun subtitleText(text: String) = subtitles.getOrPut(text) {
                subcompose("subtitle:$text") {
                    Text(
                        text,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false
                    )
                }.first().measure(unbounded)
            }

            val chosen = chooseBanner(label, subtitleForms, constraints.maxWidth, gap, ::centreWidth) { subtitleText(it).width }
            // The pill that stays is composed on its own, the only one that may open a menu.
            val centreChosen = subcompose("centre") { pill(chosen.label) }.first().measure(unbounded)
            val subtitleChosen = chosen.age?.let { subtitleText(it) }
            val height = maxOf(centreChosen.height, subtitleChosen?.height ?: 0)
            val width = centreChosen.width + (subtitleChosen?.let { gap + it.width } ?: 0)
            layout(constraints.maxWidth, height) {
                val start = ((constraints.maxWidth - width) / 2).coerceAtLeast(0)
                centreChosen.place(start, (height - centreChosen.height) / 2)
                subtitleChosen?.place(start + centreChosen.width + gap, (height - subtitleChosen.height) / 2)
            }
        }
        Box(Modifier.size(BannerActionSize), contentAlignment = Alignment.Center) { trailing?.invoke() }
    }
}

/** True while a banner pill is only being measured as a candidate (see FeedBanner). */
val LocalBannerProbe = staticCompositionLocalOf { false }

/** The label and the age the banner shows; [age] is null when there is no age to show. */
internal data class BannerChoice(val label: String, val age: String?)

/**
 * Which label and which age fit on the one line, from the widths: widths in, label and age out, so it is tested on the
 * JVM. The age gives way first, the longest form that fits; the label only when the shortest age is not enough.
 */
internal fun chooseBanner(
    label: String,
    subtitle: List<String>,
    maxWidth: Int,
    gap: Int,
    centreWidth: (String) -> Int,
    subtitleWidth: (String) -> Int
): BannerChoice {
    val cuts = labelCuts(label)
    if (subtitle.isEmpty()) {
        return BannerChoice(cuts.firstOrNull { centreWidth(it) <= maxWidth } ?: cuts.last(), null)
    }
    fun fits(text: String, age: String) = centreWidth(text) + gap + subtitleWidth(age) <= maxWidth
    // The age gives way first; the label only when the shortest age is not enough.
    val age = subtitle.firstOrNull { fits(cuts.first(), it) } ?: subtitle.last()
    val text = if (fits(cuts.first(), age)) cuts.first() else cuts.firstOrNull { fits(it, age) } ?: cuts.last()
    return BannerChoice(text, age)
}

/**
 * The label as the pill shows it, longest first: a label over 12 characters ends with an ellipsis ("Journalists…"), then
 * cut shorter by one character, down to its first 3 ("All" is never cut).
 */
internal fun labelCuts(label: String): List<String> {
    fun cut(length: Int) = if (label.length <= length) label else label.take(length).trimEnd() + "…"
    return (LabelCap downTo LabelMin).map { cut(it) }.distinct()
}

private const val LabelCap = 12
private const val LabelMin = 3

internal const val FORMAT_ABBREV_RELATIVE = 0x40000
internal const val FORMAT_ABBREV_ALL = 0x80000

/**
 * The age in forms of one meaning, the longest first: "Updated 3d ago", "3d ago", "3d" (just now: "Updated just now",
 * "just now", "now"), each one asked of getRelativeTimeSpanString with its flag.
 */
fun ageForms(age: String): List<String> =
    listOf(0, FORMAT_ABBREV_RELATIVE, FORMAT_ABBREV_ALL).map { getRelativeTimeSpanString(age, it) }

/**
 * DateUtils.getRelativeTimeSpanString(time, now, minResolution, flags): "Updated 3d ago" in full; FORMAT_ABBREV_RELATIVE
 * drops the first word ("3d ago"), FORMAT_ABBREV_ALL the last one too ("3d"). [age] is the app's own short age ("3d",
 * "5h", "12m"); an empty one or "now" is "just now".
 * https://github.com/aosp-mirror/platform_frameworks_base/blob/main/core/java/android/text/format/DateUtils.java#getRelativeTimeSpanString
 * not ported: the Context overloads (a date and a time of day past a day, formatDateRange, the year, the preposition: the
 * banner only says how long ago), the future ("In 42 minutes": an update is never ahead), RelativeDateTimeFormatter's
 * locales
 */
fun getRelativeTimeSpanString(age: String, flags: Int): String {
    val abbreviated = flags and (FORMAT_ABBREV_RELATIVE or FORMAT_ABBREV_ALL) != 0
    val all = flags and FORMAT_ABBREV_ALL != 0
    return if (age.isEmpty() || age == "now") {
        if (all) "now" else if (abbreviated) "just now" else "Updated just now"
    } else {
        if (all) age else if (abbreviated) "$age ago" else "Updated $age ago"
    }
}

/** Space the banner leaves above and below itself, as ScreenBanner's. */
private val BannerGap = 8.dp

/** Both slots of the banner, as ScreenBanner's. */
private val BannerActionSize = 44.dp

private val SearchBarAsTopBarPadding = 8.dp
private val AppBarWithSearchHorizontalPadding = 4.dp
private val AppBarWithSearchVerticalPadding = 4.dp

/**
 * Home's and Accounts' top (requests.md point 41): one glass capsule the width of the page, nothing under it.
 * AppBarWithSearch's layout: a Row of the navigation icon, the input field taking the rest and centred, the actions at
 * the end, the field padded by SearchBarAsTopBarPadding and AppBarWithSearchVerticalPadding, the icons by
 * AppBarWithSearchHorizontalPadding; here on the app's glass (FloatingPane) instead of Material's Surface. The capsule is
 * an item of a list that records the backdrop, so it is drawn with none (MenuPill's pitfall).
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/SearchBar.kt#AppBarWithSearch
 * not ported: AppBarWithSearch's SearchBarState, the expanded search (ExpandedFullScreenContainedSearchBar), the colours and
 * animateColorAsState of the scroll, tonal and shadow elevation, scrollBehavior and windowInsets (the page has its own
 * insets), SearchBarImpl (the input field is the page's own content)
 */
@Composable
fun AppBarWithSearch(
    inputField: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit)? = null,
    shape: Shape = CircleShape,
    contentPadding: PaddingValues = PaddingValues(all = 0.dp)
) {
    CompositionLocalProvider(LocalGlassBackdrop provides null) {
        FloatingPane(shape = shape, modifier = modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(contentPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                navigationIcon?.let {
                    Box(Modifier.padding(start = AppBarWithSearchHorizontalPadding)) { it() }
                }
                Box(modifier = Modifier.weight(1f)) {
                    Box(
                        Modifier
                            .padding(horizontal = SearchBarAsTopBarPadding, vertical = AppBarWithSearchVerticalPadding)
                            .align(Alignment.Center)
                    ) { inputField() }
                }
                actions?.let {
                    Box(Modifier.padding(end = AppBarWithSearchHorizontalPadding)) {
                        Row(horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically, content = it)
                    }
                }
            }
        }
    }
}

/**
 * The folder switch, the capsule's navigation icon: the folder icon, its name, a chevron, the whole of it the tap that
 * opens the GlassMenu (composed beside it by the screen, in the same Box, as MenuPill's). The name is cut to the room the
 * capsule leaves it (one line, ellipsis).
 * Also AppBarWithSearch's navigationIcon slot, https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/SearchBar.kt#AppBarWithSearch
 */
@Composable
fun FolderSwitch(
    icon: ImageVector,
    trailingIcon: ImageVector,
    label: String,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .clip(CircleShape)
            .clickable(onClickLabel = "Choose which folder to show", onClick = onClick)
            .semantics {
                contentDescription = label
                stateDescription = if (expanded) "Expanded" else "Collapsed"
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(trailingIcon, contentDescription = null, modifier = Modifier.size(20.dp))
    }
}

/**
 * The capsule's words: [hints] from the longest to the shortest ("Search 12 accounts or open a handle", "Search 12
 * accounts"). The text shrinks first (TextAutoSize down to 14 sp), then drops to the next shorter form when it still
 * overflows, as FeedBanner's subtitleForms do.
 */
@Composable
private fun SearchHint(hints: List<String>, modifier: Modifier = Modifier) {
    var form by remember(hints) { mutableStateOf(0) }
    val style = MaterialTheme.typography.bodyLarge
    BasicText(
        hints[form],
        modifier = modifier,
        style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = style.fontSize, stepSize = 0.5.sp),
        onTextLayout = { if (it.hasVisualOverflow && form < hints.lastIndex) form++ }
    )
}

/** Home's input field: "Search", the whole row the tap that opens search. */
@Composable
fun SearchCapsuleHint(searchIcon: ImageVector, hints: List<String>, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = hints[0], onClick = onClick).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(searchIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        SearchHint(hints)
    }
}

/** Accounts' input field: the real field, its hint the shrinking [hints]. */
@Composable
fun SearchCapsuleField(
    searchIcon: ImageVector,
    value: String,
    onValueChange: (String) -> Unit,
    hints: List<String>,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        decorationBox = { field ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(searchIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) SearchHint(hints)
                    field()
                }
            }
        }
    )
}
