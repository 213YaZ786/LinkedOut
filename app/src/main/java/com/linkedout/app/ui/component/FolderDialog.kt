package com.linkedout.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Which folder, with the option of a new one.
 *
 * A dialog rather than a screen, and a row of choices rather than a column of
 * switches, the way every other choice in this app is made. [everything] is
 * the row that means no folder at all, which on Home is the whole stream. An
 * account always lands in a folder, so it passes null and gets folders only.
 */
@Composable
fun FolderDialog(
    title: String,
    folders: List<String>,
    selected: String?,
    /** The "no folder at all" row, or null when every choice is a folder. */
    everything: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (creating) {
                    Text(
                        "A folder exists as long as an account is filed in it, and " +
                            "disappears when the last one leaves.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    TextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        placeholder = { Text("Folder name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    everything?.let { Choice(it, selected == null) { onSelect(null) } }
                    folders.forEach { folder ->
                        Choice(folder, selected == folder) { onSelect(folder) }
                    }
                    TextButton(onClick = { creating = true }) { Text("New folder") }
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(
                    onClick = { onSelect(name.trim()) },
                    enabled = name.isNotBlank()
                ) { Text("Create") }
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = {
            if (creating) TextButton(onClick = { creating = false }) { Text("Back") }
        }
    )
}

@Composable
private fun Choice(label: String, chosen: Boolean, onChoose: () -> Unit) {
    val haptics = rememberHaptics()
    val choose: () -> Unit = {
        haptics.tick()
        onChoose()
    }
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = chosen, onClick = choose)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = chosen, onClick = choose)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
    }
}
