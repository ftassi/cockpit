package dev.ftassi.cockpit.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ftassi.cockpit.R
import dev.ftassi.cockpit.nowplaying.NowPlayingState
import dev.ftassi.cockpit.nowplaying.openListenerSettings

private const val SPOTIFY = "com.spotify.music"
private val BarHeight = 80.dp
private val Label = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.3.sp)

// A compact pill above the stations: a glance at what plays, and pause.
@Composable
fun NowPlayingBar(state: NowPlayingState) {
    val context = LocalContext.current
    when (state) {
        NowPlayingState.NeedsAccess -> TileBox(
            Tile("Consenti accesso notifiche", color = RadioTone) { openListenerSettings(context) },
            Modifier.fillMaxWidth().height(BarHeight),
            MaterialTheme.typography.titleLarge,
        )
        NowPlayingState.Idle -> Surface(
            modifier = Modifier.fillMaxWidth().height(BarHeight),
            shape = CircleShape,
            color = SurfaceTone,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 24.dp)) {
                Text("Niente in riproduzione", style = MaterialTheme.typography.titleLarge, color = Muted)
            }
        }
        is NowPlayingState.Active -> Surface(
            modifier = Modifier.fillMaxWidth().height(BarHeight),
            shape = CircleShape,
            color = SurfaceTone,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(start = if (state.artwork == null) 24.dp else 8.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
            ) {
                if (state.artwork != null) {
                    Image(
                        state.artwork.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    (if (state.connecting) "Connessione…" else state.subtitle)?.let {
                        Text(
                            "● ${it.uppercase()}",
                            style = Label,
                            color = when (state.source) {
                                context.packageName -> RadioColor
                                SPOTIFY -> SpotifyColor
                                else -> Muted
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        state.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Surface(
                    onClick = { state.togglePlayPause() },
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    color = Accent,
                    contentColor = Color.Black,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(if (state.isPlaying || state.connecting) R.drawable.ph_pause else R.drawable.ph_play),
                            contentDescription = if (state.isPlaying || state.connecting) "Pausa" else "Riproduci",
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
        }
    }
}
