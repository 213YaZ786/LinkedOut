package com.linkedout.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.linkedout.app.ui.glass.LocalGlassBackdrop

// A dropdown in our glass, anchored under the pill that opens it: Material 3 Expressive's grouped menu (DropdownMenuPopup,
// DropdownMenuGroup and its items), drawn as the pictures the user validated in LinkedOut show it (its requests.md points 7
// and 10, JobsDraftScreenshotTest, HomeDraftScreenshotTest). It lives in its own window (a Popup), never in the list that
// holds the pill: a glass pane never floats on the backdrop of a list it is an item of (it closed LinkedOut at launch), so the pill is
// given LocalGlassBackdrop null by its caller and the menu provides null again for what it draws.

// MenuDefaults.GroupSpacing = SegmentedMenuTokens.SegmentedGap, the gap between two groups.
// https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/MenuDefaults.kt#GroupSpacing
internal val GroupSpacing = 2.dp

private val MenuWidth = 280.dp

// Menu.kt's MenuVerticalMargin (48 dp). The horizontal margin is 12 dp, the user's validated picture (Menu.kt has 8 dp).
private val MenuVerticalMargin = 48.dp
private val MenuHorizontalMargin = 12.dp

// The menu is kept 8 dp under its anchor, as the pictures show it.
private val MenuContentOffset = DpOffset(0.dp, 8.dp)

// GlassMenu is DropdownMenuPopup: the menu in a window of its own, a scrim over the screen below it. A tap on the scrim closes
// the menu ([onDismissRequest]), Back steps back ([onBack]: a submenu returns to the menu, the menu closes), the popup being
// focusable so that it takes the key.
// https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/Menu.kt#DropdownMenuPopup
// not ported: DropdownMenuPopup (it is this function, named for the app)
// not ported: DropdownMenu (the expect function with its shape, colour, elevation and border: the groups draw those here)
// not ported: the transform origin and the scale and fade in and out (the pictures show the open menu only), the expanded
// mutable transition state, MenuAnchorPosition (always Below, centred), the shape morph on hover
@Composable
fun GlassMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = onDismissRequest,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!expanded) return
    val density = LocalDensity.current
    val anchor = remember { AnchorPositionProvider() }
    val popupPositionProvider = remember(density) { DropdownMenuPositionProvider(MenuContentOffset, density) }
    val scrim = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)
    // Window-wide, so the scrim covers the screen and the status bar; the menu is placed inside it by the position
    // provider below, not by the window.
    Popup(
        popupPositionProvider = anchor,
        onDismissRequest = onBack,
        properties = PopupProperties(focusable = true, clippingEnabled = false, usePlatformDefaultWidth = false)
    ) {
        CompositionLocalProvider(LocalGlassBackdrop provides null) {
            Layout(
                content = {
                    Column(
                        modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(GroupSpacing),
                        content = content
                    )
                },
                modifier = Modifier
                    .fillMaxSize()
                    .background(scrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "Close",
                        onClick = onDismissRequest
                    )
            ) { measurables, constraints ->
                val verticalMargin = MenuVerticalMargin.roundToPx()
                val horizontalMargin = MenuHorizontalMargin.roundToPx()
                val width = minOf(MenuWidth.roundToPx(), (constraints.maxWidth - 2 * horizontalMargin).coerceAtLeast(0))
                val menu = measurables.first().measure(
                    Constraints(
                        minWidth = width,
                        maxWidth = width,
                        maxHeight = (constraints.maxHeight - 2 * verticalMargin).coerceAtLeast(0)
                    )
                )
                layout(constraints.maxWidth, constraints.maxHeight) {
                    val at = popupPositionProvider.calculatePosition(
                        anchorBounds = anchor.anchorBounds,
                        windowSize = IntSize(constraints.maxWidth, constraints.maxHeight),
                        layoutDirection = layoutDirection,
                        popupContentSize = IntSize(menu.width, menu.height)
                    )
                    menu.place(at)
                }
            }
        }
    }
}

// The window of the popup is the whole screen, at its origin; what it keeps of the position provider's call is the
// anchor's bounds, which the menu is placed against.
private class AnchorPositionProvider : PopupPositionProvider {
    var anchorBounds by mutableStateOf(IntRect.Zero)
        private set

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        this.anchorBounds = anchorBounds
        return IntOffset.Zero
    }
}

