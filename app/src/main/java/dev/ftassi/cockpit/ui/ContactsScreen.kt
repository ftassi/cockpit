package dev.ftassi.cockpit.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.ftassi.cockpit.launch.openDialer

@Composable
fun ContactsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    TileGrid(
        listOf(Tile("Tastierino", Color(0xFF37474F)) { openDialer(context) }),
        modifier,
    )
}
