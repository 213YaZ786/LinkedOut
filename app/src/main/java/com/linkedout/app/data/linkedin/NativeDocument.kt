package com.linkedout.app.data.linkedin

import com.linkedout.app.core.model.SharedDocument
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal object NativeDocument {

    private const val DOCUMENT = "data-native-document-config"

    /**
     * A shared PDF or slide deck. LinkedIn renders it as an iframe whose
     * config attribute is a JSON blob: title, page count, cover pages, and
     * the manifest that leads to the file. The post page and a profile
     * card embed the same viewer, so both read it here.
     */
    fun read(html: String): SharedDocument? = html.document()

    private fun String.document(): SharedDocument? {
        val at = indexOf(DOCUMENT).takeIf { it >= 0 } ?: return null
        val open = indexOf('"', at + DOCUMENT.length).takeIf { it >= 0 } ?: return null
        val close = indexOf('"', open + 1).takeIf { it > open } ?: return null
        // Entities only, the attribute cannot hold a raw quote.
        val config = runCatching {
            Json.parseToJsonElement(Markup.decodeEntities(substring(open + 1, close))) as? JsonObject
        }.getOrNull()?.let { (it["doc"] as? JsonObject) ?: it } ?: return null
        // Only LinkedIn's media host: the app fetches the manifest and then
        // the file it names, and a page's attribute is not to be trusted
        // with sending it anywhere else.
        val manifest = (config.text("manifestUrl") ?: config.text("url"))?.takeIf(::onMediaHost) ?: return null
        val cover = (config["coverPages"] as? JsonArray)?.firstOrNull()
            ?.let { ((it as? JsonObject)?.get("config") as? JsonObject)?.text("src") }
            ?.takeIf(::onMediaHost)
        return SharedDocument(
            title = config.text("title")?.trim()?.takeIf { it.isNotEmpty() } ?: "Document",
            pageCount = (config["totalPageCount"] as? JsonPrimitive)?.content?.toIntOrNull(),
            coverUrl = cover,
            manifestUrl = manifest
        )
    }

    fun onMediaHost(url: String): Boolean = url.startsWith(MEDIA_HOST)

    private const val MEDIA_HOST = "https://media.licdn.com/"

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
}
