package com.linkedout.app.core.debug

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Writes text into the public Downloads folder.
 *
 * MediaStore rather than a raw file path, so this needs no storage permission
 * and the file shows up in the Files app and in Chrome's downloads immediately.
 */
class LogExporter(private val context: Context) {

    suspend fun exportText(fileName: String, contents: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Could not create the file")

                resolver.openOutputStream(uri)?.use { it.write(contents.toByteArray()) }
                    ?: error("Could not open the file for writing")

                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)

                "Downloads/$fileName"
            }
        }
}
