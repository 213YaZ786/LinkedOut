package com.linkedout.app.core.media

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.linkedout.app.core.debug.RequestLog
import com.linkedout.app.core.model.SharedDocument
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.coroutines.cancellation.CancellationException

/**
 * Opens and saves the PDF behind a [SharedDocument].
 *
 * The post page names a manifest on media.licdn.com, and the manifest names
 * the file as transcribedDocumentUrl. Both are public CDN addresses that a
 * logged out visitor may read, no cookie is sent and none is needed. The file
 * address is signed for about ten days, so it is looked up on each tap rather
 * than kept.
 *
 * Opening hands the address to whatever reads PDFs on the phone, and falls
 * back to the browser. Saving goes through DownloadManager like every other
 * save, so it survives the app leaving the screen and needs no permission.
 */
class SharedDocuments(
    private val context: Context,
    private val client: HttpClient,
    private val scope: CoroutineScope,
    private val log: RequestLog
) {

    fun open(document: SharedDocument) {
        toast("Opening ${document.title}")
        scope.launch {
            val file = fileUrl(document) ?: return@launch toast(NOT_FOUND)
            withContext(Dispatchers.Main) { view(file) }
        }
    }

    fun save(document: SharedDocument, authorHandle: String) {
        scope.launch {
            val file = fileUrl(document) ?: return@launch toast(NOT_FOUND)
            val name = fileName(document, authorHandle)
            val request = DownloadManager.Request(Uri.parse(file))
                .setTitle(name)
                .setDescription("Saving from LinkedOut")
                .setMimeType(PDF)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                ?: return@launch toast("Downloads are unavailable on this device")
            runCatching { manager.enqueue(request) }
                .onSuccess { toast("Saving $name") }
                .onFailure { toast("Could not start the download") }
        }
    }

    private suspend fun fileUrl(document: SharedDocument): String? = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        try {
            val response = client.get(document.manifestUrl)
            val body = response.bodyAsText()
            val file = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
                ?.get("transcribedDocumentUrl")
                ?.let { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
            log.record(
                kind = RequestLog.Kind.MEDIA,
                url = document.manifestUrl,
                outcome = if (file != null) "document found" else "no file in the manifest",
                httpStatus = response.status.value,
                bodyBytes = body.length,
                durationMillis = System.currentTimeMillis() - started
            )
            file
        } catch (failure: Throwable) {
            if (failure is CancellationException) throw failure
            log.record(
                kind = RequestLog.Kind.MEDIA,
                url = document.manifestUrl,
                outcome = "transport failure",
                detail = failure.message ?: failure::class.simpleName
            )
            null
        }
    }

    /**
     * Typed first, so a PDF reader is offered before a browser. Not every
     * reader accepts an https address, and when none does the plain view
     * intent still reaches the browser, which downloads it.
     */
    private fun view(file: String) {
        val uri = Uri.parse(file)
        val typed = Intent(Intent.ACTION_VIEW).setDataAndType(uri, PDF).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(typed)
        } catch (_: ActivityNotFoundException) {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure { toast("No app on this phone can open a PDF") }
        }
    }

    private fun fileName(document: SharedDocument, authorHandle: String): String {
        val title = document.title.replace(UNSAFE, "_").trim('_').take(60).ifEmpty { "document" }
        return "linkedout_${authorHandle}_${title}_${System.currentTimeMillis()}.pdf"
    }

    /** Always from the main thread, since the lookup finishes on another. */
    private fun toast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    private companion object {
        const val PDF = "application/pdf"
        const val NOT_FOUND = "LinkedIn did not give this document's file"
        val UNSAFE = Regex("[^\\p{L}\\p{N}._-]+")
        val json = Json { ignoreUnknownKeys = true }
    }
}
