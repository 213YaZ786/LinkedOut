package com.linkedout.app.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.linkedout.app.ui.icon.LinkedOutIcons

/**
 * Back to the top of the list.
 *
 * The banner is an item of the list now, so it only comes back when the
 * reader has scrolled all the way up. On a stream that is hundreds of posts
 * long that is a lot of thumb, and this is the way back in one tap. It sits
 * at the bottom right, above the floating dock, where a thumb already is.
 *
 * It fades and scales rather than appearing, because something that pops into
 * a corner while you read pulls the eye away from the text.
 */
@Composable
fun ScrollUpButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 0.8f),
        modifier = modifier
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shadowElevation = 3.dp
        ) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(
                    LinkedOutIcons.ArrowUp,
                    contentDescription = "Back to the top",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
