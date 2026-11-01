package dev.ftassi.cockpit.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import kotlin.math.min

private const val TAG = "Cockpit"
// Media3's own retries can take over a minute to report an error: a car going
// out of Wi-Fi range would sit in silence all that time.
private const val STALL_MS = 10_000L

// Keeps a live stream going through coverage gaps: when playback fails while
// the user still wants it, prepare again with exponential backoff, and right
// away as soon as a network comes back.
class Reconnector(context: Context, private val player: Player) {
    private val handler = Handler(Looper.getMainLooper())
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private var attempt = 0
    private val retry = Runnable {
        Log.i(TAG, "Reconnecting (attempt $attempt)")
        // Only an idle player prepares again; stop() keeps the station.
        player.stop()
        player.prepare()
    }
    private val stalled = Runnable {
        if (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING) {
            Log.w(TAG, "Stream stalled, reconnecting")
            retry.run()
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            if (!player.playWhenReady) return
            val delay = reconnectDelayMs(attempt++)
            Log.w(TAG, "Stream failed (${error.errorCodeName}), retrying in ${delay}ms")
            schedule(delay)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) attempt = 0
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady) {
                handler.removeCallbacks(retry)
                handler.removeCallbacks(stalled)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            handler.removeCallbacks(stalled)
            if (playbackState == Player.STATE_BUFFERING) handler.postDelayed(stalled, STALL_MS)
        }
    }

    // Called on a binder thread; the player must only be touched on main.
    // Fires for every physical network (Wi-Fi, cellular) with internet: a
    // default-network callback would never fire behind an always-on VPN, whose
    // tunnel stays up while the networks underneath come and go.
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            handler.post {
                Log.i(TAG, "Network available")
                val stuck = player.playerError != null || player.playbackState == Player.STATE_BUFFERING
                if (stuck && player.playWhenReady) schedule(0)
            }
        }

        override fun onLost(network: Network) {
            Log.i(TAG, "Network lost")
        }
    }

    init {
        player.addListener(playerListener)
        connectivity.registerNetworkCallback(
            NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
            networkCallback,
        )
    }

    fun release() {
        handler.removeCallbacks(retry)
        handler.removeCallbacks(stalled)
        player.removeListener(playerListener)
        connectivity.unregisterNetworkCallback(networkCallback)
    }

    private fun schedule(delayMs: Long) {
        handler.removeCallbacks(retry)
        handler.postDelayed(retry, delayMs)
    }
}

// 1s, 2s, 4s, ... capped at 30s.
fun reconnectDelayMs(attempt: Int): Long = min(1_000L shl min(attempt, 5), 30_000L)
