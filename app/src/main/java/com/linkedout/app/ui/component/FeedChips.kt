package com.linkedout.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The places to switch between, in glass under a banner: everything, each folder, then [extras] (Home adds its Liked
 * and Archived views, Accounts adds nothing). Ported from SMS's Filters, its chipsRow:
 * https://github.com/213YaZ786/SMS/blob/main/app/src/main/java/com/yaz/sms/feature/conversations/ConversationsScreen.kt#Filters
 * Moved here from Home so that Home and Accounts draw the same chips. [selected] is null for All.
 * not ported: SearchPill (Home's search stays the banner's action)
 * not ported: stacked (the chips always wrap onto more lines when the width is short, never sideways)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeedChips(
    folders: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    extras: List<String> = emptyList()
) {
    val haptics = rememberHaptics()
    val chips: List<Pair<String?, String>> =
        listOf<Pair<String?, String>>(null to "All") + folders.map { it to it } + extras.map { it to it }
    // Wrapped by a FlowRow (Jetpack Compose, https://developer.android.com/develop/ui/compose/layouts/flow).
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        chips.forEach { (value, label) ->
            FloatingPane(
                shape = CircleShape,
                accent = selected == value,
                onClick = {
                    haptics.tick()
                    onSelect(value)
                }
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp))
            }
        }
    }
}
