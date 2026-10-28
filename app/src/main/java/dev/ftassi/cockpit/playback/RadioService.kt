package dev.ftassi.cockpit.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

// Owns the player so the radio keeps playing with the screen off or the app in
// the background. Media3 turns the session into the media notification and
// lock screen controls, and runs the service in the foreground while playing.
class RadioService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            // handleAudioFocus = true: pause when another app (e.g. Spotify)
            // starts playing, and make it pause when we start.
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            // Pause when the audio output is unplugged (AUX cable pulled).
            .setHandleAudioBecomingNoisy(true)
            // Keep CPU and Wi-Fi awake while streaming with the screen off.
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(object : Player.Listener {
            // Whatever paused us (user, Spotify taking audio focus, AUX
            // unplugged), drop the stream: a paused live stream would resume
            // from stale buffered audio, or fail because the server closed the
            // idle connection. In IDLE, the session's play command prepares
            // again, reconnecting to the live edge.
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady) player.stop()
            }
        })
        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