// Calculates the position of a Material DropdownMenu: below the anchor, centred on it, above it when there is no room
// below, kept [horizontalMargin] from the window's sides and [verticalMargin] from its top and bottom.
// https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/internal/MenuPosition.kt#DropdownMenuPositionProvider
// not ported: the transformOrigin and onPositionCalculated (no scale animation), dropdownMenuAnchorPosition (always
// Below, centred, so one horizontal candidate), MenuPosition's other alignments (startToAnchorStart, endToAnchorEnd,
// leftToWindowLeft, rightToWindowRight, topToAnchorTop, bottomToAnchorBottom), MenuPositionScopeImpl
internal data class DropdownMenuPositionProvider(
    val contentOffset: DpOffset,
    val density: Density,
    val verticalMargin: Int = with(density) { MenuVerticalMargin.roundToPx() },
    val horizontalMargin: Int = with(density) { MenuHorizontalMargin.roundToPx() }
) : PopupPositionProvider {

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        // The menu's centre on the anchor's centre.
        val centerToAnchorCenter = anchorBounds.left + anchorBounds.width / 2 - popupContentSize.width / 2
        val xCandidates = listOf(centerToAnchorCenter)
        val topToAnchorBottom = anchorBounds.top + anchorBounds.height
        val bottomToAnchorTop = anchorBounds.top - popupContentSize.height
        val centerToAnchorTop = anchorBounds.top - popupContentSize.height / 2
        val topToWindowTop = 0
        val bottomToWindowBottom = windowSize.height - popupContentSize.height
        val yCandidates = listOf(
            topToAnchorBottom,
            bottomToAnchorTop,
            centerToAnchorTop,
            if (anchorBounds.center.y < windowSize.height / 2) topToWindowTop else bottomToWindowBottom
        )
        return positioningLogic(xCandidates, yCandidates, windowSize, popupContentSize, layoutDirection)
    }

    private fun positioningLogic(
        xCandidates: List<Int>,
        yCandidates: List<Int>,
        windowSize: IntSize,
        popupContentSize: IntSize,
        layoutDirection: LayoutDirection
    ): IntOffset {
        val contentOffsetX = with(density) {
            contentOffset.x.roundToPx() * (if (layoutDirection == LayoutDirection.Ltr) 1 else -1)
        }
        val contentOffsetY = with(density) { contentOffset.y.roundToPx() }

        var x = 0
        for (index in xCandidates.indices) {
            val xCandidate = xCandidates[index] + contentOffsetX
            if (
                xCandidate >= horizontalMargin &&
                xCandidate + popupContentSize.width <= windowSize.width - horizontalMargin
            ) {
                x = xCandidate
                break
            }
            if (index == xCandidates.lastIndex) {
                x = if (popupContentSize.width >= windowSize.width - 2 * horizontalMargin) {
                    Alignment.CenterHorizontally.align(
                        size = popupContentSize.width,
                        space = windowSize.width,
                        layoutDirection = layoutDirection
                    )
                } else {
                    xCandidate.coerceIn(horizontalMargin, windowSize.width - horizontalMargin - popupContentSize.width)
                }
                break
            }
        }

        var y = 0
        for (index in yCandidates.indices) {
            val yCandidate = yCandidates[index] + contentOffsetY
            if (
                yCandidate >= verticalMargin &&
                yCandidate + popupContentSize.height <= windowSize.height - verticalMargin
            ) {
                y = yCandidate
                break
            }
            if (index == yCandidates.lastIndex) {
                y = if (popupContentSize.height >= windowSize.height - 2 * verticalMargin) {
                    Alignment.CenterVertically.align(size = popupContentSize.height, space = windowSize.height)
                } else {
                    yCandidate.coerceIn(verticalMargin, windowSize.height - verticalMargin - popupContentSize.height)
                }
                break
            }
        }

        return IntOffset(x, y)
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
        // Over the dimmed screen in a window of its own there is nothing to blur: the body is the theme's high container,
        // as in the pictures, and the pane draws the rim and the shadow.
        Column(Modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)) {
            content()
        }
    }
}

