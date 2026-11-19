package de.zeroxion.einkaufsliste.pantry.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PricesTest {
    private fun loc(brand: String?, name: String?, city: String?, lat: Double? = null, lon: Double? = null, cc: String = "DE") =
        PriceLocation("OSM", brand, name, city, cc, lat, lon)

    private val mh = PriceRegion.MUELHEIM

    @Test
    fun chainsAreRecognised() {
        assertEquals(Chain.EDEKA, Chain.of(loc("EDEKA", "EDEKA Paschmann", null)))
        assertEquals(Chain.EDEKA, Chain.of(loc(null, "E center Müller", null)))
        assertEquals(Chain.EDEKA, Chain.of(loc(null, "nah & gut Schmitz", null)))
        assertEquals(Chain.REWE, Chain.of(loc("REWE", "REWE City", null)))
        assertEquals(Chain.ALDI, Chain.of(loc("ALDI Süd", "ALDI SÜD", null)))
        assertEquals(Chain.LIDL, Chain.of(loc("Lidl", "Lidl", null)))
        assertNull(Chain.of(loc("Kaufland", "Kaufland", null)))
    }

    @Test
    fun regionMuelheim() {
        assertTrue(mh.contains(loc("", "", "Mülheim an der Ruhr", 51.43, 6.87)))
        assertTrue(mh.contains(loc("", "", "Muelheim")))
        assertTrue(mh.contains(loc("", "", null, 51.44, 6.89)))
        assertFalse(mh.contains(loc("", "", null)))
        assertFalse(mh.contains(loc("", "", "Essen", 51.44, 6.95)))
        assertFalse(mh.contains(loc("", "", "Oberhausen", 51.47, 6.86)))
        assertFalse(mh.contains(loc("", "", "Köln", 50.96, 7.01)))
        assertFalse(mh.contains(loc("", "", "Köln-Mülheim")))
        assertFalse(mh.contains(loc("", "", "Mülheim am Main")))
        assertFalse(mh.contains(loc("", "", "Mülheim-Kärlich")))
        assertTrue(PriceRegion.GERMANY.contains(loc("", "", "Hamburg", 53.5, 10.0)))
    }

    private val list = listOf(
        PriceReport(1.39, "2026-11-05", location = loc("EDEKA", "EDEKA", "Mülheim an der Ruhr", 51.41, 6.86)),
        PriceReport(0.99, "2026-11-04", true, 1.29, loc("REWE", "REWE", "Mülheim an der Ruhr", 51.42, 6.88)),
        PriceReport(1.29, "2026-10-20", location = loc("REWE", "REWE", "Mülheim an der Ruhr", 51.42, 6.88)),
        PriceReport(0.89, "2026-11-06", location = loc("ALDI Süd", "ALDI SÜD", "Oberhausen", 51.47, 6.86)),
        PriceReport(0.79, "2026-10-01", location = loc("Lidl", "Lidl", "Paris", 48.85, 2.35, "FR")),
        PriceReport(1.09, "2026-10-02", location = loc("Kaufland", "Kaufland", "Mülheim an der Ruhr", 51.43, 6.88)),
        PriceReport(null, "2026-10-02", location = loc("Lidl", "Lidl", "Mülheim an der Ruhr", 51.43, 6.88)),
    )

    @Test
    fun summaryForMuelheim() {
        val s = PriceSummary.summarize(list, mh, 1000)
        assertEquals(setOf(Chain.EDEKA, Chain.REWE), s.chains.keys)
        assertEquals(0.99, s.chains.getValue(Chain.REWE).price, 0.0)
        assertEquals(1.29, s.chains.getValue(Chain.REWE).old!!, 0.0)
        assertNull(s.chains.getValue(Chain.EDEKA).old)
        assertEquals(Chain.REWE, s.best)
        assertTrue(s.bestOverall)
        assertEquals(1.09, s.otherMin!!.price, 0.0)
        assertEquals(1, s.others)
        assertEquals(4, s.total)
    }

    @Test
    fun summaryForGermany() {
        val s = PriceSummary.summarize(list, PriceRegion.GERMANY, 0)
        assertEquals(0.89, s.chains.getValue(Chain.ALDI).price, 0.0)
        assertNull(s.chains[Chain.LIDL])
        assertEquals(Chain.ALDI, s.best)
    }

    @Test
    fun otherShopCheaperThanEveryChain() {
        val s = PriceSummary.summarize(
            listOf(list[0], PriceReport(0.5, "2026-11-01", location = loc("Kaufland", "Kaufland", "Mülheim an der Ruhr", 51.43, 6.88))),
            mh, 0,
        )
        assertEquals(Chain.EDEKA, s.best)
        assertFalse(s.bestOverall)
    }

    @Test
    fun noReports() {
        val s = PriceSummary.summarize(emptyList(), mh, 0)
        assertEquals(0, s.total)
        assertNull(s.best)
    }

    @Test
    fun labelsAndNames() {
        assertEquals("Weihenstephan Joghurt mild (500 g)", productLabel("Joghurt mild", null, null, "Weihenstephan, Molkerei", "500 g"))
        assertEquals("Nutella", productLabel(null, "Nutella", null, "Nutella", null))
        assertEquals("", productLabel(null, null, null, null, "1 l"))
        assertEquals("Weihenstephan Joghurt mild", shoppingName("Weihenstephan Joghurt mild (500 g)"))
        assertEquals("Milch", shoppingName("Milch"))
        assertEquals("1,39 €", euro(1.39))
    }

    @Test
    fun imageUrls() {
        val ok = "https://images.openfoodfacts.org/images/products/400/638/133/3931/front_de.4.200.jpg"
        assertEquals(ok, safeImageUrl(ok))
        assertEquals("", safeImageUrl("http://example.org/a.jpg"))
        assertEquals("", safeImageUrl("javascript:alert(1)"))
        assertEquals("", safeImageUrl("https://example.org/a b.jpg"))
        assertEquals("", safeImageUrl(null))
    }
}
