package dev.ftassi.cockpit.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ftassi.cockpit.R
import dev.ftassi.cockpit.nowplaying.NowPlayingState
import dev.ftassi.cockpit.nowplaying.openListenerSettings

private val BarHeight = 88.dp
private val Shape = RoundedCornerShape(14.dp)

@Composable
fun NowPlayingBar(state: NowPlayingState) {
    val context = LocalContext.current
    when (state) {
        NowPlayingState.NeedsAccess -> TileBox(
            Tile("Consenti accesso notifiche", tint = Accent) { openListenerSettings(context) },
            Modifier.fillMaxWidth().height(BarHeight),
            MaterialTheme.typography.titleLarge,
        )
        NowPlayingState.Idle -> Surface(
            modifier = Modifier.fillMaxWidth().height(BarHeight),
            shape = Shape,
            border = BorderStroke(1.5.dp, Outline),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 20.dp)) {
                Text("Niente in riproduzione", style = MaterialTheme.typography.titleLarge, color = Muted)
            }
        }
        is NowPlayingState.Active -> Surface(
            modifier = Modifier.fillMaxWidth().height(BarHeight),
            shape = Shape,
            border = BorderStroke(1.5.dp, Accent),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(12.dp),
            ) {
                val cover = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp))
                if (state.artwork != null) {
                    Image(state.artwork.asImageBitmap(), contentDescription = null, modifier = cover, contentScale = ContentScale.Crop)
                } else {
                    Box(cover.background(Placeholder))
                }
                Column(modifier = Modifier.weight(1f)) {
                    state.subtitle?.let {
                        Text(
                            it.uppercase(),
                            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.5.sp),
                            color = Accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        state.title,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Surface(
                    onClick = { state.togglePlayPause() },
                    modifier = Modifier.size(64.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Accent,
                    contentColor = Color.Black,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(if (state.isPlaying) R.drawable.ph_pause else R.drawable.ph_play),
                            contentDescription = if (state.isPlaying) "Pausa" else "Riproduci",
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
        }
    }
}
