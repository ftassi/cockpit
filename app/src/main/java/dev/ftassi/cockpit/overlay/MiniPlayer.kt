package dev.ftassi.cockpit.overlay

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.edit
import dev.ftassi.cockpit.MainActivity
import dev.ftassi.cockpit.R
import dev.ftassi.cockpit.nowplaying.NowPlayingState
import dev.ftassi.cockpit.nowplaying.NowPlayingTracker
import dev.ftassi.cockpit.ui.Accent
import dev.ftassi.cockpit.ui.SurfaceTone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val BUTTON_DP = 56
private const val PADDING_DP = 4
private const val X = "x"
private const val Y = "y"

// A small draggable pill over Waze: play/pause and the way back to Cockpit.
// Plain views, because Compose in a window without an activity would need its
// own lifecycle plumbing for two buttons.
class MiniPlayer(context: Context) {
    private val context = context.applicationContext
    private val windows = this.context.getSystemService(WindowManager::class.java)
    private val prefs = this.context.getSharedPreferences("mini_player", Context.MODE_PRIVATE)
    private val tracker = NowPlayingTracker(this.context)
    private var pill: View? = null
    private var following: Job? = null

    val canShow: Boolean get() = Settings.canDrawOverlays(context)

    // keepScreenOn: over another app, Cockpit's own window flag no longer applies.
    fun show(keepScreenOn: Boolean) {
        if (pill != null) return
        val density = context.resources.displayMetrics.density
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                (if (keepScreenOn) WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON else 0),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // On the far map, level with Waze's own round buttons on the right:
            // lower down it would sit next to the car and the coming turn.
            x = prefs.getInt(X, (16 * density).toInt())
            y = prefs.getInt(Y, (context.resources.displayMetrics.heightPixels * 0.17f).toInt())
        }

        val button = (BUTTON_DP * density).toInt()
        val playPause = ImageView(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Accent.toArgb())
            }
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(button / 4, button / 4, button / 4, button / 4)
            contentDescription = "Riproduci o metti in pausa"
            setColorFilter(Color.BLACK)
        }
        val openCockpit = ImageView(context).apply {
            setImageResource(R.drawable.ph_squares_four)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(button / 4, button / 4, button / 4, button / 4)
            contentDescription = "Apri Cockpit"
        }
        val root = LinearLayout(context).apply {
            // Upright, so it takes a narrow strip along the edge of the map.
            orientation = LinearLayout.VERTICAL
            val padding = (PADDING_DP * density).toInt()
            setPadding(padding, padding, padding, padding)
            background = GradientDrawable().apply {
                cornerRadius = button.toFloat()
                setColor(SurfaceTone.toArgb())
            }
            addView(playPause, LinearLayout.LayoutParams(button, button))
            addView(openCockpit, LinearLayout.LayoutParams(button, button))
        }

        following = CoroutineScope(Dispatchers.Main.immediate).launch {
            tracker.state.collect { state ->
                val playing = state is NowPlayingState.Active && (state.isPlaying || state.connecting)
                playPause.setImageResource(if (playing) R.drawable.ph_pause else R.drawable.ph_play)
            }
        }
        tracker.start()

        fun moves(view: View, onTap: () -> Unit) = dragging(view, root, params, onTap)
        moves(root) {}
        moves(playPause) { (tracker.state.value as? NowPlayingState.Active)?.togglePlayPause() }
        moves(openCockpit) {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        windows.addView(root, params)
        pill = root
    }

    fun hide() {
        val shown = pill ?: return
        windows.removeView(shown)
        pill = null
        following?.cancel()
        tracker.stop()
    }

    // Dragging starts only after holding it: on a bumpy road a shaky tap must
    // still be a tap, not lose the command and move the pill.
    private fun dragging(view: View, root: View, params: WindowManager.LayoutParams, onTap: () -> Unit) {
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    val held = event.eventTime - event.downTime >= ViewConfiguration.getLongPressTimeout()
                    if (held && (abs(dx) > slop || abs(dy) > slop)) dragging = true
                    if (dragging) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        windows.updateViewLayout(root, params)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        val screen = context.resources.displayMetrics
                        params.x = params.x.coerceIn(0, (screen.widthPixels - root.width).coerceAtLeast(0))
                        params.y = params.y.coerceIn(0, (screen.heightPixels - root.height).coerceAtLeast(0))
                        windows.updateViewLayout(root, params)
                        prefs.edit {
                            putInt(X, params.x)
                            putInt(Y, params.y)
                        }
                    } else {
                        onTap()
                    }
                }
            }
            true
        }
    }
}

fun openOverlaySettings(context: Context) {
    val uri = Uri.parse("package:${context.packageName}")
    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri))
}
