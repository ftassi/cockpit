package dev.ftassi.cockpit.spotify

import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Base64
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonPrimitive

// Public: PKCE needs no client secret, so the id can live in the repo. A fork
// registers its own app on Spotify's developer dashboard and replaces it.
const val CLIENT_ID = "87d2fb957c6248aca97c147cb7eda58d"
const val REDIRECT_URI = "cockpit://spotify-callback"
const val SCOPES = "user-read-recently-played playlist-read-private playlist-read-collaborative"

fun pkceChallenge(verifier: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
}

fun authorizeUrl(challenge: String, state: String): String {
    val parameters = listOf(
        "client_id" to CLIENT_ID,
        "response_type" to "code",
        "redirect_uri" to REDIRECT_URI,
        "scope" to SCOPES,
        "code_challenge_method" to "S256",
        "code_challenge" to challenge,
        "state" to state,
    )
    return "https://accounts.spotify.com/authorize?" +
        parameters.joinToString("&") { (name, value) -> "$name=${URLEncoder.encode(value, "UTF-8")}" }
}

// The playlists the history's tracks were played from, newest first. Spotify
// has no list of recent playlists: this is the closest thing to one.
fun playlistUris(historyJson: String): List<String> =
    Json.parseToJsonElement(historyJson).jsonObject["items"]?.jsonArray.orEmpty()
        .mapNotNull { item ->
            val context = item.jsonObject["context"] as? JsonObject ?: return@mapNotNull null
            if (context.text("type") == "playlist") context.text("uri") else null
        }
        .distinct()

// The account's own playlists, used when the history has too few.
fun libraryUris(json: String): List<String> =
    Json.parseToJsonElement(json).jsonObject["items"]?.jsonArray.orEmpty()
        .mapNotNull { (it as? JsonObject)?.text("uri") }

private val SHARED_PLAYLIST = Regex("""(?:open\.spotify\.com/(?:intl-\w+/)?playlist/|spotify:playlist:)(\w+)""")

// Spotify's share action sends a link, sometimes inside a sentence.
fun sharedPlaylistUri(text: String): String? =
    SHARED_PLAYLIST.find(text)?.let { "spotify:playlist:${it.groupValues[1]}" }

fun pinPlaylist(pinned: List<String>, uri: String, max: Int): List<String> =
    if (uri in pinned) pinned else (pinned + uri).takeLast(max)

data class PlaylistDetails(val name: String, val coverUrl: String?)

fun apiPlaylist(json: String): PlaylistDetails? {
    val playlist = Json.parseToJsonElement(json).jsonObject
    val name = playlist.text("name") ?: return null
    val cover = (playlist["images"] as? JsonArray)?.firstOrNull()?.jsonObject?.text("url")
    return PlaylistDetails(name, cover)
}

fun embedPlaylist(json: String): PlaylistDetails? {
    val embed = Json.parseToJsonElement(json).jsonObject
    return embed.text("title")?.let { PlaylistDetails(it, embed.text("thumbnail_url")) }
}

sealed interface RefreshOutcome {
    data class Ok(val accessToken: String, val refreshToken: String?) : RefreshOutcome

    // Refresh tokens last six months from the login, whatever their use: only
    // a new login gets another.
    data object Expired : RefreshOutcome

    // No verdict on the token: a server error or a response we cannot read.
    data object Failed : RefreshOutcome
}

fun classifyRefresh(status: Int, body: String): RefreshOutcome {
    val json = try {
        Json.parseToJsonElement(body) as? JsonObject
    } catch (_: SerializationException) {
        null
    } ?: return RefreshOutcome.Failed
    return when {
        status == 400 && json.text("error") == "invalid_grant" -> RefreshOutcome.Expired
        status != 200 -> RefreshOutcome.Failed
        else -> json.text("access_token")?.let { RefreshOutcome.Ok(it, json.text("refresh_token")) }
            ?: RefreshOutcome.Failed
    }
}

// Spotify documents no error for a lapsed Premium, only that a development
// mode app stops working: every 403 is treated as that. Getting it wrong costs
// little, since each start tries again.
fun historyDenied(status: Int): Boolean = status == 403

internal fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
