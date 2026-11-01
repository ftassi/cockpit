package dev.ftassi.cockpit.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ftassi.cockpit.R
import dev.ftassi.cockpit.config.Block
import dev.ftassi.cockpit.directory.RadioDirectory
import dev.ftassi.cockpit.directory.Station
import dev.ftassi.cockpit.directory.searchResults
import dev.ftassi.cockpit.recents.RecentRadios
import kotlinx.coroutines.delay

private const val SEARCH_DELAY_MS = 400L
private const val RECENT_SLOTS = 4
private val RowHeight = 72.dp

// The chart of Italian stations, a search over the whole catalogue, and the
// last ones played from here where the home has its Spotify row.
@Composable
fun DirectoryScreen(onAir: String?, onPlay: (Block.Radio) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val directory = remember { RadioDirectory(context) }
    val recentStore = remember { RecentRadios(context, key = "directory_radios", max = RECENT_SLOTS) }
    var recents by remember { mutableStateOf(recentStore.load()) }
    var query by remember { mutableStateOf("") }
    val top by produceState(emptyList<Station>()) { value = directory.top() }
    var found by remember { mutableStateOf(emptyList<Station>()) }
    LaunchedEffect(query) {
        found = emptyList()
        if (query.isNotBlank()) {
            delay(SEARCH_DELAY_MS)
            found = directory.search(query.trim())
        }
    }
    val play: (Block.Radio) -> Unit = { radio ->
        focus.clearFocus()
        recents = recentStore.add(radio)
        onPlay(radio)
    }
    val stations = searchResults(top, found, query)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(Gap)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Cerca una radio") },
            leadingIcon = { Icon(painterResource(R.drawable.ph_magnifying_glass), contentDescription = null, modifier = Modifier.size(24.dp)) },
            singleLine = true,
            shape = CircleShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(stations, key = { it.name }) { station ->
                StationRow(station, directory, station.name == onAir) {
                    play(Block.Radio(station.name, station.url, favicon = station.favicon))
                }
            }
        }
        // The keyboard needs the room while searching.
        if (recents.isNotEmpty() && query.isBlank()) RecentStations(recents, directory, onAir, play)
    }
}

@Composable
private fun StationRow(station: Station, directory: RadioDirectory, playing: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().height(RowHeight), shape = RoundedCornerShape(18.dp), color = if (playing) RadioTone else SurfaceTone) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
            StationImage(station.favicon, directory, Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))
            Spacer(Modifier.size(12.dp))
            Text(
                station.name,
                style = MaterialTheme.typography.titleLarge,
                color = if (playing) RadioColor else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// Same squares as the home's Spotify row, in the same place.
@Composable
private fun RecentStations(recents: List<Block.Radio>, directory: RadioDirectory, onAir: String?, onPlay: (Block.Radio) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (radio in recents) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(
                    onClick = { onPlay(radio) },
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    shape = RoundedCornerShape(18.dp),
                    color = if (radio.name == onAir) RadioTone else SurfaceTone,
                ) {
                    StationImage(radio.favicon, directory, Modifier.fillMaxSize())
                }
                Text(radio.name, style = Caption, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        repeat(RECENT_SLOTS - recents.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun StationImage(url: String?, directory: RadioDirectory, modifier: Modifier) {
    val image by produceState<ImageBitmap?>(null, url) { value = url?.let { directory.favicon(it) } }
    Box(modifier, contentAlignment = Alignment.Center) {
        val shown = image
        if (shown != null) {
            Image(shown, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        } else {
            Icon(painterResource(R.drawable.ph_radio), contentDescription = null, tint = RadioColor, modifier = Modifier.size(28.dp))
        }
    }
}
