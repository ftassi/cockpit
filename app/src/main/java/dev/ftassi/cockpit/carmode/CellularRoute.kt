package dev.ftassi.cockpit.carmode

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import android.util.Log

private const val TAG = "Cockpit"
// Without coverage (an underground garage) playback starts anyway, on
// whatever network there is.
private const val READY_TIMEOUT_MS = 3_000L

// While driving, Cockpit's streams go over mobile data even when Wi-Fi is up,
// so driving out of the home Wi-Fi does not cut the radio. Apps cannot turn
// Wi-Fi off since Android 10; keeping mobile data up and binding to it gets
// the same result without touching Wi-Fi, so there is nothing to restore.
// Process-wide: the radio service runs in the same process as the activity.
object CellularRoute {
    private var callback: ConnectivityManager.NetworkCallback? = null

    // onReady runs once, when streams are bound to mobile data or after a short
    // wait without it, so playback is never held back for long.
    fun start(context: Context, onReady: () -> Unit) {
        if (callback != null) {
            onReady()
            return
        }
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val handler = Handler(Looper.getMainLooper())
        var ready = false
        val readyOnce = {
            if (!ready) {
                ready = true
                onReady()
            }
        }
        val cellular = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val bound = connectivity.bindProcessToNetwork(network)
                Log.i(TAG, "Streams bound to mobile data: $bound")
                readyOnce()
            }

            // Back to the default network until mobile data returns.
            override fun onLost(network: Network) {
                connectivity.bindProcessToNetwork(null)
                Log.i(TAG, "Mobile data lost, streams on the default network")
            }
        }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivity.requestNetwork(request, cellular, handler)
        callback = cellular
        handler.postDelayed(readyOnce, READY_TIMEOUT_MS)
    }

    fun stop(context: Context) {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        callback?.let(connectivity::unregisterNetworkCallback)
        callback = null
        connectivity.bindProcessToNetwork(null)
    }
}
