package dev.ftassi.cockpit.carmode

import org.junit.Assert.assertEquals
import org.junit.Test

class CarModeTest {
    @Test
    fun `maps a volume fraction to the stream's steps`() {
        assertEquals(15, volumeIndex(0.6f, max = 25))
        assertEquals(0, volumeIndex(-1f, max = 25))
        assertEquals(25, volumeIndex(2f, max = 25))
    }
}
