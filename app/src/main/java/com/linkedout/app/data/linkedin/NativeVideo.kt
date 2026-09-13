package com.linkedout.app.data.linkedin

import com.linkedout.app.core.model.MediaItem
import com.linkedout.app.core.model.MediaType
import com.linkedout.app.data.linkedin.Markup.attributeAfter
import com.linkedout.app.data.linkedin.Markup.jsonNumber
import com.linkedout.app.data.linkedin.Markup.jsonStringAt

/**
 * The `<video>` element as LinkedIn renders it, on every page that renders one.
 *
 * Shared on purpose. The profile page, the organisation page and the post page
 * disagree about almost every class name, but they all embed the same player,
 * and the addresses of the renditions live in one escaped JSON attribute on
 * the element itself. Writing this three times would mean fixing it three
 * times.
 *
 * It matters because the graph is no help here. A post page serves a
 * VideoObject with a `contentUrl`, but a profile card and an organisation card
 * are DiscussionForumPosting records with no address for the video at all. Read
 * only the graph and a video in a feed has nowhere to play from, which is
 * exactly what happened: the poster image was handed to the player as if it
 * were the film.
 */
internal object NativeVideo {

    private const val MARKER = "data-test-id=\"feed-native-video-content\""
    private const val SOURCES = "data-sources"
    private const val POSTER = "data-poster-url"

    /**
     * The best rendition in [chunk], or null when it holds no player.
     *
     * "Best" is the highest stated bitrate, not the last one listed. LinkedIn
     * orders the renditions differently from page to page, 360p then 720p then
     * 640p on one, 640p then 720p on another, so position says nothing.
     */
    fun read(chunk: String): MediaItem? {
        val at = chunk.indexOf(MARKER).takeIf { it >= 0 } ?: return null
        val sources = chunk.attributeAfter(at, SOURCES, window = 2_000) ?: return null
        val poster = chunk.attributeAfter(at, POSTER, window = 8_000)?.let(Markup::decodeEntities)

        // The attribute is JSON whose quotes are written as entities, so it is
        // decoded before being read rather than being unpicked in place.
        val decoded = Markup.decodeEntities(sources)
        val best = Markup.run {
            decoded.indices
                .filter { decoded.startsWith("\"src\":", it) }
                .mapNotNull { start ->
                    val src = decoded.jsonStringAt(start + "\"src\":".length + 1)
                        ?: return@mapNotNull null
                    val rate = decoded.indexOf("\"data-bitrate\":", start)
                        .takeIf { it >= 0 }
                        ?.let { decoded.jsonNumber("data-bitrate", it) }
                        ?: 0L
                    src to rate
                }
        }.maxByOrNull { it.second }?.first ?: return null

        return MediaItem(
            previewUrl = poster ?: best,
            downloadUrl = best,
            type = MediaType.VIDEO
        )
    }
}
