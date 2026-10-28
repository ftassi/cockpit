package dev.ftassi.cockpit.recents

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentsTest {
    @Test
    fun `newest first, oldest dropped past the limit`() {
        assertEquals(listOf("c", "b"), pushRecent(listOf("b", "a"), "c", max = 2))
    }

    @Test
    fun `replaying an item moves it to the front instead of duplicating it`() {
        assertEquals(listOf("a", "b"), pushRecent(listOf("b", "a"), "a", max = 2))
    }
}
