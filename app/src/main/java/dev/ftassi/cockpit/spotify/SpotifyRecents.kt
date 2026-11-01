package dev.ftassi.cockpit.spotify

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.edit
import androidx.core.net.toUri
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private const val TAG = "Cockpit"
private const val API = "https://api.spotify.com/v1"
private const val TOKEN_URL = "https://accounts.spotify.com/api/token"
private const val EMBED_URL = "https://open.spotify.com/oembed"
private const val MAX_PLAYLISTS = 3
private const val REFRESH_TOKEN = "refresh_token"
private const val GRANTED_SCOPES = "granted_scopes"
private const val VERIFIER = "verifier"
private const val LOGIN_STATE = "login_state"
private const val PLAYLISTS = "playlists"
private const val PINNED = "pinned"

// Process-wide, because the background job has its own SpotifyRecents. Refresh
// tokens are single use: two concurrent refreshes would make the second look
// like an expired token.
private val network = Mutex()

sealed interface SpotifyState {
    data object NotConnected : SpotifyState
    data object TokenExpired : SpotifyState
    data object NoPremium : SpotifyState
    data class Connected(val playlists: List<SpotifyPlaylist>) : SpotifyState
}

class SpotifyPlaylist(val uri: String, val name: String, val cover: ImageBitmap?, val pinned: Boolean)

@Serializable
private data class SavedPlaylist(val uri: String, val name: String)

private class Response(val status: Int, val body: String) {
    val ok get() = status in 200..299
}

// The last playlists played on the account, from any device. The last list is
// saved, so a start without network, like in a garage, still shows it.
class SpotifyRecents(context: Context) {
    private val context = context.applicationContext
    private val prefs = this.context.getSharedPreferences("spotify", Context.MODE_PRIVATE)
    private val covers = File(this.context.filesDir, "spotify")
    private var accessToken: String? = null

    private val mutableState = MutableStateFlow<SpotifyState>(SpotifyState.NotConnected)
    val state: StateFlow<SpotifyState> = mutableState

    // Chosen by the user, these come before the recent ones. Only recorded here:
    // the next refresh shows them, so sharing needs no network.
    fun pin(uri: String) {
        prefs.edit { putString(PINNED, Json.encodeToString(pinPlaylist(pinned(), uri, MAX_PLAYLISTS))) }
    }

    // Out of the saved list too, or it would come back as if recently played.
    fun unpin(uri: String) {
        prefs.edit {
            putString(PINNED, Json.encodeToString(pinned() - uri))
            putString(PLAYLISTS, Json.encodeToString(savedPlaylists().filter { it.uri != uri }))
        }
    }

    private fun pinned(): List<String> =
        prefs.getString(PINNED, null)?.let { Json.decodeFromString(it) } ?: emptyList()

