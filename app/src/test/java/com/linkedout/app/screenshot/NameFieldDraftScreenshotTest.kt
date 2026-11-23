package com.linkedout.app.screenshot

import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.linkedout.app.ui.component.BoldButton
import com.linkedout.app.ui.component.DialogNameField
import com.linkedout.app.ui.component.DialogPane
import com.linkedout.app.ui.component.QuietButton
import com.linkedout.app.ui.component.rememberDialogNameState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DRAFT for the user's yes (requests.md point 25, MTGA's point 34), nothing of app/src/main changes: the name field of
 * the New folder and Rename dialogs. Ported from MTGA's draft (validated 2026-11-22):
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/test/java/com/mtga/app/screenshot/NameFieldDraftScreenshotTest.kt#NameFieldDraftScreenshotTest
 * Frames: today (FoldersScreen's NameDialog, a plain TextField with a placeholder, text centred), then the kit's
 * DialogNameField (ui/component/ZoneDialog.kt) empty, focused and empty, typed "Recruiters", and Rename with
 * "Recruiters" selected whole.
 * A ZoneAlertDialog is a window of its own, so several of them cannot share one picture: each frame is the dialog's pane
 * (the app's real DialogPane, the same glass) with a local copy of ZoneAlertContent's layout (24 dp, title in
 * headlineSmall, buttons sharing the width, the app's BoldButton and QuietButton), all centred as it is.
 * Robolectric's window never takes focus, so the kit's own requestFocus draws no focused outline; the frames that must
 * show it use [FocusedNameField], a copy of DialogNameField's field with the focus sent through an interaction source.
 * Compared: Android Files' DocumentsUI New folder dialog (outlined TextInputLayout, hint floats as a label, focus
 * on open, Done creates, maxLength 255), Google Drive's New folder, iOS Files.
 * https://github.com/LineageOS/android_packages_apps_DocumentsUI/blob/lineage-24.0/src/com/android/documentsui/CreateDirectoryFragment.java#onCreateDialog
 * https://github.com/LineageOS/android_packages_apps_DocumentsUI/blob/lineage-24.0/res/layout/dialog_file_name.xml#input_wrapper
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/samples/src/main/java/androidx/compose/material3/samples/TextFieldSamples.kt#SimpleOutlinedTextFieldSample
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/samples/src/main/java/androidx/compose/material3/samples/TextFieldSamples.kt#OutlinedTextFieldWithInitialValueAndSelection
 * not ported: DocumentsUI's createDirectory, onShowDialog, the error of a name taken and ensureKeyboardPresent (no
 * keyboard in a picture); the other samples of TextFieldSamples
 */
@OptIn(ExperimentalLayoutApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [36])
class NameFieldDraftScreenshotTest : ScreenshotBase() {

    @Test
    fun draft() = capture("name_field_draft") { Frames(first = true) }

    // A phone is too short for several dialogs: the typed and Rename frames are a second picture (same setup as
    // FoldersFloatingAddDraftScreenshotTest#today and #edge, this folder's model)
    @Test
    fun draftTyped() = capture("name_field_draft_typed") { Frames(first = false) }

    // https://github.com/takahirom/roborazzi/blob/main/roborazzi-compose/src/main/java/com/github/takahirom/roborazzi/RoborazziCompose.kt#captureRoboImage
    @Test
    fun edge() = captureEdge("name_field_draft") { Frames(first = true) }

    // https://github.com/takahirom/roborazzi/blob/main/roborazzi-compose/src/main/java/com/github/takahirom/roborazzi/RoborazziCompose.kt#captureRoboImage
    @Test
    fun edgeTyped() = captureEdge("name_field_draft_typed") { Frames(first = false) }

    @Composable
    private fun Frames(first: Boolean) {
        FlowRow(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (first) {
                // FoldersScreen.kt:91-94: New folder, Create, disabled while the name is blank.
                Frame("New folder", "Create", enabled = false) { TodayField() }
                Frame("New folder", "Create", enabled = false) { KitField("") }
                Frame("New folder", "Create", enabled = false) { FocusedNameField(rememberDialogNameState()) }
            } else {
                Frame("New folder", "Create", enabled = true) { FocusedNameField(rememberDialogNameState("Recruiters")) }
                // FoldersScreen.kt:106-109: the title carries the folder's name, the button reads Rename.
                Frame("Rename Recruiters", "Rename", enabled = true) {
                    FocusedNameField(rememberDialogNameState("Recruiters", selectAll = true))
                }
            }
        }
    }

    @Composable
    private fun Frame(title: String, confirm: String, enabled: Boolean, field: @Composable () -> Unit) {
        Box(Modifier.widthIn(min = 280.dp, max = 560.dp)) {
            DialogPane {
                Column(Modifier.padding(24.dp)) {
                    Box(Modifier.padding(bottom = 16.dp).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    }
                    // ZoneAlertContent's text slot (ZoneDialog.kt:138-147): bodyMedium merged in, text centred.
                    ProvideTextStyle(LocalTextStyle.current.merge(MaterialTheme.typography.bodyMedium).copy(textAlign = TextAlign.Center)) {
                        Box(Modifier.padding(bottom = 24.dp).fillMaxWidth(), contentAlignment = Alignment.TopCenter) { field() }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f), propagateMinConstraints = true) { QuietButton(onClick = {}) { Text("Cancel") } }
                        Box(Modifier.weight(1f), propagateMinConstraints = true) {
                            BoldButton(onClick = {}, enabled = enabled, filled = true) { Text(confirm) }
                        }
                    }
                }
            }
        }
    }

    // NameDialog's field today (FoldersScreen.kt:328-334): a TextField with a placeholder, drawn focused.
    @Composable
    private fun TodayField() {
        var name by remember { mutableStateOf("") }
        val source = remember { MutableInteractionSource() }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            source.emit(FocusInteraction.Focus())
        }
        TextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            placeholder = { Text("Folder name") },
            interactionSource = source,
            modifier = Modifier.fillMaxWidth()
        )
    }

    // The kit's DialogNameField as the app would call it, with the state the dialog would remember.
    @Composable
    private fun KitField(initial: String) {
        DialogNameField(state = rememberDialogNameState(initial), label = "Folder name", onDone = {})
    }

    // DialogNameField's OutlinedTextField (ZoneDialog.kt:372-381), same arguments, with the focus drawn through an
    // interaction source, which is what the field reads to draw it (Material's previews do it).
    @Composable
    private fun FocusedNameField(state: TextFieldState) {
        val source = remember { MutableInteractionSource() }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            source.emit(FocusInteraction.Focus())
        }
        OutlinedTextField(
            state = state,
            lineLimits = TextFieldLineLimits.SingleLine,
            label = { Text("Folder name") },
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Start),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            inputTransformation = InputTransformation.maxLength(255),
            interactionSource = source,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
