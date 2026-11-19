package de.zeroxion.einkaufsliste.parsing

import de.zeroxion.einkaufsliste.data.Quantity
import java.util.Locale

/** A product as understood from free text, e.g. "2 Liter Milch" -> (Milch, 2.0, "l"). */
data class ParsedItem(val name: String, val amount: Double? = null, val unit: String? = null)

/**
 * Turns German free text into shopping list entries.
 *
 * Handles three kinds of input:
 *  - spoken or typed sentences: "Setz bitte zwei Liter Milch, Brot und 6 Eier auf die Einkaufsliste"
 *  - pasted multi-line text (WhatsApp, notes, Gemini): every line becomes exactly one product
 *  - a single typed product: "500 g Hackfleisch"
 */
object ItemParser {

    private val de = Locale.GERMAN

    private val numberWords = mapOf(
        "ein" to 1.0, "eine" to 1.0, "einen" to 1.0, "einem" to 1.0, "einer" to 1.0, "eins" to 1.0,
        "zwei" to 2.0, "drei" to 3.0, "vier" to 4.0, "fünf" to 5.0, "sechs" to 6.0, "sieben" to 7.0,
        "acht" to 8.0, "neun" to 9.0, "zehn" to 10.0, "elf" to 11.0, "zwölf" to 12.0,
        "zwanzig" to 20.0, "dreißig" to 30.0, "halbe" to 0.5, "halbes" to 0.5, "halben" to 0.5,
        "anderthalb" to 1.5, "eineinhalb" to 1.5,
    )

    private val bullet = Regex("^\\s*(?:[-*•·–—>✅☐☑✔️]+|\\d+[.)]|\\[[ xX]?])\\s*")
    private val markdown = Regex("[*_`#]+")
    private val parentheses = Regex("\\s*\\([^)]*\\)")

    /** WhatsApp copy prefix: "[19.11.26, 01:20] Max: Milch" */
    private val chatPrefix = Regex("^\\[[^\\]]{4,30}]\\s*[^:]{1,40}:\\s+")

    private val separators = Regex(
        "\\s*(?:[,;]|\\bund\\b|\\bsowie\\b|\\bplus\\b|\\baußerdem\\b|\\bdazu\\b)\\s*",
        RegexOption.IGNORE_CASE,
    )

    private val leadingCommand = Regex(
        "^(?:hey\\s+\\w+[,!]?\\s*)?(?:bitte\\s+)?" +
            "(?:(?:setz|setze|packe|füg|füge|schreib|schreibe|trag|trage|notier|notiere|nimm|leg|lege)\\s+(?:mir\\s+|uns\\s+)?(?:bitte\\s+)?(?:noch\\s+)?" +
            "|(?:ich|wir)\\s+(?:brauche|brauchen|benötige|benötigen)\\s+(?:noch\\s+)?(?:dringend\\s+)?" +
            "|(?:wir\\s+)?(?:kauf|kaufe|kaufen)\\s+(?:noch\\s+)?" +
            "|noch\\s+)",
        RegexOption.IGNORE_CASE,
    )
    private val trailingCommand = Regex(
        "\\s*(?:bitte\\s+)?(?:(?:auf|zu|in|zur)\\s+)?(?:die\\s+|meine\\s+|unsere\\s+|der\\s+|meiner\\s+|unserer\\s+)?" +
            "(?:einkaufsliste|liste)\\s*(?:hinzu(?:fügen)?|drauf|dazu|ein(?:tragen)?|setzen|schreiben)?\\s*(?:bitte)?\\s*$" +
            "|\\s+(?:hinzu|drauf|dazu)\\s*(?:bitte)?\\s*$",
        RegexOption.IGNORE_CASE,
    )

    /** True if the text has more than one non-empty line (pasted list). */
    fun isMultiLine(text: String): Boolean =
        text.replace("\r", "").split('\n').count { it.isNotBlank() } > 1

    /**
     * Parses free text into products.
     * @param fromSpeech speech recognition rarely adds commas ("Milch Brot und Käse"), so
     *   runs of capitalised nouns are split into separate products.
     */
    fun parse(text: String, fromSpeech: Boolean = false): List<ParsedItem> {
        val lines = text.replace("\r", "").split('\n')
            .map { it.replace(chatPrefix, "").trim() }
            .filter { it.isNotEmpty() }
        val result = if (lines.size > 1) {
            lines.flatMap { parseListLine(it) }
        } else {
            lines.flatMap { parseSentence(clean(it), fromSpeech) }
        }
        return mergeDuplicates(result)
    }

    private fun clean(line: String): String =
        line.replace(bullet, "").replace(markdown, "").replace(parentheses, " ").trim()

    /**
     * One line of a pasted list = one product. "Salz und Pfeffer" stays one entry.
     * Only Gemini-style group lines ("Gemüse: Zwiebeln, Karotten") are split.
     */
    private fun parseListLine(raw: String): List<ParsedItem> {
        var line = clean(raw)
        if (line.isEmpty()) return emptyList()
        // Headings like "Zutaten:" or "Gemüse:" only introduce a group.
        if (line.endsWith(":")) return emptyList()
        val words = line.split(' ').size
        // Chatty sentences such as "Hier ist deine Liste!" or "Kannst du das mitbringen?".
        if ((line.endsWith("!") || line.endsWith("?")) && words >= 3) return emptyList()
        if (words > 8) return emptyList()

        val colon = line.indexOf(':')
        if (colon in 1..30 && colon < line.length - 1) {
            val after = line.substring(colon + 1).trim()
            if (after.contains(',') || after.contains(';')) {
                return after.split(',', ';').mapNotNull { parseFragment(it.trim().trimEnd('.')) }
            }
            line = after
        }
        return listOfNotNull(parseFragment(line.trim().trimEnd('.', '!', '?')))
    }

