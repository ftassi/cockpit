package dev.ftassi.cockpit.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ftassi.cockpit.R
import dev.ftassi.cockpit.config.Block

val BottomBarHeight = 84.dp
private val DrawerTone = Color(0xFF1C2026)
private val SelectedSlot = Color(0xFF2A2F36)
private const val DrawerColumns = 3
private val DrawerLabel = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium)

// One button per bar block; a group opens its apps in a drawer that grows the
// bar upwards, so the buttons below never move.
@Composable
fun BottomBar(
    buttons: List<BarItem>,
    drawer: Block.Group?,
    appIcon: (Block.App) -> ImageBitmap?,
    onLaunch: (Block) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keeps the contents on screen while the drawer animates closed.
    var shown by remember { mutableStateOf(drawer) }
    if (drawer != null) shown = drawer

    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp), color = SurfaceTone) {
        Column(modifier = Modifier.padding(8.dp)) {
            AnimatedVisibility(
                visible = drawer != null,
                enter = expandVertically(tween(240)) + fadeIn(tween(150, delayMillis = 60)),
                exit = fadeOut(tween(100)) + shrinkVertically(tween(180)),
            ) {
                shown?.let { DrawerGrid(it.blocks, appIcon, onLaunch) }
            }
            Row(modifier = Modifier.height(BottomBarHeight - 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (item in buttons) {
                    Surface(
                        onClick = item.onClick,
                        modifier = Modifier.weight(1f).height(BottomBarHeight - 16.dp),
                        shape = CircleShape,
                        color = if (item.selected) SelectedSlot else SurfaceTone,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(painterResource(item.icon), contentDescription = item.label, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
        }
    }
}

data class BarItem(val label: String, @DrawableRes val icon: Int, val selected: Boolean = false, val onClick: () -> Unit)

// Three to a row, left-aligned; never scrolls, the config keeps it short.
// Only apps belong in a drawer.
@Composable
private fun DrawerGrid(blocks: List<Block>, appIcon: (Block.App) -> ImageBitmap?, onLaunch: (Block) -> Unit) {
    Surface(
        modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = DrawerTone,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in blocks.filterIsInstance<Block.App>().chunked(DrawerColumns)) {
                Row {
                    for (app in row) {
                        DrawerItem(app.name, appIcon(app), Modifier.weight(1f)) { onLaunch(app) }
                    }
                    repeat(DrawerColumns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun DrawerItem(label: String, icon: ImageBitmap?, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // A missing app keeps its slot; tapping it says it is not installed.
        if (icon != null) {
            Image(icon, contentDescription = null, modifier = Modifier.size(60.dp))
        } else {
            Box(Modifier.size(60.dp).background(SurfaceTone, CircleShape))
        }
        Text(label, style = DrawerLabel, color = Color(0xFFB8BEC6), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