    fun startLogin() {
        val verifier = randomToken()
        val loginState = randomToken()
        prefs.edit {
            putString(VERIFIER, verifier)
            putString(LOGIN_STATE, loginState)
        }
        val intent = Intent(Intent.ACTION_VIEW, authorizeUrl(pkceChallenge(verifier), loginState).toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    // A cancelled login, or a callback we did not ask for, leaves things as
    // they are.
    suspend fun finishLogin(callback: Uri) {
        val code = callback.getQueryParameter("code")
        val expected = prefs.getString(LOGIN_STATE, null)
        if (code == null || expected == null || callback.getQueryParameter("state") != expected) {
            Log.w(TAG, "Spotify login not completed: ${callback.getQueryParameter("error")}")
            return
        }
        val verifier = prefs.getString(VERIFIER, null) ?: return
        network.withLock {
            val outcome = withContext(Dispatchers.IO) {
                requestToken(
                    "grant_type" to "authorization_code",
                    "code" to code,
                    "redirect_uri" to REDIRECT_URI,
                    "code_verifier" to verifier,
                )
            }
            if (outcome is RefreshOutcome.Ok) {
                accessToken = outcome.accessToken
                prefs.edit {
                    putString(REFRESH_TOKEN, outcome.refreshToken)
                    putString(GRANTED_SCOPES, SCOPES)
                    remove(VERIFIER)
                    remove(LOGIN_STATE)
                }
            }
        }
        refresh()
    }

    suspend fun refresh() = network.withLock {
        withContext(Dispatchers.IO) {
            if (prefs.getString(REFRESH_TOKEN, null) == null) {
                mutableState.value = SpotifyState.NotConnected
                return@withContext
            }
            // A login from before the app needed these scopes must be redone.
            if (prefs.getString(GRANTED_SCOPES, null) != SCOPES) {
                mutableState.value = SpotifyState.TokenExpired
                return@withContext
            }
            val saved = savedPlaylists()
            if (mutableState.value !is SpotifyState.Connected) mutableState.value = connected(saved)
            when (val outcome = renewToken()) {
                RefreshOutcome.Expired -> mutableState.value = SpotifyState.TokenExpired
                RefreshOutcome.Failed -> mutableState.value = connected(saved)
                is RefreshOutcome.Ok -> loadPlaylists(outcome.accessToken, saved)
            }
        }
    }

    private fun loadPlaylists(token: String, saved: List<SavedPlaylist>) {
        try {
            val history = request("GET", "$API/me/player/recently-played?limit=50", token)
            when {
                historyDenied(history.status) -> mutableState.value = SpotifyState.NoPremium
                !history.ok -> mutableState.value = connected(saved)
                else -> {
                    // The history only spans 50 tracks: the saved list fills in
                    // when one long playlist has pushed the others out of it, and
                    // the library, read only if needed, when that is not enough.
                    val uris = sequence {
                        yieldAll(pinned())
                        yieldAll(playlistUris(history.body))
                        yieldAll(saved.map { it.uri })
                        yieldAll(fetchLibrary(token))
                    }.distinct()
                    val resolved = uris.mapNotNull { fetchPlaylist(token, it) }.take(MAX_PLAYLISTS).toList()
                    prefs.edit { putString(PLAYLISTS, Json.encodeToString(resolved)) }
                    mutableState.value = connected(resolved)
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Spotify history failed", e)
            mutableState.value = connected(saved)
        } catch (e: SerializationException) {
            Log.w(TAG, "Spotify history unreadable", e)
            mutableState.value = connected(saved)
        }
    }

    private fun fetchLibrary(token: String): List<String> {
        val response = request("GET", "$API/me/playlists?limit=$MAX_PLAYLISTS", token)
        return if (response.ok) libraryUris(response.body) else emptyList()
    }

    // Null when its details are not available anywhere.
    private fun fetchPlaylist(token: String, uri: String): SavedPlaylist? {
        val id = uri.substringAfterLast(':')
        val details = fetchDetails(token, id) ?: return null
        details.coverUrl?.let { downloadCover(it, id) }
        return SavedPlaylist(uri, details.name)
    }

    // Playlists made by Spotify itself answer 404 to apps like this one, but
    // the public embed service still knows their name and cover.
    private fun fetchDetails(token: String, id: String): PlaylistDetails? {
        val response = request("GET", "$API/playlists/$id?fields=name,images", token)
        if (response.ok) return apiPlaylist(response.body)
        if (response.status != 404) return null
        val page = URLEncoder.encode("https://open.spotify.com/playlist/$id", "UTF-8")
        val embed = request("GET", "$EMBED_URL?url=$page", null)
        return if (embed.ok) embedPlaylist(embed.body) else null
    }

    // A failure keeps the previous cover, if any: the square still has its name.
    private fun downloadCover(url: String, id: String) {
        try {
            covers.mkdirs()
            (URL(url).openConnection() as HttpURLConnection).run {
                try {
                    inputStream.use { input -> File(covers, id).outputStream().use(input::copyTo) }
                } finally {
                    disconnect()
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Spotify cover failed", e)
        }
    }

    private fun connected(playlists: List<SavedPlaylist>): SpotifyState.Connected =
        SpotifyState.Connected(
            playlists.map { playlist ->
                val file = File(covers, playlist.uri.substringAfterLast(':'))
                val cover = if (file.exists()) BitmapFactory.decodeFile(file.path)?.asImageBitmap() else null
                SpotifyPlaylist(playlist.uri, playlist.name, cover, playlist.uri in pinned())
            },
        )

    private fun savedPlaylists(): List<SavedPlaylist> =
        prefs.getString(PLAYLISTS, null)?.let { Json.decodeFromString(it) } ?: emptyList()

    private fun renewToken(): RefreshOutcome {
        val refreshToken = prefs.getString(REFRESH_TOKEN, null) ?: return RefreshOutcome.Failed
        val outcome = requestToken("grant_type" to "refresh_token", "refresh_token" to refreshToken)
        if (outcome is RefreshOutcome.Ok) {
            accessToken = outcome.accessToken
            outcome.refreshToken?.let { prefs.edit { putString(REFRESH_TOKEN, it) } }
        }
        return outcome
    }

    // No network says nothing about the token, so it must not look expired.
    private fun requestToken(vararg fields: Pair<String, String>): RefreshOutcome = try {
        val form = (fields.toList() + ("client_id" to CLIENT_ID))
            .joinToString("&") { (name, value) -> "$name=${URLEncoder.encode(value, "UTF-8")}" }
        val response = request("POST", TOKEN_URL, null, form, "application/x-www-form-urlencoded")
        classifyRefresh(response.status, response.body)
    } catch (e: IOException) {
        Log.w(TAG, "Spotify token request failed", e)
        RefreshOutcome.Failed
    }

    private fun request(
        method: String,
        url: String,
        token: String?,
        body: String? = null,
        contentType: String = "application/json",
    ): Response {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType)
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            return Response(status, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }

    private fun randomToken(): String {
        val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
