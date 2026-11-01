package dev.ftassi.cockpit

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dev.ftassi.cockpit.carmode.CarMode
import dev.ftassi.cockpit.carmode.isUsbAudio
import dev.ftassi.cockpit.carmode.openDndAccessSettings
import dev.ftassi.cockpit.config.Block
import dev.ftassi.cockpit.config.Config
import dev.ftassi.cockpit.config.firstRadio
import dev.ftassi.cockpit.config.loadConfig
import dev.ftassi.cockpit.launch.openSpotify
import dev.ftassi.cockpit.launch.navigateHome
import dev.ftassi.cockpit.launch.openWaze
import dev.ftassi.cockpit.nowplaying.NowPlayingTracker
import dev.ftassi.cockpit.overlay.MiniPlayer
import dev.ftassi.cockpit.overlay.openOverlaySettings
import dev.ftassi.cockpit.nowplaying.currentPlayerPackage
import dev.ftassi.cockpit.nowplaying.resumePlayer
import dev.ftassi.cockpit.nowplaying.somethingIsPlaying
import dev.ftassi.cockpit.playback.RadioService
import dev.ftassi.cockpit.recents.RecentRadios
import dev.ftassi.cockpit.spotify.REDIRECT_URI
import dev.ftassi.cockpit.spotify.SpotifyHistoryJob
import dev.ftassi.cockpit.spotify.SpotifyPlaylist
import dev.ftassi.cockpit.spotify.SpotifyRecents
import dev.ftassi.cockpit.ui.CockpitScreen
import dev.ftassi.cockpit.ui.CockpitTheme
import kotlinx.coroutines.launch

