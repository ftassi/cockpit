package dev.ftassi.cockpit.directory

import java.text.Normalizer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
data class Station(val name: String, val url: String, val favicon: String? = null)

// The catalogue lists many stations twice; the first is the most listened.
fun parseStations(json: String): List<Station> =
    (Json.parseToJsonElement(json) as? JsonArray).orEmpty()
        .mapNotNull { entry ->
            val station = entry as? JsonObject ?: return@mapNotNull null
            val name = station.text("name")?.trim().orEmpty()
            val url = station.text("url_resolved")?.takeIf { it.isNotBlank() } ?: station.text("url").orEmpty()
            if (name.isBlank() || url.isBlank()) null else Station(name, url, station.text("favicon")?.takeIf { it.isNotBlank() })
        }
        .distinctBy { normalized(it.name) }

fun matches(station: Station, query: String): Boolean = normalized(station.name).contains(normalized(query))

// Known stations answer at once and stay on top; the catalogue's results follow
// when they arrive, so the list only grows while typing.
fun searchResults(known: List<Station>, found: List<Station>, query: String): List<Station> {
    if (query.isBlank()) return known
    return (known.filter { matches(it, query) } + found).distinctBy { normalized(it.name) }
}

private fun normalized(text: String): String =
    Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
