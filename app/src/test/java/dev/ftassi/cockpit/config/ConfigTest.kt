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
                { "type": "group", "name": "Musica", "icon": "search", "blocks": [
                    { "type": "radio", "name": "Test FM", "url": "https://stream.example/test.mp3", "logo": "logos/test.png" },
                    { "type": "spotify", "name": "Test list", "uri": "spotify:playlist:abc" }
                ]},
                { "type": "spotify" },
                { "type": "contacts" },
                { "type": "waze" },
                { "type": "app", "name": "Test app", "package": "com.example.test" }
              ],
              "bar": [
                { "type": "voice" }
              ]
            }
        """

        val config = parseConfig(json)

        assertEquals(
            listOf(
                Block.Group(
                    name = "Musica",
                    blocks = listOf(
                        Block.Radio(name = "Test FM", url = "https://stream.example/test.mp3", logo = "logos/test.png"),
                        Block.Spotify(name = "Test list", uri = "spotify:playlist:abc"),
                    ),
                    icon = Icon.Search,
                ),
                Block.Spotify(name = "Spotify", uri = null),
                Block.Contacts,
                Block.Waze,
                Block.App(name = "Test app", packageName = "com.example.test"),
            ),
            config.blocks,
        )
        assertEquals(listOf(Block.Voice), config.bar)
    }

    @Test
    fun `bundled config is valid`() {
        val config = parseConfig(File("src/main/assets/config.json").readText())

        assertTrue(config.blocks.isNotEmpty())
        for (logo in config.blocks.mapNotNull { (it as? Block.Radio)?.logo }) {
            assertTrue("missing $logo", File("src/main/assets/$logo").isFile)
        }
    }

    @Test
    fun `finds the first radio, depth first`() {
        val skyline = Block.Radio("Skyline", "https://stream.example/skyline.mp3")
        val config = Config(
            bar = emptyList(),
            blocks = listOf(
                Block.Waze,
                Block.Group("Radio", listOf(Block.Spotify(), skyline)),
                Block.Radio("Later", "https://stream.example/later.mp3"),
            ),
        )

        assertEquals(skyline, config.firstRadio())
    }
}
