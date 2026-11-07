package com.linkedout.app.core.web

import android.content.Context
import android.util.Log
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewOutcomeReceiver
import androidx.webkit.WebViewStartUpConfig
import androidx.webkit.WebViewStartUpResult
import androidx.webkit.WebViewStartupException
import java.util.concurrent.Executors

/**
 * Starts the WebView engine up before the backstage check needs it. The first
 * WebView of a process loads Chromium on the UI thread, in one block (30 frames
 * skipped on the 9a when a wall sent the first read through the engine,
 * 2026-11-07): startUpWebView moves what can run elsewhere to a background
 * thread and cuts the rest into pieces. Called once the first frame is drawn,
 * never in Application.onCreate, so the cold start pays nothing.
 *
 * Android's recipe, as written (initializeWebView, Kotlin sample):
 * https://developer.android.com/develop/ui/views/layout/webapps/optimize-webview-startup
 * over androidx.webkit's WebViewCompat.startUpWebView:
 * https://github.com/androidx/androidx/blob/androidx-main/webkit/webkit/src/main/java/androidx/webkit/WebViewCompat.java#startUpWebView
 * not ported: setupWebView (the sample's own next step; here the backstage creates its WebView when a wall asks).
 */
object WebViewStartup {

    private const val TAG = "WebViewStartup"

    @Volatile private var started = false

    fun initializeWebView(context: Context) {
        if (started) return
        started = true
        // 1. Create a startup configuration specifying the background thread
        // that WebView will use to run its initialization tasks.
        val startUpConfig = WebViewStartUpConfig.Builder(
            Executors.newSingleThreadExecutor()
        ).build()

        // 2. Trigger WebView startup asynchronously
        WebViewCompat.startUpWebView(
            context.applicationContext,
            startUpConfig,
            object : WebViewOutcomeReceiver<WebViewStartUpResult, WebViewStartupException> {

                override fun onResult(result: WebViewStartUpResult) {
                    // Success: The WebView has finished its background initialization.
                    // This callback is guaranteed to be invoked on the UI thread.
                    // Where a start still blocked the UI thread, said in the log (the audit of the recipe).
                    result.uiThreadBlockingStartUpLocations?.forEach { location ->
                        Log.w(TAG, "WebView startup blocked the UI thread here:", location.stackInformation)
                    }
                }

                override fun onError(error: WebViewStartupException) {
                    // Failure: The initialization encountered a startup exception.
                    Log.e(TAG, "Failed to initialize WebView", error)
                }
            }
        )
    }
}
