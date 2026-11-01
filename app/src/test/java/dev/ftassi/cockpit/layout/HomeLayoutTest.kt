package dev.ftassi.cockpit.layout

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutTest {
    @Test
    fun `items follow the saved order`() {
        assertEquals(listOf("c", "a", "b"), applyOrder(listOf("a", "b", "c"), listOf("c", "a", "b")) { it })
    }

    @Test
    fun `new items go last in their own order, removed ones are forgotten`() {
        assertEquals(listOf("b", "a", "d"), applyOrder(listOf("a", "b", "d"), listOf("gone", "b")) { it })
    }

    @Test
    fun `no saved order keeps the items as they are`() {
        assertEquals(listOf("a", "b"), applyOrder(listOf("a", "b"), emptyList()) { it })
    }

    @Test
    fun `moving takes the place of the target and shifts what is between`() {
        assertEquals(listOf("b", "c", "a", "d"), moveItem(listOf("a", "b", "c", "d"), "a", "c"))
        assertEquals(listOf("d", "a", "b", "c"), moveItem(listOf("a", "b", "c", "d"), "d", "a"))
    }

    @Test
    fun `two columns fill row by row with the gap between`() {
        val slots = gridSlots(count = 3, columns = 2, width = 210f, rowHeight = 50f, gap = 10f, fullLastRow = false)
        assertEquals(listOf(Slot(0f, 0f, 100f, 50f), Slot(110f, 0f, 100f, 50f), Slot(0f, 60f, 100f, 50f)), slots)
    }

    @Test
    fun `an odd last tile takes the whole row, like the home grid`() {
        val slots = gridSlots(count = 3, columns = 2, width = 210f, rowHeight = 50f, gap = 10f, fullLastRow = true)
        assertEquals(Slot(0f, 60f, 210f, 50f), slots[2])
    }

    @Test
    fun `the nearest slot is the one whose centre is closest`() {
        val slots = gridSlots(count = 4, columns = 2, width = 210f, rowHeight = 50f, gap = 10f, fullLastRow = false)
        assertEquals(0, nearestSlot(10f, 10f, slots, first = 0))
        assertEquals(3, nearestSlot(200f, 100f, slots, first = 0))
        assertEquals(1, nearestSlot(180f, 5f, slots, first = 0))
    }

    @Test
    fun `slots before first are never a target, nor beyond the area`() {
        val slots = gridSlots(count = 4, columns = 4, width = 430f, rowHeight = 100f, gap = 10f, fullLastRow = false)
        assertEquals(1, nearestSlot(0f, 0f, slots, first = 1))
        assertEquals(3, nearestSlot(9999f, 9999f, slots, first = 1))
    }

    @Test
    fun `moving something unknown changes nothing`() {
        assertEquals(listOf("a", "b"), moveItem(listOf("a", "b"), "x", "a"))
    }
}
