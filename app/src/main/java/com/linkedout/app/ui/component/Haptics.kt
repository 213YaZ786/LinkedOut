package com.linkedout.app.ui.component

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Every action answers.
 *
 * Three words and no more, so the whole app speaks the same way: a light tick
 * for an ordinary tap, a firm one when something is taken or begun, and a
 * short confirmation when something is finished or undone.
 *
 * Through the view rather than through Compose's own haptic type, because the
 * constants below have existed since well before this app's minimum version
 * while Compose's list has grown one name at a time, and a name that does not
 * exist in the exact version the bill of materials resolves is a failed build.
 *
 * The system setting is respected on its own: a phone with haptics turned off
 * feels nothing, and nothing here overrides that.
 */
class Haptics(private val view: View) {

    /** An ordinary tap: a button, a card, a row, a choice in a dialog. */
    fun tick() {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    /** Something begins or is taken: a refresh pulled, a tab moved to. */
    fun firm() {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    /** Something is finished: a follow, a deletion, a file saved. */
    fun done() {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
