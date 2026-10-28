package dev.ftassi.cockpit.nowplaying

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NowPlayingTest {
    private data class Session(val name: String, val playing: Boolean)

    @Test
    fun `prefers the session that is playing`() {
        val sessions = listOf(Session("recent", playing = false), Session("audible", playing = true))

        assertEquals("audible", pickActive(sessions) { it.playing }?.name)
    }

    @Test
    fun `falls back to the most recent session`() {
        val sessions = listOf(Session("recent", playing = false), Session("older", playing = false))

        assertEquals("recent", pickActive(sessions) { it.playing }?.name)
    }

    @Test
    fun `nothing to show without sessions`() {
        assertNull(pickActive(emptyList<Session>()) { it.playing })
    }
}
