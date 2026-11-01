package dev.ftassi.cockpit.spotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyRulesTest {
    @Test
    fun `challenge matches the RFC 7636 example`() {
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            pkceChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }

    @Test
    fun `authorize url asks for S256 with the given challenge and state`() {
        val url = authorizeUrl("the-challenge", "the-state")
        assertTrue(url.startsWith("https://accounts.spotify.com/authorize?"))
        assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(url.contains("code_challenge=the-challenge"))
        assertTrue(url.contains("state=the-state"))
        assertTrue(url.contains("redirect_uri=cockpit%3A%2F%2Fspotify-callback"))
    }

    @Test
    fun `history keeps distinct playlists newest first and ignores everything else`() {
        val history = """
            {"items": [
              {"context": {"type": "playlist", "uri": "spotify:playlist:A"}, "played_at": "3"},
              {"context": {"type": "playlist", "uri": "spotify:playlist:A"}, "played_at": "2"},
              {"context": {"type": "album", "uri": "spotify:album:X"}},
              {"context": null},
              {"track": {"name": "no context at all"}},
              {"context": {"type": "playlist", "uri": "spotify:playlist:B"}}
            ]}
        """
        assertEquals(listOf("spotify:playlist:A", "spotify:playlist:B"), playlistUris(history))
    }

    @Test
    fun `history without playlists gives an empty list`() {
        assertEquals(emptyList<String>(), playlistUris("""{"items": []}"""))
    }

    @Test
    fun `library keeps its order and skips empty entries`() {
        val library = """{"items": [{"uri": "spotify:playlist:A"}, null, {"name": "no uri"}, {"uri": "spotify:playlist:B"}]}"""
        assertEquals(listOf("spotify:playlist:A", "spotify:playlist:B"), libraryUris(library))
    }

    @Test
    fun `authorize url asks for every scope the app uses`() {
        assertTrue(authorizeUrl("c", "s").contains("user-read-recently-played+playlist-read-private"))
    }

    @Test
    fun `a shared playlist is recognised inside the text Spotify sends`() {
        val uri = "spotify:playlist:37i9dQZF1DWULEW2RfoSCi"
        assertEquals(uri, sharedPlaylistUri("https://open.spotify.com/playlist/37i9dQZF1DWULEW2RfoSCi?si=abc"))
        assertEquals(uri, sharedPlaylistUri("Ascolta: https://open.spotify.com/intl-it/playlist/37i9dQZF1DWULEW2RfoSCi"))
        assertEquals(uri, sharedPlaylistUri(uri))
    }

    @Test
    fun `anything else shared is not a playlist`() {
        assertEquals(null, sharedPlaylistUri("https://open.spotify.com/album/37i9dQZF1DWULEW2RfoSCi"))
        assertEquals(null, sharedPlaylistUri("https://example.com/playlist/abc"))
        assertEquals(null, sharedPlaylistUri("hello"))
    }

    @Test
    fun `pinning adds at the end and replaces the oldest when full`() {
        assertEquals(listOf("a", "b"), pinPlaylist(listOf("a"), "b", max = 3))
        assertEquals(listOf("b", "c", "d"), pinPlaylist(listOf("a", "b", "c"), "d", max = 3))
    }

    @Test
    fun `pinning twice keeps one entry in its place`() {
        assertEquals(listOf("a", "b"), pinPlaylist(listOf("a", "b"), "a", max = 3))
    }

    @Test
    fun `playlist details come from the API shape`() {
        val api = """{"name":"Mine","images":[{"url":"https://i/1"},{"url":"https://i/2"}]}"""
        assertEquals(PlaylistDetails("Mine", "https://i/1"), apiPlaylist(api))
    }

    @Test
    fun `a playlist without images still has its name`() {
        assertEquals(PlaylistDetails("Mine", null), apiPlaylist("""{"name":"Mine","images":[]}"""))
        assertEquals(PlaylistDetails("Mine", null), apiPlaylist("""{"name":"Mine","images":null}"""))
    }

    @Test
    fun `playlist details come from the public embed shape`() {
        val embed = """{"title":"70s Soul Classics","thumbnail_url":"https://i/t","type":"rich"}"""
        assertEquals(PlaylistDetails("70s Soul Classics", "https://i/t"), embedPlaylist(embed))
    }

    @Test
    fun `details without a name are unusable`() {
        assertEquals(null, apiPlaylist("""{"images":[]}"""))
        assertEquals(null, embedPlaylist("""{"thumbnail_url":"https://i/t"}"""))
    }

    @Test
    fun `invalid grant means the token expired`() {
        assertEquals(RefreshOutcome.Expired, classifyRefresh(400, """{"error":"invalid_grant"}"""))
    }

    @Test
    fun `any other refresh failure is not an expired token`() {
        assertEquals(RefreshOutcome.Failed, classifyRefresh(400, """{"error":"invalid_client"}"""))
        assertEquals(RefreshOutcome.Failed, classifyRefresh(400, "not json"))
        assertEquals(RefreshOutcome.Failed, classifyRefresh(503, ""))
    }

    @Test
    fun `a successful refresh may or may not rotate the refresh token`() {
        assertEquals(RefreshOutcome.Ok("a", null), classifyRefresh(200, """{"access_token":"a","expires_in":3600}"""))
        assertEquals(RefreshOutcome.Ok("a", "r"), classifyRefresh(200, """{"access_token":"a","refresh_token":"r"}"""))
    }

    @Test
    fun `only a 403 on the history counts as denied`() {
        assertTrue(historyDenied(403))
        assertFalse(historyDenied(401))
        assertFalse(historyDenied(500))
    }
}
