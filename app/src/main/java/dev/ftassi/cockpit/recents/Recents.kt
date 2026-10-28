package dev.ftassi.cockpit.recents

import android.content.Context
import androidx.core.content.edit
import dev.ftassi.cockpit.config.Block
import kotlinx.serialization.json.Json

private const val RADIOS = "radios"
private const val MAX_RADIOS = 2

// Stations played most recently, newest first. Kept across restarts so the
// home screen can offer them and car mode can resume the last one.
class RecentRadios(context: Context) {
    private val prefs = context.getSharedPreferences("recents", Context.MODE_PRIVATE)

    fun load(): List<Block.Radio> = prefs.getString(RADIOS, null)?.let { Json.decodeFromString(it) } ?: emptyList()

    fun add(radio: Block.Radio): List<Block.Radio> {
        val radios = pushRecent(load(), radio, MAX_RADIOS)
        prefs.edit { putString(RADIOS, Json.encodeToString(radios)) }
        return radios
    }
}

fun <T> pushRecent(items: List<T>, item: T, max: Int): List<T> = (listOf(item) + (items - item)).take(max)
