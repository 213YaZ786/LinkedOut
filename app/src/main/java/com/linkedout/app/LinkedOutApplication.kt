package com.linkedout.app

import com.linkedout.app.core.security.SafeCoilDecoder
import coil3.SingletonImageLoader
import coil3.ImageLoader
import android.app.Application
import coil3.disk.DiskCache
import okio.Path.Companion.toOkioPath
import java.io.File
import com.linkedout.app.data.cache.FeedCache
import com.linkedout.app.data.settings.SettingsStore
import com.linkedout.app.core.media.OfflineMedia
import com.linkedout.app.core.media.VideoCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.qualifier.named
import com.linkedout.app.di.appModule
import com.linkedout.app.sync.SyncWorker
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class LinkedOutApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // The isolated picture decoder is a process of this app with no
        // rights at all: nothing of the app starts there.
        if (android.os.Process.isIsolated()) return
        // The links kit's catalogs (ClearURLs rules, public suffixes) from the assets, for LinkCleaner and the link sheet.
        com.linkedout.app.core.link.ClearUrls.attach(this)
        // The saves that finished while the app was not running get their post address in the media store.
        kotlin.concurrent.thread(name = "save-origin") { com.linkedout.app.core.media.SaveOrigin.settle(this) }
        // Every picture decoded in the isolated decoder, never in the app.
        SingletonImageLoader.setSafe { context ->
            // The network side first, pictures capped in bytes before the decoder.
            // https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/MtgaApplication.kt#MtgaApplication
            ImageLoader.Builder(context).components {
                add(SafeCoilDecoder.network())
                add(SafeCoilDecoder.Factory(context))
            }.diskCache {
                // Opened on first use, off the main thread. In filesDir, not cacheDir:
                // Android empties cacheDir when space is short, and a post read
                // on the train needs its pictures.
                DiskCache.Builder()
                    .directory(File(context.filesDir, IMAGE_CACHE).toOkioPath())
                    .maxSizeBytes(IMAGE_CACHE_BYTES)
                    .build()
            }.build()
        }
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.DEBUG else Level.NONE)
            androidContext(this@LinkedOutApplication)
            modules(appModule)
        }

        // WorkManager survives reboots, but re-applying on launch keeps the
        // schedule honest after an app update or a settings change made while
        // the worker was cancelled.
        val koin = org.koin.core.context.GlobalContext.get()
        // Old posts go at launch, not only when their account is next fetched,
        // and their saved media goes with them. One retention, one sweep.
        // https://github.com/213YaZ786/MTGA/blob/main/app/src/main/java/com/mtga/app/MtgaApplication.kt#MtgaApplication
        koin.get<CoroutineScope>(named("appScope")).launch {
            // The video cache reads its folder on first use: done here, not under the first player.
            runCatching { withContext(Dispatchers.IO) { koin.get<VideoCache>().cache } }
            val cache = koin.get<FeedCache>()
            cache.applyRetention()
            koin.get<OfflineMedia>().keepOnly(cache.storedPostIds())
        }

        val settings = koin.get<SettingsStore>()
        if (settings.current.backgroundSync) {
            SyncWorker.schedule(
                this,
                settings.current.syncIntervalMinutes,
                settings.current.syncOnWifiOnly
            )
        }
    }

    companion object {
        private const val IMAGE_CACHE = "coil3_disk_cache"
        private const val IMAGE_CACHE_BYTES = 500L * 1024 * 1024
    }
}
