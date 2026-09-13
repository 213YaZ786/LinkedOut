package com.linkedout.app.di

import com.linkedout.app.core.debug.LogExporter
import com.linkedout.app.core.debug.RequestLog
import com.linkedout.app.core.link.LinkRouter
import com.linkedout.app.core.media.MediaDownloader
import com.linkedout.app.core.network.ConnectivityMonitor
import com.linkedout.app.core.network.HostThrottle
import com.linkedout.app.core.network.HttpClientFactory
import com.linkedout.app.data.linkedin.LinkedInHost
import com.linkedout.app.core.web.ChallengeGateway
import com.linkedout.app.core.web.ChallengeSolver
import com.linkedout.app.core.web.WebSession
import com.linkedout.app.data.accounts.AccountStore
import com.linkedout.app.data.cache.FeedCache
import com.linkedout.app.data.linkedin.LinkedInSource
import com.linkedout.app.data.linkedin.PostPageParser
import com.linkedout.app.data.linkedin.ProfilePageParser
import com.linkedout.app.data.repository.FeedRepository
import com.linkedout.app.data.repository.TimelineRepository
import com.linkedout.app.data.settings.SettingsStore
import com.linkedout.app.feature.accounts.AccountsViewModel
import com.linkedout.app.feature.feed.FeedViewModel
import com.linkedout.app.feature.post.PostDetailViewModel
import com.linkedout.app.feature.search.SearchViewModel
import com.linkedout.app.feature.settings.SettingsViewModel
import com.linkedout.app.feature.timeline.TimelineViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Single composition root.
 *
 * Much shorter than MTGA's. Everything that made that one long, the instance
 * store, the probe, the directory, the pool and five sources behind it, existed
 * to pick a server. There is one server here and it is a constant, so the graph
 * is a client, a parser and a repository.
 */
val appModule = module {

    single(named("appScope")) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    single { RequestLog() }
    single { LogExporter(androidContext()) }
    single { HostThrottle() }
    single { WebSession() }
    single { ChallengeSolver(get()) }
    single { HttpClientFactory.create(get(), LinkedInHost.USER_AGENT) }
    single { ChallengeGateway(get(), get(), get(), get(), get()) }
    single { ConnectivityMonitor(androidContext()) }
    single { MediaDownloader(androidContext()) }
    single { AccountStore(androidContext()) }
    single { SettingsStore(androidContext()) }
    single { LinkRouter(androidContext()) }

    single { ProfilePageParser() }
    single { PostPageParser() }
    single { LinkedInSource(get(), get(), get(), get(), get()) }
    single { FeedRepository(get()) }

    single {
        val settings: SettingsStore = get()
        FeedCache(androidContext()) { settings.current.keepPostsDays }
    }
    single { TimelineRepository(get(), get(), get()) }

    viewModel { AccountsViewModel(get(), get()) }
    viewModel { PostDetailViewModel(get(), get(), get()) }
    viewModel { SearchViewModel(get()) }
    viewModel { FeedViewModel(get(), get(), get(), get()) }
    viewModel { TimelineViewModel(get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get(), androidContext()) }
}
