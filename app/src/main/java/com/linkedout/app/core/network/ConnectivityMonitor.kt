package com.linkedout.app.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Answers two questions about the device's network.
 *
 * Is there a usable transport at all. Asked before blaming the host, so
 * "you are offline" and "linkedin.com is not answering" never get confused
 * with each other.
 *
 * Is it metered. Live, for "media on Wi-Fi only". Metered rather than
 * "not Wi-Fi", because that is what the reader means: a phone hotspot is Wi-Fi
 * and costs data, an unlimited Ethernet dongle is not Wi-Fi and costs nothing.
 * Android knows which is which, the transport name does not.
 */
class ConnectivityMonitor(private val context: Context) {

    private val manager: ConnectivityManager? =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _metered = MutableStateFlow(manager?.isActiveNetworkMetered ?: false)
    val metered: StateFlow<Boolean> = _metered.asStateFlow()

    init {
        // Lives as long as the process, like this object. One callback, no leak.
        runCatching {
            manager?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    _metered.value = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                }

                override fun onLost(network: Network) {
                    _metered.value = manager?.isActiveNetworkMetered ?: false
                }
            })
        }
    }

    fun isOnline(): Boolean {
        val manager = manager ?: return true
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
