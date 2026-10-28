package dev.ftassi.cockpit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max

val Gap = 12.dp
private val MinRowHeight = 160.dp

data class Tile(val label: String, val color: Color, val onClick: () -> Unit)

// Two tiles per row; an odd last tile takes the whole row. Rows share the
// available height, and only scroll when that would make them too small to
// hit without looking.
@Composable
fun TileGrid(tiles: List<Tile>, modifier: Modifier = Modifier) {
    val rows = tiles.chunked(2)
    BoxWithConstraints(modifier) {
        val fitted = (maxHeight - Gap * (rows.size - 1)) / rows.size
        val rowHeight = max(fitted, MinRowHeight)
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Gap),
        ) {
            for (row in rows) {
                Row(
                    modifier = Modifier.height(rowHeight),
                    horizontalArrangement = Arrangement.spacedBy(Gap),
                ) {
                    for (tile in row) {
                        TileBox(tile, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun TileBox(tile: Tile, modifier: Modifier, textStyle: TextStyle = MaterialTheme.typography.headlineLarge) {
    Surface(
        onClick = tile.onClick,
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(24.dp),
        color = tile.color,
        contentColor = Color.White,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                tile.label,
                style = textStyle,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(Gap),
            )
        }
    }
}