    private fun parseSentence(sentence: String, fromSpeech: Boolean): List<ParsedItem> {
        var s = sentence.trim().trimEnd('.', '!', '?')
        s = s.replace(leadingCommand, "").trim()
        s = s.replace(trailingCommand, "").trim()
        if (s.isEmpty()) return emptyList()
        return s.split(separators)
            .map { it.trim().trim('.', '!', '?', '"', '„', '“', '\'') }
            .filter { it.isNotEmpty() }
            .flatMap { if (fromSpeech) splitSpokenNouns(it) else listOf(it) }
            .mapNotNull { parseFragment(it) }
    }

    /** "Milch Brot Käse" -> three products; "2 Liter Milch" or "frische Milch" stay together. */
    private fun splitSpokenNouns(fragment: String): List<String> {
        val words = fragment.split(' ').filter { it.isNotBlank() }
        if (words.size < 2) return listOf(fragment)
        val allNouns = words.all { w ->
            w.first().isUpperCase() && numberWords[w.lowercase(de)] == null && Quantity.canonicalUnit(w) == null
        }
        return if (allNouns) words else listOf(fragment)
    }

    /** "2 Liter Milch" -> (Milch, 2, l), "ein Brot" -> (Brot), "500g Hack" -> (Hack, 500, g), "6 Eier" -> (Eier, 6). */
    fun parseFragment(fragment: String): ParsedItem? {
        var rest = fragment.replace(Regex("\\s+"), " ").trim()
        if (rest.isEmpty()) return null

        // Strip trailing command words again for each fragment ("... und Eier drauf").
        rest = rest.replace(trailingCommand, "").trim()
        if (rest.isEmpty()) return null

        var amount: Double? = null
        var unit: String? = null

        // Number at the start, possibly glued to the unit ("500g").
        val numberMatch = Regex("^(\\d+(?:[.,]\\d+)?|½)\\s*(?:x|×)?\\s*([\\p{L}]+\\.?)?\\s*(.*)$").find(rest)
        if (numberMatch != null) {
            amount = Quantity.parseNumber(numberMatch.groupValues[1])
            val maybeUnit = numberMatch.groupValues[2]
            val tail = numberMatch.groupValues[3]
            val canonical = if (maybeUnit.isNotEmpty()) Quantity.canonicalUnit(maybeUnit) else null
            if (canonical != null && tail.isNotBlank()) {
                unit = canonical
                rest = tail
            } else {
                rest = (maybeUnit + " " + tail).trim()
            }
        } else {
            val words = rest.split(' ')
            val first = words.first().lowercase(de)
            val number = numberWords[first]
            if (number != null && words.size > 1) {
                var idx = 1
                val second = words[1].lowercase(de)
                if (first == "ein" && second == "paar") {
                    amount = null; idx = 2
                } else if (first == "ein" && (second == "halbes" || second == "halber" || second == "halbe")) {
                    amount = 0.5; idx = 2
                } else {
                    amount = number
                }
                val unitWord = words.getOrNull(idx)
                val canonical = unitWord?.let { Quantity.canonicalUnit(it) }
                if (canonical != null && words.size > idx + 1) {
                    unit = canonical
                    idx++
                }
                rest = words.drop(idx).joinToString(" ")
            } else if (words.size > 1 && first.length > 2 && Quantity.canonicalUnit(first) != null &&
                first !in setOf("pack", "packs")
            ) {
                // "Packung Eier" without a number
                unit = Quantity.canonicalUnit(first)
                amount = 1.0
                rest = words.drop(1).joinToString(" ")
            }
        }

        // Trailing amount: "Eier x6", "Eier 6x", "Milch (2)"
        if (amount == null) {
            Regex("^(.*?)\\s+(?:x|×)\\s*(\\d+)$|^(.*?)\\s+(\\d+)\\s*(?:x|×|stk\\.?|stück)$", RegexOption.IGNORE_CASE)
                .find(rest)?.let { m ->
                    val name = m.groupValues[1].ifEmpty { m.groupValues[3] }
                    val num = m.groupValues[2].ifEmpty { m.groupValues[4] }
                    if (name.isNotBlank()) {
                        rest = name
                        amount = num.toDoubleOrNull()
                    }
                }
        }

        rest = rest.removePrefix("von ").removePrefix("an ").trim()
        rest = rest.replace(Regex("^(?:der|die|das|den|dem|etwas|bisschen|ein bisschen)\\s+", RegexOption.IGNORE_CASE), "")
        rest = rest.trim().trimEnd(',', ';', '-', ':')
        if (rest.isBlank()) return null
        if (rest.length > 60) rest = rest.take(60).trim()

        val finalAmount = amount?.takeIf { it > 0 }
        return ParsedItem(name = capitalize(rest), amount = finalAmount, unit = if (finalAmount == null && unit == null) null else unit)
    }

    private fun capitalize(text: String): String =
        text.replaceFirstChar { if (it.isLowerCase()) it.titlecase(de) else it.toString() }

    private fun mergeDuplicates(items: List<ParsedItem>): List<ParsedItem> {
        val seen = LinkedHashMap<String, ParsedItem>()
        for (item in items) {
            val key = item.name.lowercase(de)
            if (!seen.containsKey(key)) seen[key] = item
        }
        return seen.values.toList()
    }
}
