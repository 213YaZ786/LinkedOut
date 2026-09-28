package com.linkedout.app.core.system

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Keeps the background check from being starved by battery savers.
 *
 * The check runs through WorkManager, which already survives the process
 * being killed and the phone restarting. What stops it on some phones is the
 * maker's own battery saving, which postpones or drops background work of
 * apps it has not been told to leave alone. A permanent notification was
 * considered and refused: it keeps a service alive all day to solve a problem
 * the scheduler does not have. Asking to be left out of battery optimisation,
 * once, at the moment the reader turns background checks on, is the fix that
 * targets the actual cause.
 *
 * Shared across the apps of this base: edit Modules/shared, then run sync.sh.
 */
object BatteryExemption {

    fun isExempt(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    /**
     * Shows Android's own yes or no dialog, which needs
     * REQUEST_IGNORE_BATTERY_OPTIMIZATIONS in the manifest. Where a maker has
     * removed that dialog, the list of apps opens instead and the reader picks
     * this one. Nothing is asked when the app is already exempt.
     */
    @SuppressLint("BatteryLife")
    fun request(context: Context) {
        if (isExempt(context)) return
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(direct)
        } catch (_: ActivityNotFoundException) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
}
