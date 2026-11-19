package de.zeroxion.einkaufsliste.pantry.logic

import java.time.LocalDate
import java.time.YearMonth

/** A best-before date found in text, plus the piece of text it came from. */
data class DateHit(val date: LocalDate, val raw: String)

/**
 * Finds a best-before date (MHD) in noisy OCR text.
 * Supported: TT.MM.JJJJ, TT.MM.JJ, JJJJ-MM-TT, TT MM JJ, MM/JJJJ (→ last day of month) and,
 * only as a last resort, TTMMJJ without separators. If several dates are found, the best
 * pattern wins; on a tie the later date wins (next to a production date, the MHD is always later).
 */
object MhdDateParser {

    private class Pattern(
        val score: Int,
        val regex: Regex,
        val fallback: Boolean = false,
        /** Returns year, month, day. Day 0 means "last day of the month". */
        val pick: (MatchResult) -> Triple<Int, Int, Int>,
    )

    private fun g(m: MatchResult, i: Int) = m.groupValues[i].toInt()
    private fun fixYear(y: Int) = if (y < 100) 2000 + y else y

    private val patterns = listOf(
        Pattern(3, Regex("""(?:^|[^\d])(\d{1,2})[./-](\d{1,2})[./-](\d{4}|\d{2})(?!\d)""")) {
            Triple(fixYear(g(it, 3)), g(it, 2), g(it, 1))
        },
        Pattern(3, Regex("""(\d{4})[./-](\d{1,2})[./-](\d{1,2})(?!\d)""")) {
            Triple(g(it, 1), g(it, 2), g(it, 3))
        },
        Pattern(2, Regex("""(?:^|[^\d])(\d{2}) (\d{2}) (\d{4}|\d{2})(?!\d)""")) {
            Triple(fixYear(g(it, 3)), g(it, 2), g(it, 1))
        },
        Pattern(1, Regex("""(?:^|[^\d./-])(\d{1,2})[./-](\d{4}|\d{2})(?![\d./-])""")) {
            Triple(fixYear(g(it, 2)), g(it, 1), 0)
        },
        Pattern(0, Regex("""(?:^|[^\d])(\d{2})(\d{2})(\d{4}|\d{2})(?!\d)"""), fallback = true) {
            Triple(fixYear(g(it, 3)), g(it, 2), g(it, 1))
        },
    )

    /** Fixes typical OCR mix-ups inside number sequences (O→0, l→1, comma→dot) and spaces around separators. */
    fun normalize(text: String): String = text
        .replace(Regex("""(\d)[,;·•_](?=[\dOoQ])""")) { it.groupValues[1] + "." }
        .replace(Regex("""(\d):(?=\d{2}[./-]\d)""")) { it.groupValues[1] + "." }
        .replace(Regex("""([\d./-])[OoQ](?=\d)""")) { it.groupValues[1] + "0" }
        .replace(Regex("""(\d)[OoQ](?=[./-])""")) { it.groupValues[1] + "0" }
        .replace(Regex("""([\d./-])[Il|!](?=\d)""")) { it.groupValues[1] + "1" }
        .replace(Regex("""(\d)[Il|!](?=[./-])""")) { it.groupValues[1] + "1" }
        .replace(Regex("[‐-―]"), "-")
        .replace(Regex("""\s*([./-])\s*""")) { it.groupValues[1] }

    /** Real calendar date, at most one year in the past and eight years ahead. */
    private fun plausible(y: Int, m: Int, d: Int, today: LocalDate): LocalDate? {
        if (m !in 1..12 || y !in 1900..2999) return null
        if (d < 1 || d > YearMonth.of(y, m).lengthOfMonth()) return null
        val date = LocalDate.of(y, m, d)
        return if (date.isAfter(today.minusDays(366)) && date.isBefore(today.plusDays(8L * 366))) date else null
    }

    fun parse(text: String?, today: LocalDate = LocalDate.now()): DateHit? {
        val t = normalize(text.orEmpty())
        data class Found(val hit: DateHit, val score: Int)
        val found = mutableListOf<Found>()
        for (p in patterns) {
            if (p.fallback && found.isNotEmpty()) continue
            var start = 0
            while (start <= t.length) {
                val m = p.regex.find(t, start) ?: break
                start = m.range.first + 1 // allow overlapping matches
                var (y, mo, d) = p.pick(m)
                if (d == 0) {
                    if (mo !in 1..12) continue
                    d = YearMonth.of(y, mo).lengthOfMonth()
                }
                val date = plausible(y, mo, d, today) ?: continue
                found += Found(DateHit(date, m.value.replace(Regex("""^[^\d]+"""), "").trim()), p.score)
            }
        }
        return found
            .sortedWith(compareByDescending<Found> { it.score }.thenByDescending { it.hit.date })
            .firstOrNull()?.hit
    }

    /**
     * A date only counts once it was read the same way several times – single misreadings
     * (8 instead of 3) drop out. [history] is updated in place.
     */
    fun confirm(history: MutableList<LocalDate?>, hit: DateHit?, needed: Int = 2, window: Int = 5): Boolean {
        history += hit?.date
        while (history.size > window) history.removeAt(0)
        return hit != null && history.count { it == hit.date } >= needed
    }
}
