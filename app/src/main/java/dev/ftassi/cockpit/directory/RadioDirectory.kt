package dev.ftassi.cockpit.directory

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private const val TAG = "Cockpit"
private const val API = "https://all.api.radio-browser.info/json/stations/search"
private const val TOP_SIZE = 100
private const val SEARCH_SIZE = 30
private const val TIMEOUT_MS = 15_000
private val MAX_AGE_MS = TimeUnit.DAYS.toMillis(7)

// radio-browser.info, ordered by its own click count: the closest public thing
// to a listening chart, not actual audience figures. The list is saved, so it
// still opens without network.
class RadioDirectory(context: Context) {
    private val saved = File(context.filesDir, "radio-directory.json")
    private val favicons = File(context.filesDir, "favicons")

    suspend fun top(): List<Station> = withContext(Dispatchers.IO) {
        val cached = if (saved.exists()) decode(saved.readText()) else emptyList()
        val fresh = cached.isNotEmpty() && System.currentTimeMillis() - saved.lastModified() < MAX_AGE_MS
        if (fresh) return@withContext cached
        val stations = fetch("countrycode=IT").take(TOP_SIZE)
        if (stations.isEmpty()) return@withContext cached
        saved.writeText(Json.encodeToString(stations))
        stations
    }

    suspend fun search(query: String): List<Station> = withContext(Dispatchers.IO) {
        fetch("name=${URLEncoder.encode(query, "UTF-8")}").take(SEARCH_SIZE)
    }

    suspend fun favicon(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        val file = File(favicons, url.hashCode().toString())
        try {
            if (!file.exists()) {
                favicons.mkdirs()
                open(url).run {
                    try {
                        inputStream.use { input -> file.outputStream().use(input::copyTo) }
                    } finally {
                        disconnect()
                    }
                }
            }
            BitmapFactory.decodeFile(file.path)?.asImageBitmap()
        } catch (e: IOException) {
            file.delete()
            null
        }
    }

    // Twice the size needed, because repeated stations are dropped afterwards.
    private fun fetch(filter: String): List<Station> {
        val limit = TOP_SIZE * 2
        return try {
            val connection = open("$API?$filter&order=clickcount&reverse=true&hidebroken=true&limit=$limit")
            try {
                if (connection.responseCode in 200..299) {
                    parseStations(connection.inputStream.bufferedReader().use { it.readText() })
                } else {
                    emptyList()
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: IOException) {
            Log.w(TAG, "Radio catalogue failed", e)
            emptyList()
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Radio catalogue unreadable", e)
            emptyList()
        }
    }

    private fun decode(json: String): List<Station> = try {
        Json.decodeFromString(json)
    } catch (_: IllegalArgumentException) {
        emptyList()
    }

    // The catalogue asks clients to identify themselves.
    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = TIMEOUT_MS
        readTimeout = TIMEOUT_MS
        setRequestProperty("User-Agent", "Cockpit/0.1")
    }
}
