package dev.ftassi.cockpit

import android.content.ComponentName
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import dev.ftassi.cockpit.nowplaying.NowPlayingTracker
import dev.ftassi.cockpit.nowplaying.currentPlayerPackage
import dev.ftassi.cockpit.nowplaying.resumePlayer
import dev.ftassi.cockpit.playback.RadioService
import dev.ftassi.cockpit.recents.RecentRadios
import dev.ftassi.cockpit.ui.CockpitScreen
import dev.ftassi.cockpit.ui.CockpitTheme

private const val CAR_MODE_LINK = "cockpit://car"

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private lateinit var config: Config
    private lateinit var nowPlaying: NowPlayingTracker
    private lateinit var carMode: CarMode
    private lateinit var recents: RecentRadios
    private var carModeActive by mutableStateOf(false)
    private var recentRadios by mutableStateOf(emptyList<Block.Radio>())
    // Set by a car mode link, handled in onResume once the controller exists.
    private var carModeRequested = false

    // Registered for the activity's whole life, so it also works while Waze or
    // Spotify is in front.
    private val usbAudio = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<AudioDeviceInfo>) {
            if (added.any { it.isUsbAudio() }) carMode.maxOutUsbVolume()
        }

        // The splitter unplugged (paying a toll, leaving the car): end the drive.
        override fun onAudioDevicesRemoved(removed: Array<AudioDeviceInfo>) {
            if (carMode.isActive && removed.any { it.isUsbAudio() }) exitCarMode()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = loadConfig(this)
        nowPlaying = NowPlayingTracker(this)
        carMode = CarMode(this)
        recents = RecentRadios(this)
        recentRadios = recents.load()
        showCarMode(carMode.isActive)
        if (savedInstanceState == null) carModeRequested = intent.isCarModeLink()
        getSystemService(AudioManager::class.java)
            .registerAudioDeviceCallback(usbAudio, Handler(Looper.getMainLooper()))
        setContent {
            val nowPlayingState by nowPlaying.state.collectAsState()
            CockpitTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CockpitScreen(
                        config.blocks,
                        config.bar,
                        nowPlayingState,
                        recentRadios,
                        carModeActive = carModeActive,
                        onPlay = ::play,
                        onEnterCarMode = ::enterCarMode,
                        onExitCarMode = ::exitCarMode,
                    )
                }
            }
        }
    }

    // launchMode="singleTask": links reach the running activity here.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.isCarModeLink()) carModeRequested = true
    }

    // The controller is the UI's handle on RadioService; it connects
    // asynchronously and only lives while the activity is visible.
    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, RadioService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
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
        getSystemService(AudioManager::class.java).unregisterAudioDeviceCallback(usbAudio)
        super.onDestroy()
    }

    override fun onStop() {
        nowPlaying.stop()
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
        super.onStop()
    }

    private fun enterCarMode() {
        if (!carMode.hasDndAccess) {
            Toast.makeText(this, "Consenti a Cockpit l'accesso a Non disturbare", Toast.LENGTH_LONG).show()
            openDndAccessSettings(this)
            return
        }
        if (carMode.isActive) return
        carMode.enter()
        showCarMode(true)
        resumeListening()
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
        carModeActive = active
        if (active) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun play(radio: Block.Radio) {
        val future = controllerFuture ?: return
        recentRadios = recents.add(radio)
        future.addListener({
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
                            .build(),
                    )
                    .build(),
            )
            controller.prepare()
            controller.play()
        }, mainExecutor)
    }
}

// The car mode link, or the car's USB audio splitter being plugged in (the
// manifest's USB filter only lets that device through).
private fun Intent.isCarModeLink() =
    dataString == CAR_MODE_LINK || action == UsbManager.ACTION_USB_DEVICE_ATTACHED
