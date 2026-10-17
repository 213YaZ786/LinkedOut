package com.linkedout.app.core.security

import android.content.Context
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.network.NetworkFetcher
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.Options
import coil3.size.pxOrElse
import com.linkedout.app.core.network.ResponseLimit
import com.linkedout.app.core.network.SizeLimits
import com.linkedout.app.core.network.readAsBytes
import kotlin.math.max
import okhttp3.OkHttpClient

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
        // Bounded as Signal's OkHttpUtil.readAsBytes (see readAsBytes): a copy
        // already on disk can be larger than the network lets in.
        val bytes = source.source().use { readAsBytes(it.inputStream(), SizeLimits.PICTURE) }
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

    companion object {
        private const val DEFAULT_SIDE = 2048

        /**
         * Coil's network fetcher on an OkHttp client of its own, as Coil's
         * documentation sets one up (https://coil-kt.github.io/coil/network/),
         * with [ResponseLimit] so a picture over [SizeLimits.PICTURE] fails
         * on the network side, before any byte reaches the decoder. Add it
         * to the image loader's components before [Factory].
         */
        fun network(): NetworkFetcher.Factory =
            OkHttpNetworkFetcherFactory(callFactory = { OkHttpClient.Builder().addInterceptor(ResponseLimit(SizeLimits.PICTURE)).build() })
    }
}
