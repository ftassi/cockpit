package dev.ftassi.cockpit.config

import android.content.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// blocks fill the main grid; bar holds the small icon buttons always at the
// bottom of the screen.
@Serializable
data class Config(val blocks: List<Block>, val bar: List<Block>)

// The JSON "type" field selects the variant.
@Serializable
sealed interface Block {
    // Opens a sub-screen with its own blocks.
    @Serializable
    @SerialName("group")
    data class Group(val name: String, val blocks: List<Block>) : Block

    @Serializable
    @SerialName("radio")
    data class Radio(val name: String, val url: String) : Block

    // A null uri opens the Spotify app on its home screen.
    @Serializable
    @SerialName("spotify")
    data class Spotify(val name: String = "Spotify", val uri: String? = null) : Block

    // Opens the starred contacts from the phone's address book.
    @Serializable
    @SerialName("contacts")
    data object Contacts : Block

    @Serializable
    @SerialName("waze")
    data object Waze : Block

    // Starts the phone's voice assistant.
    @Serializable
    @SerialName("voice")
    data object Voice : Block
}

// The station car mode falls back to before anything has been played.
fun Config.firstRadio(): Block.Radio? = blocks.firstRadio()

private fun List<Block>.firstRadio(): Block.Radio? = firstNotNullOfOrNull {
    when (it) {
        is Block.Radio -> it
        is Block.Group -> it.blocks.firstRadio()
        else -> null
    }
}

fun parseConfig(json: String): Config = Json.decodeFromString(json)

fun loadConfig(context: Context): Config =
    parseConfig(context.assets.open("config.json").bufferedReader().use { it.readText() })
