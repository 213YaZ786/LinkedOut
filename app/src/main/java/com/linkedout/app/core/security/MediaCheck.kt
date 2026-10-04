package com.linkedout.app.core.security

import android.content.Context
import android.net.Uri

/**
 * What a file really is, read from its first bytes, never from the type
 * its sender wrote on it: a "photo" that is something else is not handed
 * to a picture or video decoder at all, it stays a file.
 *
 * Only the formats a message, a post or a camera brings are known; the
 * rest is [Kind.OTHER], which is shown as a file and never decoded.
 *
 * Shared across the apps of this base: edit Modules/shared, then run sync.sh.
 */
object MediaCheck {

    enum class Kind { IMAGE, VIDEO, AUDIO, OTHER }

    /** The kind of the file behind [uri], from its first bytes. */
    fun kind(context: Context, uri: Uri): Kind = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val head = ByteArray(HEAD)
            var read = 0
            while (read < HEAD) {
                val n = input.read(head, read, HEAD - read)
                if (n < 0) break
                read += n
            }
            kind(head.copyOf(read))
        }
    }.getOrNull() ?: Kind.OTHER

    fun kind(head: ByteArray): Kind {
        fun at(offset: Int, vararg bytes: Int) =
            head.size >= offset + bytes.size && bytes.indices.all { head[offset + it].toInt() and 0xFF == bytes[it] }
        fun text(offset: Int, value: String) = at(offset, *value.map { it.code }.toIntArray())
        return when {
            at(0, 0xFF, 0xD8, 0xFF) -> Kind.IMAGE
            at(0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> Kind.IMAGE
            text(0, "GIF87a") || text(0, "GIF89a") -> Kind.IMAGE
            text(0, "RIFF") && text(8, "WEBP") -> Kind.IMAGE
            text(0, "RIFF") && text(8, "WAVE") -> Kind.AUDIO
            text(0, "BM") && head.size >= 14 -> Kind.IMAGE
            text(4, "ftyp") -> brand(String(head, 8, minOf(4, head.size - 8).coerceAtLeast(0), Charsets.ISO_8859_1))
            at(0, 0x1A, 0x45, 0xDF, 0xA3) -> Kind.VIDEO
            text(0, "OggS") || text(0, "fLaC") || text(0, "#!AMR") || text(0, "ID3") -> Kind.AUDIO
            // An MP3 or AAC frame: eleven bits set.
            head.size >= 2 && head[0].toInt() and 0xFF == 0xFF && head[1].toInt() and 0xE0 == 0xE0 -> Kind.AUDIO
            else -> Kind.OTHER
        }
    }

    /** An ISO media file says what it holds in its brand. */
    private fun brand(brand: String): Kind = when (brand.trim().lowercase()) {
        "heic", "heix", "heim", "heis", "hevc", "hevx", "mif1", "msf1", "avif", "avis" -> Kind.IMAGE
        "m4a", "m4b", "m4p" -> Kind.AUDIO
        "" -> Kind.OTHER
        else -> Kind.VIDEO
    }

    private const val HEAD = 16
}