// DropdownMenuItemContent's Row: the whole row is the tap target, the container colour fills the item's whole width and
// height, flush to the group's edges, the content sits 16 dp from the side and 12 dp above and below, centred, at least 48 dp
// high (MenuListItemContainerHeight). The shape is the group's, not the row's. [interaction] is the row's click, selection or
// toggle, with its role and state for TalkBack.
// https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/Menu.kt#DropdownMenuItemContent
@Composable
internal fun DropdownMenuItemContent(
    selected: Boolean,
    interaction: Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(container)
            .then(interaction)
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
// Role.RadioButton as the source. The pick ticks.
// https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/Menu.kt#SelectableDropdownMenuItem
// not ported: supportingText, trailingContent, the colours and shapes parameters (MenuItemShapes, SelectableMenuItemColors:
// one standard set from the theme), enabled, interactionSource
@Composable
internal fun SelectableDropdownMenuItem(selected: Boolean, onClick: () -> Unit, text: String) {
    val haptics = rememberHaptics()
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    DropdownMenuItemContent(
        selected = selected,
        interaction = Modifier.selectable(
            selected = selected,
            role = Role.RadioButton,
            onClick = {
                haptics.tick()
                onClick()
            }
        )
    ) {
        if (selected) {
            Icon(MenuIcons.Check, contentDescription = null, modifier = Modifier.size(24.dp), tint = content)
        } else {
            Spacer(Modifier.size(24.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, color = content, modifier = Modifier.weight(1f))
    }
}

// CheckableDropdownMenuItem (Menu.kt, next to SelectableDropdownMenuItem): an on and off setting drawn as the source does
// (selected = checked, onClick toggles, selectedLeadingIcon = checkedLeadingIcon): no box; on, the check at the start and the
// row in the selected container, as the chosen row of a submenu (and iOS 26's menu toggles); off, the label alone with the
// same 24 dp slot blank so the labels line up. Role.Checkbox as the source. The toggle ticks.
// https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/Menu.kt#CheckableDropdownMenuItem
// not ported: leadingIcon, trailingContent, supportingText, enabled, colours and shapes, interactionSource
@Composable
internal fun CheckableDropdownMenuItem(checked: Boolean, onCheckedChange: (Boolean) -> Unit, text: String) {
    val haptics = rememberHaptics()
    val content = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    DropdownMenuItemContent(
        selected = checked,
        interaction = Modifier.toggleable(
            value = checked,
            role = Role.Checkbox,
            onValueChange = {
                haptics.tick()
                onCheckedChange(it)
            }
        )
    ) {
        if (checked) {
            Icon(MenuIcons.Check, contentDescription = null, modifier = Modifier.size(24.dp), tint = content)
        } else {
            Spacer(Modifier.size(24.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, color = content, modifier = Modifier.weight(1f))
    }
}

// DropdownMenuItem: text and an optional leading icon, the colours from the theme (label onSurface; the error colour for
// a dangerous row). Role.Button.
// https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/Menu.kt#DropdownMenuItem
// trailingIcon (requests.md point 12, Accounts' "Manage folders" row with its chevron) is the source's, after the label.
// not ported: enabled, colours, contentPadding, interactionSource
@Composable
internal fun DropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    color: Color = MaterialTheme.colorScheme.onSurface,
    leadingIcon: (@Composable () -> Unit)? = null,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    val haptics = rememberHaptics()
    DropdownMenuItemContent(
        selected = false,
        interaction = Modifier.clickable(
            role = Role.Button,
            onClick = {
                haptics.tick()
                onClick()
            }
        )
    ) {
        leadingIcon?.invoke()
        Text(text, style = style, color = color, modifier = Modifier.weight(1f))
        trailingIcon?.invoke()
    }
}

// A row with a choice behind it: the label, its current value in onSurfaceVariant (the trailing content) and a chevron.
// TalkBack reads the value as the row's state.
@Composable
internal fun ValueMenuItem(label: String, value: String, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    DropdownMenuItemContent(
        selected = false,
        interaction = Modifier
            .clickable(
                role = Role.Button,
                onClick = {
                    haptics.tick()
                    onClick()
                }
            )
            .semantics { stateDescription = value }
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(
            MenuIcons.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// The submenu's first row: the row it came from as a header, Back at its start.
@Composable
internal fun SubmenuHeader(title: String, onBack: () -> Unit) {
    DropdownMenuItem(
        text = title,
        leadingIcon = {
            Icon(
                MenuIcons.ArrowBack,
                contentDescription = "Back",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        style = MaterialTheme.typography.titleSmall,
        onClick = onBack
    )
}

// The pill that opens a menu: a glass capsule with an icon, a label and, for a pick among several, a small chevron
// ([trailingIcon]). [accent] washes it with the accent (a filter is set). It is an item of a list, so it never floats on the
// list's backdrop (the pitfall of CLAUDE.md): it is drawn with LocalGlassBackdrop null, as a zone. [onClick] opens the menu,
// which is composed beside the pill in the same Box (the Popup's anchor).
@Composable
internal fun MenuPill(
    icon: ImageVector,
    label: String,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    trailingIcon: ImageVector? = null
) {
    CompositionLocalProvider(LocalGlassBackdrop provides null) {
        FloatingPane(
            shape = CircleShape,
            accent = accent,
            onClick = onClick,
            modifier = modifier.semantics {
                contentDescription = label
                stateDescription = if (expanded) "Expanded" else "Collapsed"
            }
        ) {
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(label, style = MaterialTheme.typography.labelLarge)
                trailingIcon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(20.dp)) }
            }
        }
    }
}

// The menu's own icons, so this file stands alone in every app: the three the menu draws, with the same path data and
// builder as the app's icon set (Check, ArrowBack: Material icons on the 24 dp grid; ChevronRight: Material Symbols outlined,
// viewport 960, autoMirrored).
// https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/ui/icon/LinkedOutIcons.kt#build
// https://github.com/google/material-design-icons/blob/master/symbols/android/chevron_right/materialsymbolsoutlined/chevron_right_24px.xml
// not ported: the icon set's other icons (the menu does not draw them)
private object MenuIcons {
    val Check: ImageVector by lazy {
        build(
            "Check",
            "M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z"
        )
    }

    val ArrowBack: ImageVector by lazy {
        build("ArrowBack", "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z")
    }

    val ChevronRight: ImageVector by lazy {
        val pathData = "M504,480L320,296L376,240L616,480L376,720L320,664L504,480Z"
        ImageVector.Builder("ChevronRight", 24.dp, 24.dp, viewportWidth = 960f, viewportHeight = 960f, autoMirror = true)
            .addPath(pathData = PathParser().parsePathString(pathData).toNodes(), fill = SolidColor(Color.White))
            .build()
    }

    private fun build(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = name == "ArrowBack"
        ).apply {
            addPath(
                pathData = PathParser().parsePathString(pathData).toNodes(),
                fill = SolidColor(Color.Black)
            )
        }.build()
}
