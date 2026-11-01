package dev.ftassi.cockpit.layout

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.json.Json

private const val RADIOS = "radios"
private const val PLAYLISTS = "playlists"

// Where the user put the home's tiles. Saved here, not in the config, so
// reorganizing needs no rebuild.
class HomeLayout(context: Context) {
    private val prefs = context.getSharedPreferences("home_layout", Context.MODE_PRIVATE)

    fun radios(): List<String> = load(RADIOS)
    fun playlists(): List<String> = load(PLAYLISTS)
    fun saveRadios(order: List<String>) = save(RADIOS, order)
    fun savePlaylists(order: List<String>) = save(PLAYLISTS, order)

    private fun load(key: String): List<String> =
        prefs.getString(key, null)?.let { Json.decodeFromString(it) } ?: emptyList()

    private fun save(key: String, order: List<String>) = prefs.edit { putString(key, Json.encodeToString(order)) }
}

fun <T> applyOrder(items: List<T>, order: List<String>, key: (T) -> String): List<T> {
    val position = order.withIndex().associate { (index, name) -> name to index }
    return items.sortedBy { position[key(it)] ?: Int.MAX_VALUE }
}

fun moveItem(items: List<String>, moved: String, target: String): List<String> {
    val destination = items.indexOf(target)
    if (moved !in items || destination < 0) return items
    return (items - moved).toMutableList().apply { add(destination, moved) }
}

// Where a tile sits in a grid, in pixels. While reorganizing the tiles are placed
// by hand, so they can slide between these fixed places.
data class Slot(val x: Float, val y: Float, val width: Float, val height: Float)

fun gridSlots(count: Int, columns: Int, width: Float, rowHeight: Float, gap: Float, fullLastRow: Boolean): List<Slot> {
    val tileWidth = (width - gap * (columns - 1)) / columns
    return List(count) { index ->
        val y = (index / columns) * (rowHeight + gap)
        val alone = fullLastRow && index == count - 1 && index % columns == 0
        if (alone) Slot(0f, y, width, rowHeight) else Slot((index % columns) * (tileWidth + gap), y, tileWidth, rowHeight)
    }
}

// Always answers, so a tile dropped anywhere lands somewhere it can be seen.
fun nearestSlot(x: Float, y: Float, slots: List<Slot>, first: Int): Int =
    (first until slots.size).minBy { index ->
        val slot = slots[index]
        val dx = slot.x + slot.width / 2 - x
        val dy = slot.y + slot.height / 2 - y
        dx * dx + dy * dy
    }
