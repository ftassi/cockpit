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
                { "type": "radio", "name": "Test FM", "url": "https://stream.example/test.mp3" },
                { "type": "spotify", "uri": "spotify:playlist:abc" },
                { "type": "spotify" },
                { "type": "waze" },
                { "type": "maps" },
                { "type": "phone" }
              ]
            }
        """

        val config = parseConfig(json)

        assertEquals(
            listOf(
                Block.Radio(name = "Test FM", url = "https://stream.example/test.mp3"),
                Block.Spotify(uri = "spotify:playlist:abc"),
                Block.Spotify(uri = null),
                Block.Waze,
                Block.Maps,
                Block.Phone,
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
