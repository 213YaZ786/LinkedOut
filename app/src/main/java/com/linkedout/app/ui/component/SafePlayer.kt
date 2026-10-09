package com.linkedout.app.ui.component

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ExtractorsFactory

/**
 * A player for films and sounds from outside. Media3 reads the file itself
 * in Kotlin and Java, where a crafted file can only throw, and hands the
 * picture and the sound to Android's own decoders, which run in a
 * sandboxed system process updated every month. On top of that it only
 * knows the formats a message or a post comes in: the other readers Media3
 * carries (streams, old containers) are left out, so a file pretending to
 * be one of them is never parsed. No decoder of our own, ever.
 *
 * Shared across the apps of this base: edit Modules/shared, then run sync.sh.
 */
object SafePlayer {

    @OptIn(UnstableApi::class)
    fun build(context: Context): ExoPlayer =
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context, extractors()))
            .build()

    /** Only the readers of [KNOWN], for a player built with a data source of its own. */
    @OptIn(UnstableApi::class)
    fun extractors(): ExtractorsFactory = ExtractorsFactory {
        DefaultExtractorsFactory().createExtractors().filter { it.javaClass.simpleName in KNOWN }.toTypedArray()
    }

    /** MP4 and 3GP, WebM and Matroska, AMR, Ogg, MP3, AAC, WAV, FLAC. */
    private val KNOWN = setOf(
        "Mp4Extractor", "FragmentedMp4Extractor", "MatroskaExtractor", "AmrExtractor",
        "OggExtractor", "Mp3Extractor", "AdtsExtractor", "WavExtractor", "FlacExtractor"
    )
}
