package dev.ftassi.cockpit.nowplaying

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed interface NowPlayingState {
    // The notification listener permission has not been granted yet.
    data object NeedsAccess : NowPlayingState

    data object Idle : NowPlayingState

    data class Active(
        val title: String,
        val subtitle: String?,
        val artwork: Bitmap?,
        val isPlaying: Boolean,
        // Our radio wants to play but has no audio yet: starting or reconnecting.
        val connecting: Boolean,
        private val controller: MediaController,
    ) : NowPlayingState {
        // The app playing it, e.g. Cockpit itself for the radio.
        val source: String get() = controller.packageName

        fun togglePlayPause() {
            if (isPlaying || connecting) controller.transportControls.pause() else controller.transportControls.play()
        }
    }
}

// Follows whichever media session is active on the phone (our radio, Spotify,
// anything else) so a single bar can show and control it.
class NowPlayingTracker(private val context: Context) {
    private val sessions = context.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(context, SessionListener::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var controllers: List<MediaController> = emptyList()

    private val mutableState = MutableStateFlow<NowPlayingState>(NowPlayingState.Idle)
    val state: StateFlow<NowPlayingState> = mutableState

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { track(it.orEmpty()) }

    private val controllerChanged = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
        override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
        override fun onSessionDestroyed() = refresh()
    }

    fun start() {
        val granted = context.getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(listener)
        if (!granted) {
            mutableState.value = NowPlayingState.NeedsAccess
            return
        }
        sessions.addOnActiveSessionsChangedListener(sessionsChanged, listener, handler)
        track(sessions.getActiveSessions(listener))
    }

    fun stop() {
        sessions.removeOnActiveSessionsChangedListener(sessionsChanged)
        track(emptyList())
    }

    private fun track(active: List<MediaController>) {
        controllers.forEach { it.unregisterCallback(controllerChanged) }
        controllers = active
        controllers.forEach { it.registerCallback(controllerChanged, handler) }
        refresh()
    }

    private fun refresh() {
        // Sessions without a title (e.g. the telecom one) have nothing to show.
        val candidates = controllers.filter { it.title() != null }
        val chosen = pickActive(candidates) { it.isPlaying() }
        mutableState.value = if (chosen == null) {
            NowPlayingState.Idle
        } else {
            NowPlayingState.Active(
                title = chosen.title()!!,
                subtitle = chosen.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST),
                artwork = chosen.metadata?.artwork(),
                isPlaying = chosen.isPlaying(),
                connecting = chosen.packageName == context.packageName && chosen.playbackState?.state.let {
                    it == PlaybackState.STATE_BUFFERING || it == PlaybackState.STATE_ERROR
                },
                controller = chosen,
            )
        }
    }
}

// The app whose session the bar would show right now: what is playing, or what
// was playing last. Requires the notification listener permission.
fun currentPlayerPackage(context: Context): String? =
    pickActive(activeSessions(context).filter { it.title() != null }) { it.isPlaying() }?.packageName

fun somethingIsPlaying(context: Context): Boolean = activeSessions(context).any { it.isPlaying() }

// Resumes the given app's session, if it still has one.
fun resumePlayer(context: Context, packageName: String): Boolean {
    val session = activeSessions(context).firstOrNull { it.packageName == packageName } ?: return false
    session.transportControls.play()
    return true
}

// Empty without the notification listener permission, which reading other
// apps' sessions requires.
private fun activeSessions(context: Context): List<MediaController> {
    val listener = ComponentName(context, SessionListener::class.java)
    val granted = context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(listener)
    if (!granted) return emptyList()
    return context.getSystemService(MediaSessionManager::class.java).getActiveSessions(listener)
}

fun openListenerSettings(context: Context) {
    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
        .putExtra(
            Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
            ComponentName(context, SessionListener::class.java).flattenToString(),
        )
    context.startActivity(intent)
}

private fun MediaController.title(): String? = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)

// Players fill either key, so try both.
private fun MediaMetadata.artwork(): Bitmap? =
    getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: getBitmap(MediaMetadata.METADATA_KEY_ART)

private fun MediaController.isPlaying(): Boolean = playbackState?.state.let {
    it == PlaybackState.STATE_PLAYING || it == PlaybackState.STATE_BUFFERING
}

// Prefer what is audible right now, otherwise the most recently active
// session: the system lists them most recent first.
fun <T> pickActive(sessions: List<T>, isPlaying: (T) -> Boolean): T? =
    sessions.firstOrNull(isPlaying) ?: sessions.firstOrNull()
