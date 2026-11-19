package de.zeroxion.einkaufsliste.pantry.logic

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Decides which products a reminder is about. Pure logic, tested on the JVM.
 *
 * Rule: every product is reported once as soon as it is at most [leadDays] days before its MHD.
 * This is remembered per product *and* date (`id@date`) – changing the date means a new reminder.
 */
class ReminderPlan private constructor(
    /** All due products (new and already reported), most urgent first. */
    val due: List<Due>,
    /** Updated memory: keys of all reported products that have not expired yet. */
    val notified: Set<String>,
    /** True if at least one product is due for the first time – only then a notification is shown. */
    val hasNew: Boolean,
) {
    data class Item(val id: String, val name: String, val date: LocalDate)

    data class Due(val name: String, val date: LocalDate, val days: Int) {
        val whenText: String
            get() = when (days) {
                0 -> "heute"
                1 -> "morgen"
                else -> "in $days Tagen"
            }
    }

    val title: String
        get() = if (due.size == 1) "${due[0].name} läuft ${due[0].whenText} ab" else "${due.size} Produkte laufen bald ab"

    /** One-line summary. */
    val text: String
        get() = if (due.size == 1) {
            "Mindestens haltbar bis " + due[0].date.format(GERMAN_DATE)
        } else {
            due.joinToString(", ") { it.name }
        }

    /** Expanded notification text: one product per line. */
    val bigText: String
        get() = if (due.size == 1) text else due.joinToString("\n") { "${it.name} – ${it.whenText}" }

    companion object {
        private val GERMAN_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy")

        fun key(id: String, date: LocalDate) = "$id@$date"

        /**
         * @param includeToday also report products that expire today for the first time (daily check);
         *                     false right after adding – the user has just seen it themselves.
         */
        fun build(
            items: List<Item>,
            notifiedBefore: Set<String>,
            leadDays: Int,
            includeToday: Boolean,
            today: LocalDate,
        ): ReminderPlan {
            val due = mutableListOf<Due>()
            val notified = mutableSetOf<String>()
            var hasNew = false
            for (item in items) {
                val days = ChronoUnit.DAYS.between(today, item.date)
                if (days < 0) continue // expired: not reported any more, memory runs out
                val key = key(item.id, item.date)
                val already = key in notifiedBefore
                if (already) notified += key
                if (days > leadDays) continue
                if (!already) {
                    if (days == 0L && !includeToday) continue
                    hasNew = true
                    notified += key
                }
                due += Due(item.name.trim().ifEmpty { "Produkt" }, item.date, days.toInt())
            }
            due.sortWith(compareBy<Due> { it.days }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            return ReminderPlan(due, notified, hasNew)
        }
    }
}
