package dev.ftassi.cockpit

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dev.ftassi.cockpit.config.Block
import dev.ftassi.cockpit.config.loadConfig
import dev.ftassi.cockpit.playback.RadioService
import dev.ftassi.cockpit.ui.CockpitScreen
import dev.ftassi.cockpit.ui.CockpitTheme

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = loadConfig(this)
        setContent {
            CockpitTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CockpitScreen(config.blocks, onPlay = ::play)
                }
            }
        }
    }

    // The controller is the UI's handle on RadioService; it connects
    // asynchronously and only lives while the activity is visible.
    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, RadioService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
    }

    override fun onStop() {
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
        super.onStop()
    }

    private fun play(radio: Block.Radio) {
        val future = controllerFuture ?: return
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
