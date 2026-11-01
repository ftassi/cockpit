package dev.ftassi.cockpit.recents

import android.content.Context
import androidx.core.content.edit
import dev.ftassi.cockpit.config.Block
import kotlinx.serialization.json.Json

// Stations played most recently, newest first. Kept across restarts so the
// home screen can offer them and car mode can resume the last one.
class RecentRadios(context: Context, private val key: String = "radios", private val max: Int = 2) {
    private val prefs = context.getSharedPreferences("recents", Context.MODE_PRIVATE)

    fun load(): List<Block.Radio> = prefs.getString(key, null)?.let { Json.decodeFromString(it) } ?: emptyList()

    fun add(radio: Block.Radio): List<Block.Radio> {
        val radios = pushRecent(load(), radio, max)
        prefs.edit { putString(key, Json.encodeToString(radios)) }
        return radios
    }
}

fun <T> pushRecent(items: List<T>, item: T, max: Int): List<T> = (listOf(item) + (items - item)).take(max)
