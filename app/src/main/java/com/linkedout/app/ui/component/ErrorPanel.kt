package com.linkedout.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
    onVerify: ((AppError.ChallengeRequired) -> Unit)? = null
) {
    val presentation = error.present()

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
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
                ErrorAction.OPEN_DIAGNOSTICS,
                ErrorAction.CHANGE_INSTANCE -> TextButton(onClick = onOpenDiagnostics) {
                    Text("Check connection")
                }
                ErrorAction.OPEN_FALLBACK_VIEWER -> {
                    val check = error as? AppError.ChallengeRequired
                    if (check != null && onVerify != null) {
                        TextButton(onClick = { onVerify(check) }) { Text("Do the check") }
                    } else {
                        TextButton(onClick = onOpenDiagnostics) { Text("Check connection") }
                    }
                }
                ErrorAction.NONE -> Unit
            }
        }
    }
}
