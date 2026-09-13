package com.linkedout.app.data.accounts

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
 *
 * The kind now rides along. Fritter's format has one column and no room for
 * it, so LinkedOut writes its own `linkedout_kind` beside the shared fields:
 * an unknown key is ignored by both other apps, and a file exported here and
 * imported here keeps its organisations. Before this, an organisation was
 * dropped on import rather than followed as a person, which was the honest
 * half measure and is no longer needed.
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
                                // LinkedOut's own field. Fritter and Squawker
                                // ignore what they do not know, so the file
                                // stays readable by both.
                                put("linkedout_kind", account.kind.segment)
                                // The same fact as an address, for anything
                                // that reads links rather than our field.
                                put("url", SubscriptionFormat.addressOf(account.handle, account.kind))
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

    /** Accounts found in [text], valid, deduplicated ignoring case, in file order. */
    fun import(text: String): List<SubscriptionFormat.Entry> {
        val trimmed = text.trim()
        val found = if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            fromJson(trimmed)
        } else {
            SubscriptionFormat.fromText(trimmed)
        }
        return SubscriptionFormat.clean(found)
    }

    private fun fromJson(text: String): List<SubscriptionFormat.Entry> {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull()
            ?: return SubscriptionFormat.fromText(text)
        val list = when (root) {
            is JsonObject -> root["subscriptions"] as? JsonArray
            is JsonArray -> root
            else -> null
        } ?: return emptyList()

        return list.mapNotNull { element ->
            when (element) {
                // Which key means what is decided in SubscriptionFormat, which
                // has no JSON library and can therefore be run against real
                // files outside a build. This function only pulls the fields
                // out of the tree.
                is JsonObject -> SubscriptionFormat.entryOf(
                    handle = element.text("screen_name") ?: element.text("screenName") ?: element.text("handle"),
                    kindField = element.text("linkedout_kind"),
                    url = element.text("url")
                )
                is JsonPrimitive -> element.contentOrNull?.let { SubscriptionFormat.entryOf(it) }
                else -> null
            }
        }
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

}
