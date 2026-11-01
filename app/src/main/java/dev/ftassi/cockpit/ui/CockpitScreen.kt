package dev.ftassi.cockpit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import dev.ftassi.cockpit.launch.openApp
import dev.ftassi.cockpit.launch.openSpotify
import dev.ftassi.cockpit.launch.startVoiceAssist
import dev.ftassi.cockpit.nowplaying.NowPlayingState
import dev.ftassi.cockpit.spotify.SpotifyPlaylist
import dev.ftassi.cockpit.spotify.SpotifyState
import kotlinx.coroutines.delay

private const val DrawerTimeout = 10_000L
private val SpotifyPillHeight = 88.dp
private const val SpotifyRowSize = 4
private val Caption = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium)

@Composable
fun CockpitScreen(
    blocks: List<Block>,
    bar: List<Block>,
    nowPlaying: NowPlayingState,
    onPlay: (Block.Radio) -> Unit,
    onWaze: () -> Unit,
    spotify: SpotifyState,
    onSpotifyLogin: () -> Unit,
    onPlaylist: (String) -> Unit,
    onPlaylistLongPress: (SpotifyPlaylist) -> Unit,
) {
    val context = LocalContext.current
    // Screens opened on top of the home: only Group and Contacts.
    val stack = remember { mutableStateListOf<Block>() }
    BackHandler(enabled = stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }
    // A bar group open as a drawer over the screen.
    var drawer by remember { mutableStateOf<Block.Group?>(null) }
    BackHandler(enabled = drawer != null) { drawer = null }
    // Driving off with it open: the home must come back on its own.
    LaunchedEffect(drawer) {
        if (drawer != null) {
            delay(DrawerTimeout)
            drawer = null
        }
    }
    val logos = remember { mutableMapOf<String, Logo>() }
    val appIcons = remember { mutableMapOf<String, ImageBitmap?>() }

    val onBlockClick: (Block) -> Unit = { block ->
        when (block) {
            is Block.Group, Block.Contacts -> stack.add(block)
            is Block.Radio -> onPlay(block)
            is Block.Spotify -> openSpotify(context, block.uri)
            is Block.App -> openApp(context, block.packageName, block.name)
            Block.Waze -> onWaze()
            Block.Voice -> startVoiceAssist(context)
        }
    }
    // The radio Cockpit itself is playing, matched by the title it was given.
    val onAir = (nowPlaying as? NowPlayingState.Active)
        ?.takeIf { it.isPlaying && it.source == context.packageName }
        ?.title
    val toTile: (Block) -> Tile = { block ->
        val logo = (block as? Block.Radio)?.logo?.let { path -> logos.getOrPut(path) { loadLogo(context, path) } }
        block.toTile(onAir, logo) { onBlockClick(block) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp).padding(bottom = BottomBarHeight + Gap),
            verticalArrangement = Arrangement.spacedBy(Gap),
        ) {
            val content = Modifier.weight(1f)
            // Going back lives up here, so the bar's buttons never shift.
            if (stack.isNotEmpty()) BackPill { stack.removeAt(stack.lastIndex) }
            when (val screen = stack.lastOrNull()) {
                // Spotify gets a pill of its own under the stations, which take
                // the rest of the screen.
                null -> {
                    TileGrid(blocks.filter { it !is Block.Spotify }.map(toTile), content)
                    blocks.filterIsInstance<Block.Spotify>().forEach { SpotifyArea(it, spotify, onBlockClick, onSpotifyLogin, onPlaylist, onPlaylistLongPress) }
                }
                is Block.Group -> TileGrid(screen.blocks.map(toTile), content)
                else -> ContactsScreen(content)
            }
            NowPlayingBar(nowPlaying)
        }

        AnimatedVisibility(drawer != null, enter = fadeIn(tween(240)), exit = fadeOut(tween(180))) {
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { drawer = null },
            )
        }

        val buttons = bar.mapNotNull { block ->
            when (block) {
                Block.Contacts -> BarItem("Telefono", R.drawable.ph_phone) { onBlockClick(block) }
                Block.Waze -> BarItem("Waze", R.drawable.ph_navigation_arrow) { onBlockClick(block) }
                Block.Voice -> BarItem("Assistente vocale", R.drawable.ph_microphone) { onBlockClick(block) }
                is Block.Group -> {
                    val open = drawer == block
                    BarItem(block.name, if (open) R.drawable.ph_caret_down else block.icon?.drawable() ?: return@mapNotNull null, open) {
                        drawer = if (open) null else block
                    }
                }
                else -> null
            }
        }
        BottomBar(
            buttons,
            drawer,
            appIcon = { app -> appIcons.getOrPut(app.packageName) { loadAppIcon(context, app.packageName) } },
            onLaunch = { app ->
                drawer = null
                onBlockClick(app)
            },
            modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(16.dp),
        )
    }
}

