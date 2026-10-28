package dev.ftassi.cockpit.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.ftassi.cockpit.contacts.loadFavorites
import dev.ftassi.cockpit.launch.call
import dev.ftassi.cockpit.launch.openDialer

private val permissions = arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE)

// Starred contacts from the address book, one tap to call, plus the dialer.
@Composable
fun ContactsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(permissions.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED })
    }
    val request = rememberLauncherForActivityResult(RequestMultiplePermissions()) { result ->
        granted = result.values.all { it }
    }
    LaunchedEffect(Unit) {
        if (!granted) request.launch(permissions)
    }
    val favorites = remember(granted) { if (granted) loadFavorites(context) else emptyList() }

    val tiles = buildList {
        if (!granted) {
            add(Tile("Consenti contatti e chiamate", Color(0xFFEF6C00)) { request.launch(permissions) })
        }
        favorites.forEach { add(Tile(it.name, Color(0xFF2E7D32)) { call(context, it.number) }) }
        add(Tile("Tastierino", Color(0xFF37474F)) { openDialer(context) })
    }
    TileGrid(tiles, modifier)
}
