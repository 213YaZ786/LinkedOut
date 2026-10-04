package com.linkedout.app.core.security

import android.content.Context
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.size.pxOrElse
import kotlin.math.max

/**
 * Every picture of a feed decoded in the isolated decoder ([SafeImages])
 * instead of inside the app: a post's photo is a file anyone uploaded, and
 * some networks serve it as it was sent. Registered first, so Coil's own
 * decoders, which run in the app, never see a picture. A picture the
 * isolated decoder refuses fails, it is not passed on to them.
 *
 * Shared across the apps of this base: edit Modules/shared, then run sync.sh.
 */
class SafeCoilDecoder(
    private val context: Context,
    private val source: ImageSource,
    private val options: Options
) : Decoder {

    override suspend fun decode(): DecodeResult {
        val bytes = source.source().use { it.readByteArray() }
        val wanted = max(options.size.width.pxOrElse { 0 }, options.size.height.pxOrElse { 0 })
        val side = if (wanted > 0) wanted else DEFAULT_SIDE
        val bitmap = SafeImages.decode(context, bytes, side) ?: error("Not a picture the isolated decoder accepts")
        return DecodeResult(image = bitmap.asImage(), isSampled = true)
    }

    class Factory(context: Context) : Decoder.Factory {
        private val app = context.applicationContext

        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder =
            SafeCoilDecoder(app, result.source, options)
    }

    private companion object {
        const val DEFAULT_SIDE = 2048
    }
}
