package de.zeroxion.einkaufsliste.pantry.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReminderPlanTest {
    private val today = LocalDate.of(2026, 11, 13)
    private fun item(id: String, name: String, inDays: Long) = ReminderPlan.Item(id, name, today.plusDays(inDays))

    @Test
    fun tomorrowIsReportedOnce() {
        val items = listOf(item("a", "Milch", 1), item("b", "Käse", 5))
        val first = ReminderPlan.build(items, emptySet(), 1, true, today)
        assertTrue(first.hasNew)
        assertEquals(1, first.due.size)
        assertEquals("Milch läuft morgen ab", first.title)
        assertEquals("Mindestens haltbar bis 14.11.2026", first.text)

        val second = ReminderPlan.build(items, first.notified, 1, true, today)
        assertFalse(second.hasNew)
        assertEquals(first.notified, second.notified)
    }

    @Test
    fun changedDateMeansNewReminder() {
        val first = ReminderPlan.build(listOf(item("a", "Milch", 1)), emptySet(), 1, true, today)
        val changed = ReminderPlan.build(listOf(item("a", "Milch", 0)), first.notified, 1, true, today)
        assertTrue(changed.hasNew)
        assertEquals(setOf("a@2026-11-13"), changed.notified)
    }

    @Test
    fun todayOnlyInDailyCheck() {
        val items = listOf(item("a", "Joghurt", 0))
        assertFalse(ReminderPlan.build(items, emptySet(), 1, false, today).hasNew)
        assertTrue(ReminderPlan.build(items, emptySet(), 1, true, today).hasNew)
    }

    @Test
    fun leadDaysAndSorting() {
        val items = listOf(item("a", "Wurst", 3), item("b", "Brot", 2), item("c", "apfel", 2), item("d", "Sahne", 4), item("e", "alt", -1))
        val plan = ReminderPlan.build(items, emptySet(), 3, true, today)
        assertEquals(listOf("apfel", "Brot", "Wurst"), plan.due.map { it.name })
        assertEquals("3 Produkte laufen bald ab", plan.title)
        assertEquals("apfel – in 2 Tagen\nBrot – in 2 Tagen\nWurst – in 3 Tagen", plan.bigText)
        assertEquals(3, plan.notified.size)
    }

    @Test
    fun expiredProductsLeaveTheMemory() {
        val plan = ReminderPlan.build(listOf(item("a", "Milch", -1)), setOf("a@2026-11-12"), 1, true, today)
        assertTrue(plan.notified.isEmpty())
        assertTrue(plan.due.isEmpty())
    }

    @Test
    fun alreadyReportedStaysInListWhenSomethingNewComes() {
        val first = ReminderPlan.build(listOf(item("a", "Milch", 1)), emptySet(), 2, true, today)
        val plan = ReminderPlan.build(listOf(item("a", "Milch", 1), item("b", "Butter", 2)), first.notified, 2, true, today)
        assertTrue(plan.hasNew)
        assertEquals(listOf("Milch", "Butter"), plan.due.map { it.name })
    }
}
