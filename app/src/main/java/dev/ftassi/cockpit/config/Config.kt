package dev.ftassi.cockpit.config

import android.content.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Config(val blocks: List<Block>)

// The JSON "type" field selects the variant.
@Serializable
sealed interface Block {
    @Serializable
    @SerialName("radio")
    data class Radio(val name: String, val url: String) : Block

    // A null uri opens the Spotify app on its home screen.
    @Serializable
    @SerialName("spotify")
    data class Spotify(val uri: String? = null) : Block

    @Serializable
    @SerialName("waze")
    data object Waze : Block

    @Serializable
    @SerialName("maps")
    data object Maps : Block

    @Serializable
    @SerialName("phone")
    data object Phone : Block
}

fun parseConfig(json: String): Config = Json.decodeFromString(json)

fun loadConfig(context: Context): Config =
    parseConfig(context.assets.open("config.json").bufferedReader().use { it.readText() })
