package dev.ftassi.cockpit.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ftassi.cockpit.R
import dev.ftassi.cockpit.config.Block
import dev.ftassi.cockpit.config.Icon
import dev.ftassi.cockpit.launch.openSpotify
import dev.ftassi.cockpit.launch.openWaze
import dev.ftassi.cockpit.launch.startVoiceAssist
import dev.ftassi.cockpit.nowplaying.NowPlayingState

private val BarHeight = 76.dp
private val HomeTilesHeight = 160.dp

@Composable
fun CockpitScreen(
    blocks: List<Block>,
    bar: List<Block>,
    nowPlaying: NowPlayingState,
    recentRadios: List<Block.Radio>,
    carModeActive: Boolean,
    onPlay: (Block.Radio) -> Unit,
    onEnterCarMode: () -> Unit,
    onExitCarMode: () -> Unit,
) {
    val context = LocalContext.current
    // Screens opened on top of the root grid: only Group and Contacts.
    val stack = remember { mutableStateListOf<Block>() }
    BackHandler(enabled = stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }

    val onBlockClick: (Block) -> Unit = { block ->
        when (block) {
            is Block.Group, Block.Contacts -> stack.add(block)
            is Block.Radio -> onPlay(block)
            is Block.Spotify -> openSpotify(context, block.uri)
            Block.Waze -> openWaze(context)
            Block.Voice -> startVoiceAssist(context)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(Gap),
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        val content = Modifier.weight(1f)
        when (val screen = stack.lastOrNull()) {
            null -> if (recentRadios.isEmpty()) {
                TileGrid(blocks.map { it.toTile(onBlockClick) }, content)
            } else {
                TileGrid(blocks.map { it.toTile(onBlockClick) }, Modifier.height(HomeTilesHeight))
                Recents(recentRadios, onPlay, content)
            }
            is Block.Group -> TileGrid(screen.blocks.map { it.toTile(onBlockClick) }, content)
            else -> ContactsScreen(content)
        }

        NowPlayingBar(nowPlaying)

        Row(
            modifier = Modifier.height(BarHeight),
            horizontalArrangement = Arrangement.spacedBy(Gap),
        ) {
            val barModifier = Modifier.weight(1f)
            val barText = MaterialTheme.typography.titleLarge
            if (stack.isNotEmpty()) {
                TileBox(Tile("Indietro", R.drawable.ph_arrow_left) { stack.removeAt(stack.lastIndex) }, barModifier, barText, BarIconSize)
            }
            for (block in bar) {
                TileBox(block.toTile(onBlockClick), barModifier, barText, BarIconSize)
            }
            if (carModeActive) {
                TileBox(Tile("Esci dalla modalità auto", R.drawable.ph_power, onClick = onExitCarMode), barModifier, barText, BarIconSize)
            } else {
                TileBox(Tile("Modalità auto", R.drawable.ph_car, Accent, onEnterCarMode), barModifier, barText, BarIconSize)
            }
        }
    }
}

// Three per row, cover on top and title below; radios have no cover yet,
// so they show the radio icon instead.
@Composable
private fun Recents(radios: List<Block.Radio>, onPlay: (Block.Radio) -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Gap)) {
        Text("RECENTI", style = SectionLabel, color = Muted)
        for (row in radios.chunked(3)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (radio in row) {
                    Recent(radio.name, "RADIO", R.drawable.ph_radio, RadioColor, Modifier.weight(1f)) { onPlay(radio) }
                }
                // Keep the columns aligned when the last row is not full.
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Recent(title: String, kind: String, @DrawableRes icon: Int, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f)
                .border(1.5.dp, Outline, RoundedCornerShape(10.dp))
                .background(Placeholder, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(40.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(kind, style = SectionLabel, color = Muted)
    }
}

private val SectionLabel = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
private val BarIconSize = 30.dp

private fun Block.toTile(onClick: (Block) -> Unit): Tile {
    val click = { onClick(this) }
    return when (this) {
        is Block.Group -> when (icon) {
            Icon.Radio -> Tile(name, R.drawable.ph_radio, RadioColor, click)
            Icon.Spotify -> Tile(name, R.drawable.ph_spotify_logo, SpotifyColor, click)
            null -> Tile(name, onClick = click)
        }
        is Block.Radio -> Tile(name, onClick = click)
        // Without a uri it opens the app itself, so the logo says it all.
        is Block.Spotify -> if (uri == null) Tile(name, R.drawable.ph_spotify_logo, SpotifyColor, click) else Tile(name, onClick = click)
        Block.Contacts -> Tile("Telefono", R.drawable.ph_phone, onClick = click)
        Block.Waze -> Tile("Waze", R.drawable.ph_navigation_arrow, onClick = click)
        Block.Voice -> Tile("Assistente vocale", R.drawable.ph_microphone, onClick = click)
    }
}