@Composable
private fun BackPill(onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().height(64.dp), shape = CircleShape, color = SurfaceTone) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 24.dp)) {
            Icon(painterResource(R.drawable.ph_arrow_left), contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Text("Indietro", style = MaterialTheme.typography.titleLarge)
        }
    }
}

// The row of recent playlists when there are any, otherwise the plain pill: the
// app stays fully usable without the Spotify integration.
@Composable
private fun SpotifyArea(
    block: Block.Spotify,
    state: SpotifyState,
    onClick: (Block) -> Unit,
    onLogin: () -> Unit,
    onPlaylist: (String) -> Unit,
    onPlaylistLongPress: (SpotifyPlaylist) -> Unit,
) {
    when {
        state is SpotifyState.Connected && state.playlists.isNotEmpty() ->
            SpotifyRow(block, state.playlists, onClick, onPlaylist, onPlaylistLongPress)
        state is SpotifyState.TokenExpired -> Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
            SpotifyPill(block, onClick, Modifier.weight(1f))
            Surface(
                onClick = onLogin,
                modifier = Modifier.size(SpotifyPillHeight),
                shape = CircleShape,
                color = SpotifyTone,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.ph_arrows_clockwise),
                        contentDescription = "Rinnova accesso Spotify",
                        tint = SpotifyColor,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
        }
        // Long press because it is needed once, and the pill is all there is.
        state is SpotifyState.NotConnected -> SpotifyPill(block, onClick, Modifier.fillMaxWidth(), onLongClick = onLogin)
        else -> SpotifyPill(block, onClick, Modifier.fillMaxWidth())
    }
}

// Four equal squares, whatever the number of playlists, so they keep their size.
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpotifyRow(
    block: Block.Spotify,
    playlists: List<SpotifyPlaylist>,
    onClick: (Block) -> Unit,
    onPlaylist: (String) -> Unit,
    onLongPress: (SpotifyPlaylist) -> Unit,
) {
    val shown = playlists.take(SpotifyRowSize - 1)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Surface(
                onClick = { onClick(block) },
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                shape = RoundedCornerShape(18.dp),
                color = SpotifyTone,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ph_spotify_logo), contentDescription = block.name, tint = SpotifyColor, modifier = Modifier.size(40.dp))
                }
            }
            Text(block.name, style = Caption, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        for (playlist in shown) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .combinedClickable(onLongClick = { onLongPress(playlist) }) { onPlaylist(playlist.uri) },
                    shape = RoundedCornerShape(18.dp),
                    color = SpotifyTone,
                ) {
                    playlist.cover?.let {
                        Image(it, contentDescription = playlist.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
                Text(playlist.name, style = Caption, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        repeat(SpotifyRowSize - 1 - shown.size) { Spacer(Modifier.weight(1f)) }
    }
}

// Shown on its own when there are no recents to offer.
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpotifyPill(block: Block.Spotify, onClick: (Block) -> Unit, modifier: Modifier, onLongClick: (() -> Unit)? = null) {
    Surface(
        modifier = modifier.height(SpotifyPillHeight)
            .clip(CircleShape)
            .combinedClickable(onLongClick = onLongClick) { onClick(block) },
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
    Icon.Apps -> R.drawable.ph_squares_four
}

private fun Block.toTile(onAir: String?, logo: Logo?, onClick: () -> Unit): Tile = when (this) {
    is Block.Group -> Tile(name, icon = icon?.drawable(), iconTint = RadioColor, onClick = onClick)
    is Block.Radio -> Tile(name, RadioTone, logo = logo, playing = name == onAir, onClick = onClick)
    is Block.App -> Tile(name, onClick = onClick)
    is Block.Spotify -> Tile(name, SpotifyTone, R.drawable.ph_spotify_logo, SpotifyColor, onClick = onClick)
    Block.Contacts -> Tile("Telefono", icon = R.drawable.ph_phone, onClick = onClick)
    Block.Waze -> Tile("Waze", icon = R.drawable.ph_navigation_arrow, onClick = onClick)
    Block.Voice -> Tile("Assistente vocale", icon = R.drawable.ph_microphone, onClick = onClick)
}
