package dev.ftassi.cockpit.ui

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import dev.ftassi.cockpit.layout.gridSlots
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

val Gap = 12.dp
private val MinRowHeight = 120.dp
// Few tiles would otherwise stretch into tall slabs.
private val MaxRowHeight = 200.dp
private val Shape = RoundedCornerShape(24.dp)
private val Badge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.3.sp)

// The tile around a logo takes the colour of the logo's edge, so the logo
// can be shown whole on a tile of any proportion without a visible seam.
data class Logo(val image: ImageBitmap, val background: Color)

// A logo replaces everything else: it says which station it is on its own.
data class Tile(
    val label: String,
    val color: Color = SurfaceTone,
    @DrawableRes val icon: Int? = null,
    val iconTint: Color = Color.White,
    val logo: Logo? = null,
    val playing: Boolean = false,
    val onClick: () -> Unit,
)

// Two tiles per row; an odd last tile takes the whole row. Rows share the
// available height, and only scroll when that would make them too small to
// hit without looking. Positions never move, so they can be found by memory.
@Composable
fun TileGrid(tiles: List<Tile>, modifier: Modifier = Modifier) {
    val rows = tiles.chunked(2)
    BoxWithConstraints(modifier) {
        val fitted = (maxHeight - Gap * (rows.size - 1)) / rows.size
        val rowHeight = fitted.coerceIn(MinRowHeight, MaxRowHeight)
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

// The home's grid while reorganizing: the same rows, same place, tiles that slide.
@Composable
fun ReorderableTileGrid(items: List<Pair<String, Tile>>, onDrop: (List<String>) -> Unit, modifier: Modifier = Modifier) {
    val rows = (items.size + 1) / 2
    BoxWithConstraints(modifier) {
        val rowHeight = ((maxHeight - Gap * (rows - 1)) / rows).coerceIn(MinRowHeight, MaxRowHeight)
        val slots = with(LocalDensity.current) {
            gridSlots(items.size, 2, maxWidth.toPx(), rowHeight.toPx(), Gap.toPx(), fullLastRow = true)
        }
        val tiles = items.toMap()
        ReorderableArea(items.map { it.first }, slots, onDrop, Modifier.fillMaxSize()) { key ->
            tiles[key]?.let { TileBox(it, Modifier.fillMaxSize()) }
        }
    }
}

@Composable
fun TileBox(tile: Tile, modifier: Modifier, textStyle: TextStyle = MaterialTheme.typography.headlineMedium) {
    // Without a logo the playing tile turns coral; a logo keeps its colours
    // and gets a coral frame instead.
    val filled = tile.playing && tile.logo == null
    Surface(
        onClick = tile.onClick,
        modifier = modifier.fillMaxSize(),
        shape = Shape,
        color = when {
            filled -> RadioColor
            tile.logo != null -> tile.logo.background
            else -> tile.color
        },
        contentColor = if (filled) Color.Black else Color.White,
        border = if (tile.playing && tile.logo != null) BorderStroke(4.dp, RadioColor) else null,
    ) {
        if (tile.logo != null) {
            Box {
                Image(tile.logo.image, contentDescription = tile.label, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                if (tile.playing) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                        shape = RoundedCornerShape(50),
                        color = RadioColor,
                        contentColor = Color.Black,
                    ) {
                        Text("● IN ONDA", style = Badge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                when {
                    tile.playing -> Text("● IN ONDA", style = Badge)
                    tile.icon != null -> Icon(painterResource(tile.icon), contentDescription = null, tint = tile.iconTint, modifier = Modifier.size(30.dp))
                    else -> Box {}
                }
                Text(tile.label, style = textStyle, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// Transparent logos (no solid edge) sit on the plain tile surface.
fun loadLogo(context: Context, path: String): Logo {
    val bitmap = context.assets.open(path).use { BitmapFactory.decodeStream(it) }
    val edge = Color(bitmap.getPixel(0, 0))
    return Logo(bitmap.asImageBitmap(), if (edge.alpha == 1f) edge else SurfaceTone)
}

// The app's own launcher icon; null when the app is not installed.
fun loadAppIcon(context: Context, packageName: String): ImageBitmap? = try {
    context.packageManager.getApplicationIcon(packageName).toBitmap(256, 256).asImageBitmap()
} catch (_: PackageManager.NameNotFoundException) {
    null
}
