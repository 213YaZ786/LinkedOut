package com.linkedout.app.core.security

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.net.Uri
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import java.io.DataInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Pictures from outside, decoded by [DecoderService] in its isolated
 * process. Null for anything that is not a picture, too large, or when the
 * decoder took too long or died on it: the caller shows its placeholder.
 *
 * The decoder stays bound a little while after the last picture, so a
 * conversation or a feed full of them does not start a process for each.
 *
 * Shared across the apps of this base: edit Modules/shared, then run sync.sh.
 */
object SafeImages {

    suspend fun decode(context: Context, uri: Uri, maxSide: Int = 2048): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()?.use { decode(context, it, maxSide) }
    }

    suspend fun decode(context: Context, bytes: ByteArray, maxSide: Int = 1440): Bitmap? = withContext(Dispatchers.IO) {
        decode(context, bytes.inputStream(), maxSide)
    }

    suspend fun decode(context: Context, input: InputStream, maxSide: Int): Bitmap? = withContext(Dispatchers.IO) {
        // Two at a time: each can hold a large picture in the decoder's memory.
        running.withPermit {
            withTimeoutOrNull(TIMEOUT) { transact(context.applicationContext, input, maxSide) }
        }.also { releaseLater(context.applicationContext) }
    }

    private suspend fun transact(app: Context, input: InputStream, maxSide: Int): Bitmap? {
        val decoder = binder(app) ?: return null
        val pipe = ParcelFileDescriptor.createPipe()
        // Fed from here while the decoder reads it.
        Thread {
            runCatching { ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { out -> input.copyTo(out) } }
        }.start()
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        val result = try {
            data.writeInterfaceToken(DecoderService.DESCRIPTOR)
            data.writeFileDescriptor(pipe[0].fileDescriptor)
            data.writeInt(maxSide.coerceIn(DecoderService.MIN_SIDE, DecoderService.MAX_SIDE))
            val sent = runCatching { decoder.transact(DecoderService.DECODE, data, reply, 0) }.getOrDefault(false)
            if (sent && reply.readInt() == 1) reply.readFileDescriptor() else null
        } finally {
            pipe[0].close()
            data.recycle()
            reply.recycle()
        }
        result ?: return null
        return runCatching {
            DataInputStream(ParcelFileDescriptor.AutoCloseInputStream(result).buffered()).use { inp ->
                val w = inp.readInt()
                val h = inp.readInt()
                require(w in 1..DecoderService.MAX_SIDE && h in 1..DecoderService.MAX_SIDE)
                val pixels = ByteArray(w * h * 4)
                inp.readFully(pixels)
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { copyPixelsFromBuffer(ByteBuffer.wrap(pixels)) }
            }
        }.getOrNull()
    }

    private val running = Semaphore(2)
    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var bound: IBinder? = null
    private var connection: ServiceConnection? = null
    private var release: Job? = null

    private suspend fun binder(app: Context): IBinder? = lock.withLock {
        release?.cancel()
        bound?.takeIf { it.isBinderAlive }?.let { return@withLock it }
        connection?.let { runCatching { app.unbindService(it) } }
        connection = null
        bound = null
        suspendCancellableCoroutine<IBinder?> { cont ->
            val c = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    bound = service
                    if (cont.isActive) cont.resume(service)
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    bound = null
                }

                override fun onBindingDied(name: ComponentName?) {
                    bound = null
                    if (cont.isActive) cont.resume(null)
                }
            }
            connection = c
            val ok = runCatching { app.bindService(Intent(app, DecoderService::class.java), c, Context.BIND_AUTO_CREATE) }.getOrDefault(false)
            if (!ok && cont.isActive) cont.resume(null)
        }
    }

    private fun releaseLater(app: Context) {
        scope.launch {
            lock.withLock {
                release?.cancel()
                release = scope.launch {
                    delay(KEEP_BOUND)
                    lock.withLock {
                        connection?.let { runCatching { app.unbindService(it) } }
                        connection = null
                        bound = null
                    }
                }
            }
        }
    }

    private const val TIMEOUT = 15_000L
    private const val KEEP_BOUND = 20_000L
}
