package dev.ftassi.cockpit.config

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigTest {
    @Test
    fun `parses every block type`() {
        val json = """
            {
              "blocks": [
                { "type": "group", "name": "Musica", "blocks": [
                    { "type": "radio", "name": "Test FM", "url": "https://stream.example/test.mp3" },
                    { "type": "spotify", "name": "Test list", "uri": "spotify:playlist:abc" }
                ]},
                { "type": "spotify" },
                { "type": "contacts" },
                { "type": "waze" }
              ]
            }
        """

        val config = parseConfig(json)

        assertEquals(
            listOf(
                Block.Group(
                    name = "Musica",
                    blocks = listOf(
                        Block.Radio(name = "Test FM", url = "https://stream.example/test.mp3"),
                        Block.Spotify(name = "Test list", uri = "spotify:playlist:abc"),
                    ),
                ),
                Block.Spotify(name = "Spotify", uri = null),
                Block.Contacts,
                Block.Waze,
            ),
            config.blocks,
        )
    }

    @Test
    fun `bundled config is valid`() {
        val config = parseConfig(File("src/main/assets/config.json").readText())

        assertTrue(config.blocks.isNotEmpty())
    }
}
