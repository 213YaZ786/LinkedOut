package com.linkedout.app.ui.component

import com.linkedout.app.ui.glass.LocalGlass
import com.linkedout.app.ui.glass.glassZone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/**
 * A dialog of the app: Material's AlertDialog, made of glass when glass is on,
 * like every zone. The fill and the tonal elevation give way to the glass,
 * whose rim draws the edge. Every dialog goes through here.
 *
 * Shared across the apps of this base: edit Modules/shared, then run sync.sh.
 */
@Composable
fun ZoneAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    properties: DialogProperties = DialogProperties()
) {
    val glass = LocalGlass.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = if (glass == null) modifier else modifier.glassZone(shape, glass),
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = if (glass == null) AlertDialogDefaults.containerColor else Color.Transparent,
        iconContentColor = AlertDialogDefaults.iconContentColor,
        titleContentColor = if (glass == null) AlertDialogDefaults.titleContentColor else MaterialTheme.colorScheme.onSurface,
        textContentColor = if (glass == null) AlertDialogDefaults.textContentColor else MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = if (glass == null) AlertDialogDefaults.TonalElevation else 0.dp,
        properties = properties
    )
}
