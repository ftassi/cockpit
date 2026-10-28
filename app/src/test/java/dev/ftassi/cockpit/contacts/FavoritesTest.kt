package dev.ftassi.cockpit.contacts

import org.junit.Assert.assertEquals
import org.junit.Test

class FavoritesTest {
    @Test
    fun `keeps one number per contact, preferring the default one`() {
        val rows = listOf(
            PhoneRow(contactId = 1, name = "Alice Example", number = "+39 000 0000001", isDefault = false),
            PhoneRow(contactId = 1, name = "Alice Example", number = "+39 000 0000002", isDefault = true),
            PhoneRow(contactId = 2, name = "Bob Example", number = "+39 000 0000003", isDefault = false),
            PhoneRow(contactId = 2, name = "Bob Example", number = "+39 000 0000004", isDefault = false),
        )

        assertEquals(
            listOf(
                Favorite(name = "Alice Example", number = "+39 000 0000002"),
                Favorite(name = "Bob Example", number = "+39 000 0000003"),
            ),
            favoritesFrom(rows),
        )
    }
}
