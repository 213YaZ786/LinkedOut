package com.linkedout.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.linkedout.app.core.common.AppError
import com.linkedout.app.core.common.ErrorAction
import com.linkedout.app.core.common.present

/**
 * The visible half of the error taxonomy. Every failure gets a name, an
 * explanation and the one action that helps, instead of a shrug.
 */
@Composable
fun ErrorPanel(
    error: AppError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val presentation = error.present()

    Zone(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(presentation.headline, style = MaterialTheme.typography.titleMedium)
            Text(
                presentation.explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when (presentation.action) {
                ErrorAction.RETRY -> TextButton(onClick = onRetry) { Text("Try again") }
                ErrorAction.NONE -> Unit
            }
        }
    }
}
