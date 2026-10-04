package com.linkedout.app.core.security

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Decodes pictures that come from outside (a message's photo, a picture
 * from the web, a file chosen on the phone) in a process of its own with
 * no permission, no files and no network: a picture crafted to attack the
 * image decoder can reach nothing of the user's. What comes back is plain
 * pixels, which the app takes without parsing any picture format.
 *
 * Declared by each app taking it, never exported:
 * `<service android:name=".core.security.DecoderService" android:exported="false"
 * android:isolatedProcess="true" android:process=":decoder" />`
 *
 * One call over a plain Binder (no AIDL, so no build feature to switch on):
 * in, the picture as a pipe and the largest side wanted; out, a pipe that
 * gives the width, the height and the ARGB pixels, or nothing.
 *
 * Shared across the apps of this base: edit Modules/shared, then run sync.sh.
 */
class DecoderService : Service() {

    private val binder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code != DECODE) return super.onTransact(code, data, reply, flags)
            data.enforceInterface(DESCRIPTOR)
            val input = data.readFileDescriptor()
            val maxSide = data.readInt()
            val output = input?.let { runCatching { decode(it, maxSide) }.getOrNull() }
            if (reply != null) {
                if (output == null) {
                    reply.writeInt(0)
                } else {
                    reply.writeInt(1)
                    reply.writeFileDescriptor(output.fileDescriptor)
                }
            }
            output?.close()
            return true
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun decode(input: ParcelFileDescriptor, maxSide: Int): ParcelFileDescriptor? {
        val bytes = ParcelFileDescriptor.AutoCloseInputStream(input).use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                total += n
                if (total > MAX_INPUT) return null
                out.write(buffer, 0, n)
            }
            out.toByteArray()
        }
        // Only the formats a picture comes in; anything else is not decoded at all.
        if (MediaCheck.kind(bytes) != MediaCheck.Kind.IMAGE) return null
        val side = maxSide.coerceIn(MIN_SIDE, MAX_SIDE)
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            require(w in 1..MAX_DIMENSION && h in 1..MAX_DIMENSION)
            val scale = min(1f, side.toFloat() / max(w, h))
            if (scale < 1f) decoder.setTargetSize((w * scale).roundToInt().coerceAtLeast(1), (h * scale).roundToInt().coerceAtLeast(1))
        }.let { decoded -> if (decoded.config == Bitmap.Config.ARGB_8888) decoded else decoded.copy(Bitmap.Config.ARGB_8888, false) }
        val pipe = ParcelFileDescriptor.createPipe()
        Thread {
            runCatching {
                DataOutputStream(ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).buffered()).use { out ->
                    out.writeInt(bitmap.width)
                    out.writeInt(bitmap.height)
                    val pixels = ByteBuffer.allocate(bitmap.byteCount)
                    bitmap.copyPixelsToBuffer(pixels)
                    out.write(pixels.array())
                }
            }
            bitmap.recycle()
        }.start()
        return pipe[0]
    }

    companion object {
        internal const val DESCRIPTOR = "com.linkedout.app.core.security.Decoder"
        internal const val DECODE = IBinder.FIRST_CALL_TRANSACTION
        internal const val MIN_SIDE = 16
        internal const val MAX_SIDE = 4096
        private const val MAX_INPUT = 40 * 1024 * 1024
        /** Larger than any camera makes; a bigger header is a trap. */
        private const val MAX_DIMENSION = 30_000
    }
}
