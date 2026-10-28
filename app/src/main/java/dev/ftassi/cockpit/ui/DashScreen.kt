package dev.ftassi.cockpit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.ftassi.cockpit.config.Block

private val gap = 12.dp

// Two blocks per row, rows share the screen height equally; an odd last
// block takes the whole row. No scrolling: everything must be reachable
// without looking for it.
@Composable
fun DashScreen(blocks: List<Block>, onBlockClick: (Block) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(gap),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        for (row in blocks.chunked(2)) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                for (block in row) {
                    BlockTile(block, onClick = { onBlockClick(block) }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun BlockTile(block: Block, onClick: () -> Unit, modifier: Modifier) {
    val (label, color) = when (block) {
        is Block.Radio -> block.name to Color(0xFF8E44AD)
        is Block.Spotify -> "Spotify" to Color(0xFF1DB954)
        Block.Waze -> "Waze" to Color(0xFF33CCFF)
        Block.Maps -> "Maps" to Color(0xFF4285F4)
        Block.Phone -> "Telefono" to Color(0xFF2E7D32)
    }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(24.dp),
        color = color,
        contentColor = Color.White,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(gap),
            )
        }
    }
}
