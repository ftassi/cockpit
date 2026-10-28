package dev.ftassi.cockpit.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class ReconnectorTest {
    @Test
    fun `delay doubles from one second and is capped at thirty`() {
        assertEquals(
            listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L),
            (0..6).map(::reconnectDelayMs),
        )
    }

    @Test
    fun `delay stays capped for long outages`() {
        assertEquals(30_000L, reconnectDelayMs(1_000))
    }
}
