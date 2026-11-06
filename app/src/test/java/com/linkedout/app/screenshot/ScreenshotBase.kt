package com.linkedout.app.screenshot

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.linkedout.app.navigation.ReadableScroll
import com.linkedout.app.ui.glass.LocalGlass
import com.linkedout.app.ui.glass.glassGround
import com.linkedout.app.ui.glass.rememberGlassLook
import com.linkedout.app.ui.theme.LinkedOutTheme
import org.junit.Rule
import org.robolectric.RuntimeEnvironment

/**
 * What every screenshot test of this app shares: a screen drawn on a phone and a tablet, by day and by night, and
 * compared with the pictures kept in src/test/screenshots. Ported from Language's ScreenshotBase
 * https://github.com/213YaZ786/Language/blob/main/app/src/test/java/com/yaz/language/screenshot/ScreenshotBase.kt#ScreenshotBase
 * which takes it from Now in Android's captureForDevice
 * (https://github.com/android/nowinandroid/blob/main/core/screenshot-testing/src/main/kotlin/com/google/samples/apps/nowinandroid/core/testing/util/ScreenshotHelper.kt):
 * the screen's size set through Robolectric's qualifiers, inspection mode on, one picture each; and written as SMS's
 * PersonPageScreenshotTest (this family's model)
 * https://github.com/213YaZ786/SMS/blob/main/app/src/test/java/com/yaz/sms/feature/thread/PersonPageScreenshotTest.kt
 * The frame around the screen is MainActivity's (the theme, glass look and ground), which needs an activity's window
 * bars, so it is rebuilt here without them.
 */
abstract class ScreenshotBase {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    // Now in Android's DefaultTestDevices, phone and tablet.
    private val devices = listOf(
        "phone" to "w411dp-h914dp-420dpi",
        "tablet" to "w1280dp-h800dp-320dpi"
    )

    private var pictures = 0

    private val options = RoborazziOptions(
        // Pixel-perfect, as Now in Android's DefaultRoborazziOptions: a word changed is a change.
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0f),
        recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5)
    )

    // As captureForDevice: the qualifiers first, then the content set on the activity again; [interact] taps what the
    // picture should show opened, once the screen has settled.
    protected fun capture(name: String, interact: () -> Unit = {}, body: @Composable () -> Unit) {
        devices.forEach { (device, qualifiers) ->
            RuntimeEnvironment.setQualifiers(qualifiers)
            listOf(false, true).forEach { night ->
                // The activity keeps the saved state of the screen drawn before (a section opened, a scroll): a key per
                // picture gives each its own, so a tap in one does not decide the next.
                val picture = pictures++
                compose.activity.setContent {
                    CompositionLocalProvider(LocalInspectionMode provides true) {
                        LinkedOutTheme(darkTheme = night, dynamicColor = false) {
                            // The app's own look, quiet glass, as MainActivity sets it.
                            val look = rememberGlassLook(MaterialTheme.colorScheme, true, quiet = true)
                            CompositionLocalProvider(LocalGlass provides look, LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                                key(picture) {
                                    Box(Modifier.fillMaxSize().glassGround(look, MaterialTheme.colorScheme.background)) {
                                        // As LinkedOutApp wraps every route: the readable column on a wide window.
                                        ReadableScroll { body() }
                                    }
                                }
                            }
                        }
                    }
                }
                compose.waitForIdle()
                interact()
                compose.onRoot().captureRoboImage(
                    "src/test/screenshots/${name}_${device}_${if (night) "dark" else "light"}.png",
                    roborazziOptions = options
                )
            }
        }
    }
}
