package dev.ftassi.cockpit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ftassi.cockpit.nowplaying.NowPlayingState
import dev.ftassi.cockpit.nowplaying.openListenerSettings

private val BarHeight = 112.dp
private val BarColor = Color(0xFF263238)

@Composable
fun NowPlayingBar(state: NowPlayingState) {
    val context = LocalContext.current
    when (state) {
        NowPlayingState.NeedsAccess -> TileBox(
            Tile("Consenti accesso notifiche", Color(0xFFEF6C00)) { openListenerSettings(context) },
            Modifier.fillMaxWidth().height(BarHeight),
        )
        NowPlayingState.Idle -> Surface(
            modifier = Modifier.fillMaxWidth().height(BarHeight),
            shape = RoundedCornerShape(24.dp),
            color = BarColor,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 24.dp)) {
                Text("Niente in riproduzione", style = MaterialTheme.typography.titleLarge, color = Color.Gray)
            }
        }
        is NowPlayingState.Active -> Row(
            modifier = Modifier.fillMaxWidth().height(BarHeight),
            horizontalArrangement = Arrangement.spacedBy(Gap),
        ) {
            Surface(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                shape = RoundedCornerShape(24.dp),
                color = BarColor,
            ) {
                Column(verticalArrangement = Arrangement.Center, modifier = Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        state.title,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    state.subtitle?.let {
                        Text(it, style = MaterialTheme.typography.titleMedium, color = Color.LightGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Surface(
                onClick = { state.togglePlayPause() },
                modifier = Modifier.fillMaxHeight().aspectRatio(1f),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF1565C0),
            ) {
                PlayPauseIcon(isPlaying = state.isPlaying, modifier = Modifier.padding(36.dp))
            }
        }
    }
}

// Drawn by hand: text glyphs like ⏸ render as colored emoji, and the icon
// library would be a dependency for two shapes.
@Composable
private fun PlayPauseIcon(isPlaying: Boolean, modifier: Modifier) {
    Canvas(modifier.fillMaxSize()) {
        if (isPlaying) {
            val bar = size.width / 3
            drawRect(Color.White, size = Size(bar, size.height))
            drawRect(Color.White, topLeft = Offset(size.width - bar, 0f), size = Size(bar, size.height))
        } else {
            val triangle = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, size.height / 2)
                lineTo(0f, size.height)
                close()
            }
            drawPath(triangle, Color.White)
        }
    }
}
