package com.linkedout.app.feature.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings

// The Android settings page LinkedOut sends the reader to from the welcome guide, ported from MTGA's SystemPages.kt.
// not ported: xLinksEnabled (X's link state: LinkedOut's is linkedInLinksEnabled, in WelcomeScreen.kt and SettingsScreen.kt)
// not ported: openLinkSettings (LinkedOut keeps its own copies in WelcomeScreen.kt and SettingsScreen.kt)

/**
 * LinkedOut's own notification page in Android settings: ACTION_APP_NOTIFICATION_SETTINGS with EXTRA_APP_PACKAGE, with
 * the new task flag kept, since a non-activity context may call it.
 * https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/feature/settings/SystemPages.kt#openNotificationSettings
 */
internal fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
