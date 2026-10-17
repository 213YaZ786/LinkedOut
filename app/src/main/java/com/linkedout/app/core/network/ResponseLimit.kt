package com.linkedout.app.core.network

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.Source
import okio.buffer

/** An answer or a picture bigger than the cap set for it. */
class ResponseTooLarge(val limit: Long) : IOException("More than $limit bytes")

/**
 * Whether [this] failed on the size cap, however Ktor wrapped it: a body
 * read through its channel arrives as a ClosedByteChannelException whose
 * cause is the [ResponseTooLarge] (KtorResponseLimitTest). Walks the cause
 * chain as Kotlin's Throwable.cause provides
 * (https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-throwable/cause.html).
 */
fun Throwable.isTooLarge(): Boolean = generateSequence(this) { it.cause }.any { it is ResponseTooLarge }

/**
 * Size caps, in bytes, of what the network brings. Text (JSON, HTML) is
 * capped per app, beside its HttpClient; pictures are the same everywhere,
 * under the 40 MB the isolated decoder accepts at most.
 */
object SizeLimits {
    const val PICTURE = 25L * 1024 * 1024

    /** Google's redirect notice is a few kB. */
    const val NOTICE = 1L * 1024 * 1024
}

/**
 * [input] read whole, or [ResponseTooLarge] as soon as it holds more than
 * [limit] bytes. Signal-Android's OkHttpUtil.readAsBytes
 * (https://github.com/signalapp/Signal-Android,
 * app/src/main/java/org/thoughtcrime/securesms/util/OkHttpUtil.java):
 * 32 KB chunks, a running total, the limit checked before a chunk is kept.
 */
fun readAsBytes(input: InputStream, limit: Long): ByteArray {
    val out = ByteArrayOutputStream()
    val chunk = ByteArray(32 * 1024)
    var total = 0L
    while (true) {
        val read = input.read(chunk)
        if (read == -1) break
        if (total + read > limit) throw ResponseTooLarge(limit)
        total += read
        out.write(chunk, 0, read)
    }
    return out.toByteArray()
}

/**
 * Stops any answer over [limit] bytes: at once when its Content-Length says
 * so, else when the bytes actually received pass it (a missing or false
 * length, a gzip bomb: the count is of the unpacked bytes, as this sits
 * above OkHttp's gzip). Signal's OkHttpUtil.readAsString does the same two
 * checks, Content-Length first, then the bytes read, throwing an IOException.
 * The reader of the body gets a [ResponseTooLarge], which the callers'
 * failure path already handles as any transport failure. Added to an
 * OkHttp client with addInterceptor (Ktor: `engine { addInterceptor(...) }`,
 * https://ktor.io/docs/client-engines.html#okhttp).
 */
class ResponseLimit(private val limit: Long) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (chain.request().method == "HEAD") return response
        val body = response.body
        if (body.contentLength() > limit) {
            response.close()
            throw ResponseTooLarge(limit)
        }
        val capped = Capped(body.source(), limit).buffer().asResponseBody(body.contentType(), body.contentLength())
        return response.newBuilder().body(capped).build()
    }

    private class Capped(delegate: Source, private val limit: Long) : ForwardingSource(delegate) {
        private var total = 0L

        override fun read(sink: Buffer, byteCount: Long): Long {
            val read = super.read(sink, byteCount)
            if (read > 0) {
                total += read
                if (total > limit) throw ResponseTooLarge(limit)
            }
            return read
        }
    }
}
