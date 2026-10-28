package dev.ftassi.cockpit.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.ftassi.cockpit.R
import dev.ftassi.cockpit.config.Block
import dev.ftassi.cockpit.config.Icon
import dev.ftassi.cockpit.launch.openApp
import dev.ftassi.cockpit.launch.openSpotify
import dev.ftassi.cockpit.launch.openWaze
import dev.ftassi.cockpit.launch.startVoiceAssist
import dev.ftassi.cockpit.nowplaying.NowPlayingState

private val BarHeight = 84.dp
private val SpotifyPillHeight = 88.dp

@Composable
fun CockpitScreen(
    blocks: List<Block>,
    bar: List<Block>,
    nowPlaying: NowPlayingState,
    onPlay: (Block.Radio) -> Unit,
) {
    val context = LocalContext.current
    // Screens opened on top of the home: only Group and Contacts.
    val stack = remember { mutableStateListOf<Block>() }
    BackHandler(enabled = stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }
    val logos = remember { mutableMapOf<String, Logo?>() }

    val onBlockClick: (Block) -> Unit = { block ->
        when (block) {
            is Block.Group, Block.Contacts -> stack.add(block)
            is Block.Radio -> onPlay(block)
            is Block.Spotify -> openSpotify(context, block.uri)
            is Block.App -> openApp(context, block.packageName, block.name)
            Block.Waze -> openWaze(context)
            Block.Voice -> startVoiceAssist(context)
        }
    }
    // The radio Cockpit itself is playing, matched by the title it was given.
    val onAir = (nowPlaying as? NowPlayingState.Active)
        ?.takeIf { it.isPlaying && it.source == context.packageName }
        ?.title
    val toTile: (Block) -> Tile = { block ->
        val logo = when (block) {
            is Block.Radio -> block.logo?.let { path -> logos.getOrPut(path) { loadLogo(context, path) } }
            is Block.App -> logos.getOrPut(block.packageName) { loadAppIcon(context, block.packageName) }
            else -> null
        }
        block.toTile(onAir, logo) { onBlockClick(block) }
    }

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        val content = Modifier.weight(1f)
        when (val screen = stack.lastOrNull()) {
            // Spotify gets a pill of its own under the stations, which take the
            // rest of the screen.
            null -> {
                TileGrid(blocks.filter { it !is Block.Spotify }.map(toTile), content)
                blocks.filterIsInstance<Block.Spotify>().forEach { SpotifyPill(it, onBlockClick) }
            }
            is Block.Group -> TileGrid(screen.blocks.map(toTile), content)
            else -> ContactsScreen(content)
        }

        NowPlayingBar(nowPlaying)

        Surface(
            modifier = Modifier.fillMaxWidth().height(BarHeight),
            shape = RoundedCornerShape(28.dp),
            color = SurfaceTone,
        ) {
            Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val slot = Modifier.weight(1f)
                if (stack.isNotEmpty()) {
                    BarButton("Indietro", R.drawable.ph_arrow_left, slot) { stack.removeAt(stack.lastIndex) }
                }
                for (block in bar) {
                    val (label, icon) = when (block) {
                        Block.Contacts -> "Telefono" to R.drawable.ph_phone
                        Block.Waze -> "Waze" to R.drawable.ph_navigation_arrow
                        Block.Voice -> "Assistente vocale" to R.drawable.ph_microphone
                        is Block.Group -> block.name to (block.icon?.drawable() ?: continue)
                        else -> continue
                    }
                    BarButton(label, icon, slot) { onBlockClick(block) }
                }
            }
        }
    }
}

@Composable
private fun BarButton(label: String, @DrawableRes icon: Int, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.fillMaxHeight(), shape = RoundedCornerShape(22.dp), color = SurfaceTone) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = label, modifier = Modifier.size(32.dp))
        }
    }
}

// A full-width pill under the stations, until Spotify recents give it
// something to show next to the logo.
@Composable
private fun SpotifyPill(block: Block.Spotify, onClick: (Block) -> Unit) {
    Surface(
        onClick = { onClick(block) },
        modifier = Modifier.fillMaxWidth().height(SpotifyPillHeight),
        shape = CircleShape,
        color = SpotifyTone,
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ph_spotify_logo), contentDescription = null, tint = SpotifyColor, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            Text(block.name, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

private fun Icon.drawable() = when (this) {
    Icon.Search -> R.drawable.ph_magnifying_glass
    Icon.Car -> R.drawable.ph_car
}

private fun Block.toTile(onAir: String?, logo: Logo?, onClick: () -> Unit): Tile = when (this) {
    is Block.Group -> Tile(name, icon = icon?.drawable(), iconTint = RadioColor, onClick = onClick)
    is Block.Radio -> Tile(name, RadioTone, logo = logo, playing = name == onAir, onClick = onClick)
    is Block.App -> Tile(name, logo = logo, onClick = onClick)
    is Block.Spotify -> Tile(name, SpotifyTone, R.drawable.ph_spotify_logo, SpotifyColor, onClick = onClick)
    Block.Contacts -> Tile("Telefono", icon = R.drawable.ph_phone, onClick = onClick)
    Block.Waze -> Tile("Waze", icon = R.drawable.ph_navigation_arrow, onClick = onClick)
    Block.Voice -> Tile("Assistente vocale", icon = R.drawable.ph_microphone, onClick = onClick)
}
