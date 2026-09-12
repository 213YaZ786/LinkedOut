package com.linkedout.app.data.accounts

import com.linkedout.app.core.link.LinkedInLink
import com.linkedout.app.core.model.FollowedAccount
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Reads and writes the list of followed accounts. Pure, no Android.
 *
 * The file format is the one Fritter and Squawker share, checked against both
 * sources in September 2026 (lib/settings/_data.dart, lib/database/entities.dart):
 *
 * { "subscriptions": [ { "id": "...", "screen_name": "...", "name": "...",
 *   "profile_image_url_https": null, "verified": 0, "in_feed": 1,
 *   "created_at": "2026-09-11 10:12:00" } ] }
 *
 * One honest gap: both apps key accounts by X's numeric user id, which LinkedOut
 * never learns. The export puts the handle in "id". Fritter and Squawker
 * read the file, but may need to look each account up again.
 *
 * Import is forgiving: that JSON, from any of the three apps, or plain text
 * with handles, @handles or x.com links, one per line or separated by commas.
 */
object SubscriptionCodec {

    private val json = Json { ignoreUnknownKeys = true }
    private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)

    fun export(accounts: List<FollowedAccount>): String {
        val root = buildJsonObject {
            put("exported_by", "LinkedOut")
            put(
                "subscriptions",
                buildJsonArray {
                    accounts.forEach { account ->
                        add(
                            buildJsonObject {
                                put("id", account.handle)
                                put("screen_name", account.handle)
                                put("name", account.displayName ?: account.handle)
                                put("profile_image_url_https", null as String?)
                                put("verified", 0)
                                put("in_feed", 1)
                                put("created_at", DATE.format(Instant.ofEpochMilli(account.addedAtMillis.coerceAtLeast(0))))
                            }
                        )
                    }
                }
            )
            // Present and empty, so Fritter and Squawker see a file they know.
            put("subscriptionGroups", JsonArray(emptyList()))
            put("subscriptionGroupMembers", JsonArray(emptyList()))
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    /** Handles found in [text], valid, deduplicated ignoring case, in file order. */
    fun import(text: String): List<String> {
        val trimmed = text.trim()
        val found = if (trimmed.startsWith("{") || trimmed.startsWith("[")) fromJson(trimmed) else fromText(trimmed)
        return found.mapNotNull(FollowedAccount::normalise)
            .distinctBy { it.lowercase() }
    }

    private fun fromJson(text: String): List<String> {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return fromText(text)
        val list = when (root) {
            is JsonObject -> root["subscriptions"] as? JsonArray
            is JsonArray -> root
            else -> null
        } ?: return emptyList()

        return list.mapNotNull { element ->
            when (element) {
                is JsonObject -> (element["screen_name"] ?: element["screenName"] ?: element["handle"])
                    .let { (it as? JsonPrimitive)?.contentOrNull }
                is JsonPrimitive -> element.contentOrNull
                else -> null
            }
        }
    }

    private fun fromText(text: String): List<String> =
        text.split('\n', ',', ';', ' ', '\t')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { token ->
                when (val link = LinkedInLink.parse(token)) {
                    is LinkedInLink.Profile -> link.handle
                    is LinkedInLink.Company -> link.slug
                    is LinkedInLink.Post -> link.handle
                    null -> if (token.contains("://")) null else token
                }
            }
}
