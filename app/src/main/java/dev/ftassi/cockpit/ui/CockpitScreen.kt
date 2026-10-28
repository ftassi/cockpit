package dev.ftassi.cockpit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.ftassi.cockpit.config.Block
import dev.ftassi.cockpit.launch.openSpotify
import dev.ftassi.cockpit.launch.openWaze
import dev.ftassi.cockpit.launch.startVoiceAssist
import dev.ftassi.cockpit.nowplaying.NowPlayingState

private val BarHeight = 96.dp
private val RecentHeight = 112.dp

@Composable
fun CockpitScreen(
    blocks: List<Block>,
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
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(Gap),
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        val content = Modifier.weight(1f)
        when (val screen = stack.lastOrNull()) {
            null -> {
                if (recentRadios.isNotEmpty()) Recents(recentRadios.map { it.toTile(onBlockClick) })
                TileGrid(blocks.map { it.toTile(onBlockClick) }, content)
            }
            is Block.Group -> TileGrid(screen.blocks.map { it.toTile(onBlockClick) }, content)
            else -> ContactsScreen(content)
        }

        NowPlayingBar(nowPlaying)

        Row(
            modifier = Modifier.height(BarHeight),
            horizontalArrangement = Arrangement.spacedBy(Gap),
        ) {
            if (stack.isNotEmpty()) {
                TileBox(
                    Tile("Indietro", Color(0xFF424242)) { stack.removeAt(stack.lastIndex) },
                    Modifier.weight(1f),
                )
            }
            TileBox(
                Tile("Voce", Color(0xFFC62828)) { startVoiceAssist(context) },
                Modifier.weight(1f),
            )
            if (carModeActive) {
                TileBox(Tile("Esci", Color(0xFF6D4C41), onExitCarMode), Modifier.weight(1f))
            } else {
                TileBox(Tile("Auto", Color(0xFF00695C), onEnterCarMode), Modifier.weight(1f))
            }
        }
    }
}

// Smaller than the main blocks, up to three per row; each row shares its
// width among however many recents it holds, so any count fills the screen.
@Composable
private fun Recents(tiles: List<Tile>) {
    for (row in tiles.chunked(3)) {
        Row(modifier = Modifier.height(RecentHeight), horizontalArrangement = Arrangement.spacedBy(Gap)) {
            for (tile in row) {
                TileBox(tile, Modifier.weight(1f), MaterialTheme.typography.titleLarge)
            }
        }
    }
}

private fun Block.toTile(onClick: (Block) -> Unit): Tile {
    val (label, color) = when (this) {
        is Block.Group -> name to Color(0xFF455A64)
        is Block.Radio -> name to Color(0xFF8E44AD)
        is Block.Spotify -> name to Color(0xFF1DB954)
        Block.Contacts -> "Telefono" to Color(0xFF2E7D32)
        Block.Waze -> "Waze" to Color(0xFF33CCFF)
    }
    return Tile(label, color) { onClick(this) }
}
