package dev.ftassi.cockpit.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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

private val BarHeight = 96.dp

@Composable
fun CockpitScreen(blocks: List<Block>) {
    val context = LocalContext.current
    // Screens opened on top of the root grid: only Group and Contacts.
    val stack = remember { mutableStateListOf<Block>() }
    BackHandler(enabled = stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }

    val onBlockClick: (Block) -> Unit = { block ->
        when (block) {
            is Block.Group, Block.Contacts -> stack.add(block)
            else -> launch(context, block)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(Gap),
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        val content = Modifier.weight(1f)
        when (val screen = stack.lastOrNull()) {
            null -> TileGrid(blocks.map { it.toTile(onBlockClick) }, content)
            is Block.Group -> TileGrid(screen.blocks.map { it.toTile(onBlockClick) }, content)
            else -> ContactsScreen(content)
        }

        Row(
            modifier = Modifier.height(BarHeight),
            horizontalArrangement = Arrangement.spacedBy(Gap),
        ) {
            if (stack.isNotEmpty()) {
                TileBox(
                    Tile("← Indietro", Color(0xFF424242)) { stack.removeAt(stack.lastIndex) },
                    Modifier.weight(1f),
                )
            }
            TileBox(
                Tile("🎤 Voce", Color(0xFFC62828)) { startVoiceAssist(context) },
                Modifier.weight(1f),
            )
        }
    }
}

private fun launch(context: Context, block: Block) {
    when (block) {
        is Block.Spotify -> openSpotify(context, block.uri)
        Block.Waze -> openWaze(context)
        is Block.Radio -> Unit // Playback arrives with RadioService.
        is Block.Group, Block.Contacts -> error("$block is a screen, not an action")
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
