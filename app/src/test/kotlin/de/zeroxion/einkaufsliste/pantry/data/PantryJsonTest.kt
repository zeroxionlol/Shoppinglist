package de.zeroxion.einkaufsliste.pantry.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PantryJsonTest {

    /** Exactly what the old MHD-Tracker app (version 1.4) exported. */
    private val oldBackup = """
        {"app":"mhd-tracker","v":1,"items":[
          {"id":"m1abc","name":"Weihenstephan Joghurt mild (500 g)","date":"2026-11-21","barcode":"4008452011004",
           "image":"https://images.openfoodfacts.org/images/products/400/845/201/1004/front_de.3.200.jpg","added":"2026-11-10"},
          {"id":"m2def","name":"Butter","date":"2026-12-05","barcode":"","image":"","added":"2026-11-12"},
          {"name":"kaputt"}
        ]}
    """.trimIndent()

    @Test
    fun importsBackupOfTheOldApp() {
        val items = PantryJson.parseBackup(oldBackup, emptySet())
        assertEquals(2, items.size)
        val yoghurt = items[0]
        assertEquals("m1abc", yoghurt.id)
        assertEquals(LocalDate.of(2026, 11, 21), yoghurt.date)
        assertEquals("4008452011004", yoghurt.barcode)
        assertTrue(yoghurt.image.startsWith("https://images.openfoodfacts.org/"))
        assertEquals(LocalDate.of(2026, 11, 10), yoghurt.added)
    }

    @Test
    fun existingIdsAreReplaced() {
        val items = PantryJson.parseBackup(oldBackup, setOf("m1abc"))
        assertNotEquals("m1abc", items[0].id)
        assertEquals("m2def", items[1].id)
    }

    @Test
    fun plainListAndRoundTrip() {
        val items = PantryJson.parseBackup("""[{"name":"Käse","date":"2026-12-24"},{"name":"kaputt"}]""", emptySet())
        assertEquals(1, items.size)
        assertEquals("Käse", items[0].name)
        val again = PantryJson.parseBackup(PantryJson.exportBackup(items), emptySet())
        assertEquals(items, again)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidBackupThrows() {
        PantryJson.parseBackup("""{"foo":1}""", emptySet())
    }

    @Test(expected = IllegalArgumentException::class)
    fun noJsonThrows() {
        PantryJson.parseBackup("Milch, Brot", emptySet())
    }

    @Test
    fun entriesAreCleaned() {
        val item = PantryJson.normalize(
            JSONObject()
                .put("id", 7).put("name", "x".repeat(300)).put("date", "2027-01-01")
                .put("barcode", " 4006381333931<").put("image", "http://unsicher/x.png"),
        )!!
        assertEquals("7", item.id)
        assertEquals(120, item.name.length)
        assertEquals("4006381333931", item.barcode)
        assertEquals("", item.image)
        assertNull(PantryJson.normalize(JSONObject().put("name", "ohne Datum")))
        assertNull(PantryJson.normalize(JSONObject().put("name", "x").put("date", "2027-13-01")))
        assertNull(PantryJson.normalize(JSONObject().put("name", "x").put("date", "2027-02-30")))
        assertEquals("Produkt", PantryJson.normalize(JSONObject().put("date", "2027-01-01"))!!.name)
    }

    @Test
    fun recognisesBackupsInSharedText() {
        assertTrue(PantryJson.looksLikeBackup(oldBackup))
        assertTrue(PantryJson.looksLikeBackup("""[{"name":"Käse","date":"2026-12-24"}]"""))
        assertFalse(PantryJson.looksLikeBackup("Milch\nBrot\n6 Eier"))
        assertFalse(PantryJson.looksLikeBackup("Für Lasagne brauchst du: Hackfleisch, Tomaten"))
    }
}
