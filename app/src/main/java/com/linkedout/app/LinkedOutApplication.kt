package com.linkedout.app

import com.linkedout.app.core.security.SafeCoilDecoder
import coil3.SingletonImageLoader
import coil3.ImageLoader
import android.app.Application
import com.linkedout.app.data.cache.FeedCache
import com.linkedout.app.data.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
        // Every picture decoded in the isolated decoder, never in the app.
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context).components { add(SafeCoilDecoder.Factory(context)) }.build()
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
        // Old posts go at launch, not only when their account is next fetched.
        koin.get<CoroutineScope>(named("appScope")).launch { koin.get<FeedCache>().applyRetention() }

        val settings = koin.get<SettingsStore>()
        if (settings.current.backgroundSync) {
            SyncWorker.schedule(
                this,
                settings.current.syncIntervalMinutes,
                settings.current.syncOnWifiOnly
            )
        }
    }
}
