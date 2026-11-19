package de.zeroxion.einkaufsliste.pantry.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MhdDateParserTest {
    private val today = LocalDate.of(2026, 11, 13)
    private fun iso(text: String) = MhdDateParser.parse(text, today)?.date?.toString()

    @Test
    fun commonFormats() {
        assertEquals("2027-03-12", iso("MHD 12.03.2027"))
        assertEquals("2027-03-12", iso("mindestens haltbar bis: 12.03.27"))
        assertEquals("2027-03-12", iso("EXP 2027-03-12"))
        assertEquals("2027-03-12", iso("12/03/2027"))
        assertEquals("2027-03-12", iso("12 03 27"))
        assertEquals("2027-03-01", iso("1.3.27"))
    }

    @Test
    fun monthAndYearMeansLastDay() {
        assertEquals("2027-03-31", iso("03/2027"))
        assertEquals("2028-02-29", iso("Ende 02.2028"))
        assertEquals(null, iso("13/2027"))
    }

    @Test
    fun withoutSeparatorsOnlyAsFallback() {
        assertEquals("2027-04-15", iso("L1234 150427"))
        assertEquals("2027-03-12", iso("150427 12.03.2027"))
    }

    @Test
    fun typicalOcrErrorsAreFixed() {
        assertEquals("2027-03-12", iso("12,O3.2027"))
        assertEquals("2026-12-10", iso("1O.l2.26"))
        assertEquals("2027-06-30", iso("Bis 3O.06.2O27"))
        assertEquals("2027-03-12", iso("12 . 03 . 2027"))
        assertEquals("2027-03-12", iso("12.03.\n2027"))
    }

    @Test
    fun implausibleDatesAreRejected() {
        assertEquals(null, iso("31.02.2027"))
        assertEquals(null, iso("12.03.2019"))
        assertEquals(null, iso("12.03.2040"))
        assertEquals(null, iso("123456789"))
        assertEquals(null, iso(""))
        assertEquals(null, iso("Chargennummer L 4711"))
    }

    @Test
    fun timeOrBatchNextToDateDoesNotDisturb() {
        assertEquals("2026-11-12", iso("12-11-2026 14:32"))
        assertEquals("2027-03-12", iso("L 2603 12.03.27"))
        assertEquals("2027-05-31", iso("14:32 05.2027"))
    }

    @Test
    fun laterDateWins() {
        assertEquals("2027-11-01", iso("hergestellt 01.11.2026 haltbar bis 01.11.2027"))
        assertEquals("2027-11-01", iso("01.11.2027 / 01.11.2026"))
    }

    @Test
    fun wordsAroundTheDateFromTextRecognition() {
        assertEquals("2027-01-24", iso("Mindestens haltbar bis: 24.01.2027 L4711 08:15"))
        assertEquals("2027-01-24", iso("MHD 24.01.27 LOT 2611"))
        assertEquals("2026-12-05", iso("zu verbrauchen bis 05.12.26"))
    }

    @Test
    fun recentlyExpiredStaysReadable() {
        assertEquals("2026-11-10", iso("10.11.2026"))
    }

    @Test
    fun confirmNeedsTwoEqualReadings() {
        val h = mutableListOf<LocalDate?>()
        val a = DateHit(LocalDate.of(2027, 3, 12), "")
        assertFalse(MhdDateParser.confirm(h, a))
        assertFalse(MhdDateParser.confirm(h, DateHit(LocalDate.of(2027, 8, 12), "")))
        assertFalse(MhdDateParser.confirm(h, null))
        assertTrue(MhdDateParser.confirm(h, a))
    }

    @Test
    fun confirmForgetsOldReadings() {
        val h = mutableListOf<LocalDate?>()
        val a = DateHit(LocalDate.of(2027, 3, 12), "")
        MhdDateParser.confirm(h, a)
        repeat(5) { MhdDateParser.confirm(h, null) }
        assertFalse(MhdDateParser.confirm(h, a))
        assertEquals(5, h.size)
    }
}