private const val CAR_MODE_LINK = "cockpit://car"
// A shaky contact or the starter dipping the power must not end the drive.
private const val UNPLUG_GRACE_MS = 3_000L
private const val SPOTIFY_START_MS = 2_500L

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private lateinit var config: Config
    private lateinit var nowPlaying: NowPlayingTracker
    private lateinit var carMode: CarMode
    private lateinit var miniPlayer: MiniPlayer
    private lateinit var spotify: SpotifyRecents
    private lateinit var recents: RecentRadios
    private var recentRadios = emptyList<Block.Radio>()
    // Set by a car mode link, handled in onResume once the controller exists.
    private var carModeRequested = false
    // Asked for while the controller was not there: played at the next onStart.
    private var pendingRadio: Block.Radio? = null
    private var spotifyLoaded = false
    private val handler = Handler(Looper.getMainLooper())
    private val unplugged = Runnable { if (carMode.isActive) exitCarMode() }
    private val backToCockpit = Runnable {
        startActivity(Intent(this, MainActivity::class.java))
    }

    // Registered for the activity's whole life, so it also works while Waze or
    // Spotify is in front.
    private val usbAudio = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<AudioDeviceInfo>) {
            if (added.any { it.isUsbAudio() }) {
                handler.removeCallbacks(unplugged)
                carMode.maxOutUsbVolume()
            }
        }

        // The splitter unplugged (paying a toll, leaving the car): end the drive.
        override fun onAudioDevicesRemoved(removed: Array<AudioDeviceInfo>) {
            if (carMode.isActive && removed.any { it.isUsbAudio() }) handler.postDelayed(unplugged, UNPLUG_GRACE_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        alive = true
        config = loadConfig(this)
        nowPlaying = NowPlayingTracker(this)
        carMode = CarMode(this)
        miniPlayer = MiniPlayer(this)
        spotify = SpotifyRecents(this)
        SpotifyHistoryJob.schedule(this)
        recents = RecentRadios(this)
        recentRadios = recents.load()
        showCarMode(carMode.isActive)
        if (savedInstanceState == null) {
            carModeRequested = intent.isCarModeLink()
            finishSpotifyLogin(intent)
            // Asked here, never on the way into a drive.
            if (!carModeRequested && !carMode.hasDndAccess) openDndAccessSettings(this)
        }
        // The process may have died mid-drive, taking the audio with it.
        if (carMode.isActive && !carModeRequested) resumeIfSilent()
        getSystemService(AudioManager::class.java)
            .registerAudioDeviceCallback(usbAudio, Handler(Looper.getMainLooper()))
        setContent {
            val nowPlayingState by nowPlaying.state.collectAsState()
            val spotifyState by spotify.state.collectAsState()
            CockpitTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CockpitScreen(
                        config.blocks,
                        config.bar,
                        nowPlayingState,
                        onPlay = ::play,
                        onWaze = { openWithMiniPlayer(::openWaze) },
                        onWazeHome = { openWithMiniPlayer(::navigateHome) },
                        onSpotify = { uri -> openWithMiniPlayer { openSpotify(it, uri) } },
                        spotify = spotifyState,
                        onSpotifyLogin = spotify::startLogin,
                        onPlaylist = ::playPlaylist,
                        onPlaylistPin = ::togglePin,
                    )
                }
            }
        }
    }

    // launchMode="singleTask": links reach the running activity here.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.isCarModeLink()) carModeRequested = true
        finishSpotifyLogin(intent)
    }

    // The controller is the UI's handle on RadioService; it connects
    // asynchronously and only lives while the activity is visible.
    override fun onStart() {
        super.onStart()
        miniPlayer.hide()
        // In the car the row must not reorder under a returning finger: one load
        // per drive, and the hourly job keeps the saved list for the next.
        if (!spotifyLoaded || !carMode.isActive) {
            spotifyLoaded = true
            lifecycleScope.launch { spotify.refresh() }
        }
        val token = SessionToken(this, ComponentName(this, RadioService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        pendingRadio?.let {
            pendingRadio = null
            play(it)
        }
        // Also re-checks the listener permission when coming back from Settings.
        nowPlaying.start()
    }

    override fun onResume() {
        super.onResume()
        if (carModeRequested) {
            carModeRequested = false
            enterCarMode()
        }
    }

    override fun onDestroy() {
        alive = false
        handler.removeCallbacks(unplugged)
        handler.removeCallbacks(backToCockpit)
        miniPlayer.hide()
        getSystemService(AudioManager::class.java).unregisterAudioDeviceCallback(usbAudio)
        super.onDestroy()
    }

    override fun onStop() {
        // A way back from whatever app is in front, and the screen stays on.
        if (carMode.isActive && !isFinishing && miniPlayer.canShow) miniPlayer.show(keepScreenOn = true)
        nowPlaying.stop()
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
        super.onStop()
    }

    private fun finishSpotifyLogin(intent: Intent) {
        val data = intent.data ?: return
        if (intent.dataString?.startsWith(REDIRECT_URI) == true) lifecycleScope.launch { spotify.finishLogin(data) }
    }

    // Spotify's ":play" suffix starts the playlist even from closed, but only by
    // opening Spotify, so Cockpit comes back on its own once it had time to start.
    // Nothing else works: the Web API fails with NO_ACTIVE_DEVICE until Spotify
    // plays here, and its media session ignores playFromUri.
    private fun playPlaylist(uri: String) {
        openWithMiniPlayer { openSpotify(it, "$uri:play") }
        handler.removeCallbacks(backToCockpit)
        handler.postDelayed(backToCockpit, SPOTIFY_START_MS)
    }

    private fun togglePin(playlist: SpotifyPlaylist) {
        if (playlist.pinned) spotify.unpin(playlist.uri) else spotify.pin(playlist.uri)
        val message = if (playlist.pinned) "Playlist non più pinnata" else "Playlist pinnata"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch { spotify.refresh() }
    }

    // While driving, the app opens regardless: the pill is the optional part.
    private fun openWithMiniPlayer(open: (Context) -> Unit) {
        if (miniPlayer.canShow) {
            miniPlayer.show(keepScreenOn = carMode.isActive)
        } else if (!carMode.isActive) {
            Toast.makeText(this, "Consenti a Cockpit di mostrarsi sopra le altre app", Toast.LENGTH_LONG).show()
            openOverlaySettings(this)
            return
        }
        open(this)
    }

    private fun enterCarMode() {
        if (carMode.isActive) {
            resumeIfSilent()
            return
        }
        carMode.enter()
        showCarMode(true)
        resumeListening()
    }

    private fun resumeIfSilent() {
        if (!somethingIsPlaying(this)) resumeListening()
    }

    // Pick up whatever was on when the last drive ended: a podcast in Spotify
    // resumes there, otherwise the radio comes back.
    private fun resumeListening() {
        val player = carMode.lastPlayer
        if (player != null && player != packageName && resumePlayer(this, player)) return
        (recentRadios.firstOrNull() ?: config.firstRadio())?.let(::play)
    }

    private fun exitCarMode() {
        // Before exit() pauses it; unplugging may have paused it already, in
        // which case it is still the most recent session.
        carMode.lastPlayer = currentPlayerPackage(this)
        carMode.exit()
        showCarMode(false)
        finish()
    }

    private fun showCarMode(active: Boolean) {
        if (active) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun play(radio: Block.Radio) {
        val future = controllerFuture
        if (future == null) {
            pendingRadio = radio
            return
        }
        recentRadios = recents.add(radio)
        future.addListener({
            // Released by onStop while connecting: get() would throw.
            if (future !== controllerFuture) {
                pendingRadio = radio
                return@addListener
            }
            val controller = future.get()
            controller.setMediaItem(
                MediaItem.Builder()
                    .setUri(radio.url)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(radio.name)
                            .setStation(radio.name)
                            // System media controls print a missing artist as "null".
                            .setArtist("In diretta")
                            // Shown by the now playing bar and the system media controls.
                            .setArtworkData(radio.logo?.let { assets.open(it).use { s -> s.readBytes() } }, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                            .build(),
                    )
                    .build(),
            )
            controller.prepare()
            controller.play()
        }, mainExecutor)
    }

    companion object {
        // Lets UsbDetachReceiver leave the unplug to a living activity.
        var alive = false
            private set
    }
}

// The car mode link, or the car's USB audio splitter being plugged in (the
// manifest's USB filter only lets that device through).
private fun Intent.isCarModeLink() =
    dataString == CAR_MODE_LINK || action == UsbManager.ACTION_USB_DEVICE_ATTACHED
