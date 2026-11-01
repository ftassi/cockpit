package dev.ftassi.cockpit.directory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectoryRulesTest {
    private fun station(name: String) = Station(name, "https://stream.example/$name")

    @Test
    fun `stations keep the catalogue order with resolved url and favicon`() {
        val json = """[
            {"name": " Radio Uno ", "url": "http://a/redirect", "url_resolved": "https://a/live", "favicon": "https://a/i.png", "votes": 3},
            {"name": "Radio Due", "url": "http://b/live", "url_resolved": "", "favicon": ""}
        ]"""
        assertEquals(
            listOf(Station("Radio Uno", "https://a/live", "https://a/i.png"), Station("Radio Due", "http://b/live", null)),
            parseStations(json),
        )
    }

    @Test
    fun `the same station listed twice appears once`() {
        val json = """[{"name": "Radio Uno", "url": "https://a"}, {"name": "RADIO UNO ", "url": "https://b"}]"""
        assertEquals(listOf(Station("Radio Uno", "https://a")), parseStations(json))
    }

    @Test
    fun `entries that cannot play are skipped`() {
        val json = """[{"name": "", "url": "https://a"}, {"name": "No url", "url": ""}, null, {"name": "Ok", "url": "https://c"}]"""
        assertEquals(listOf(Station("Ok", "https://c")), parseStations(json))
    }

    @Test
    fun `search ignores case and accents`() {
        assertTrue(matches(station("Radio Città Futura"), "citta"))
        assertTrue(matches(station("RTL 102.5"), "rtl"))
        assertFalse(matches(station("RTL 102.5"), "deejay"))
    }

    @Test
    fun `results show the known stations first, then new ones without repeats`() {
        val known = listOf(station("Radio Deejay"), station("Radio Italia"))
        val found = listOf(station("Deejay Time"), station("radio deejay"))
        assertEquals(listOf(station("Radio Deejay"), station("Deejay Time")), searchResults(known, found, "deejay"))
    }

    @Test
    fun `an empty query shows the whole list`() {
        val known = listOf(station("A"), station("B"))
        assertEquals(known, searchResults(known, emptyList(), "  "))
    }
}
