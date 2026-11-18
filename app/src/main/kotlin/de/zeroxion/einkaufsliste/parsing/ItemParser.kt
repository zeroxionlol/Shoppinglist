package de.zeroxion.einkaufsliste.parsing

import java.util.Locale

/** A product as understood from free text, e.g. "2 Liter Milch" -> (Milch, "2 l"). */
data class ParsedItem(val name: String, val quantity: String?)

/**
 * Turns German free text into shopping list entries.
 *
 * Handles three kinds of input:
 *  - spoken sentences: "Setz bitte zwei Liter Milch, Brot und 6 Eier auf die Einkaufsliste"
 *  - lists shared from Gemini or other apps (markdown bullets, headings, numbering)
 *  - a single typed product: "500 g Hackfleisch"
 */
object ItemParser {

    private val de = Locale.GERMAN

    private val numberWords = mapOf(
        "ein" to "1", "eine" to "1", "einen" to "1", "einem" to "1", "einer" to "1", "eins" to "1",
        "zwei" to "2", "drei" to "3", "vier" to "4", "fünf" to "5", "sechs" to "6", "sieben" to "7",
        "acht" to "8", "neun" to "9", "zehn" to "10", "elf" to "11", "zwölf" to "12",
        "zwanzig" to "20", "dreißig" to "30", "halbe" to "½", "halbes" to "½", "halben" to "½",
        "anderthalb" to "1½", "eineinhalb" to "1½",
    )

    private val units = mapOf(
        "kg" to "kg", "kilo" to "kg", "kilogramm" to "kg",
        "g" to "g", "gr" to "g", "gramm" to "g",
        "l" to "l", "liter" to "l", "ml" to "ml", "milliliter" to "ml",
        "stück" to "Stück", "stk" to "Stück",
        "packung" to "Packung", "packungen" to "Packungen", "pack" to "Pack", "packs" to "Pack",
        "päckchen" to "Päckchen", "paket" to "Paket", "pakete" to "Pakete",
        "flasche" to "Flasche", "flaschen" to "Flaschen",
        "dose" to "Dose", "dosen" to "Dosen", "becher" to "Becher",
        "glas" to "Glas", "gläser" to "Gläser", "tüte" to "Tüte", "tüten" to "Tüten",
        "beutel" to "Beutel", "bund" to "Bund", "netz" to "Netz",
        "kiste" to "Kiste", "kisten" to "Kisten", "kasten" to "Kasten",
        "rolle" to "Rolle", "rollen" to "Rollen", "tafel" to "Tafel", "tafeln" to "Tafeln",
        "scheibe" to "Scheibe", "scheiben" to "Scheiben", "stange" to "Stange", "stangen" to "Stangen",
        "karton" to "Karton", "kartons" to "Kartons", "schale" to "Schale", "schalen" to "Schalen",
        "kopf" to "Kopf", "köpfe" to "Köpfe", "laib" to "Laib", "dutzend" to "Dutzend",
    )

    private val bullet = Regex("^\\s*(?:[-*•·–—>]+|\\d+[.)]|\\[[ xX]?])\\s*")
    private val markdown = Regex("[*_`#]+")
    private val parentheses = Regex("\\s*\\([^)]*\\)")
    private val separators = Regex("\\s*(?:[,;]|\\bund\\b|\\bsowie\\b|\\bplus\\b|\\baußerdem\\b|\\bdazu\\b)\\s*", RegexOption.IGNORE_CASE)

    private val leadingCommand = Regex(
        "^(?:hey\\s+\\w+[,!]?\\s*)?(?:bitte\\s+)?" +
            "(?:(?:setz|setze|pack|packe|füg|füge|schreib|schreibe|trag|trage|notier|notiere|nimm|leg|lege)\\s+(?:mir\\s+|uns\\s+)?(?:bitte\\s+)?(?:noch\\s+)?" +
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

    /**
     * Parses free text into products.
     * @param fromSpeech speech recognition rarely adds commas ("Milch Brot und Käse"), so
     *   runs of capitalised nouns are split into separate products.
     */
    fun parse(text: String, fromSpeech: Boolean = false): List<ParsedItem> {
        val lines = text.replace("\r", "").split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        val multiLine = lines.size > 1
        val result = mutableListOf<ParsedItem>()
        for (rawLine in lines) {
            var line = rawLine.replace(bullet, "").replace(markdown, "").trim()
            if (line.isEmpty()) continue
            if (multiLine) {
                // Headings like "Zutaten:" or "Gemüse:" introduce a group.
                if (line.endsWith(":")) continue
                // "Gemüse: Zwiebeln, Karotten" -> keep the part after the label.
                val colon = line.indexOf(':')
                if (colon in 1..30) line = line.substring(colon + 1).trim()
                // Skip chatty sentences such as "Hier ist deine Liste!" or "Guten Appetit!".
                if (line.endsWith("!") || line.endsWith("?")) continue
                if (line.split(' ').size > 8) continue
            }
            result += parseSentence(line, fromSpeech)
        }
        return mergeDuplicates(result)
    }

    private fun parseSentence(sentence: String, fromSpeech: Boolean): List<ParsedItem> {
        var s = sentence.replace(parentheses, " ").trim().trimEnd('.', '!', '?')
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
        val allNouns = words.all { w -> w.first().isUpperCase() && numberWords[w.lowercase(de)] == null && !units.containsKey(w.lowercase(de)) }
        return if (allNouns) words else listOf(fragment)
    }

    /** "2 Liter Milch" -> (Milch, "2 l"), "ein Brot" -> (Brot, null), "500g Hack" -> (Hack, "500 g"). */
    fun parseFragment(fragment: String): ParsedItem? {
        var rest = fragment.replace(Regex("\\s+"), " ").trim()
        if (rest.isEmpty()) return null

        // Strip trailing command words again for each fragment ("... und Eier drauf").
        rest = rest.replace(trailingCommand, "").trim()
        if (rest.isEmpty()) return null

        var amount: String? = null
        var unit: String? = null

        // Number at the start, possibly glued to the unit ("500g").
        val numberMatch = Regex("^(\\d+(?:[.,]\\d+)?|½)\\s*([\\p{L}]+\\.?)?\\s*(.*)$").find(rest)
        if (numberMatch != null) {
            amount = numberMatch.groupValues[1].replace('.', ',')
            val maybeUnit = numberMatch.groupValues[2].trimEnd('.').lowercase(de)
            val tail = numberMatch.groupValues[3]
            if (maybeUnit.isNotEmpty() && units.containsKey(maybeUnit) && tail.isNotBlank()) {
                unit = units[maybeUnit]
                rest = tail
            } else {
                rest = (numberMatch.groupValues[2] + " " + tail).trim()
            }
        } else {
            val words = rest.split(' ')
            val first = words.first().lowercase(de)
            val number = numberWords[first]
            if (number != null && words.size > 1) {
                var idx = 1
                val second = words[1].lowercase(de)
                if (first == "ein" && second == "paar") {
                    amount = "ein paar"; idx = 2
                } else if (first == "ein" && (second == "halbes" || second == "halber" || second == "halbe")) {
                    amount = "½"; idx = 2
                } else {
                    amount = number
                }
                val unitWord = words.getOrNull(idx)?.lowercase(de)?.trimEnd('.')
                if (unitWord != null && units.containsKey(unitWord) && words.size > idx + 1) {
                    unit = units[unitWord]
                    idx++
                }
                rest = words.drop(idx).joinToString(" ")
            } else if (units.containsKey(first.trimEnd('.')) && words.size > 1 && first.length > 2) {
                // "Packung Eier" without a number
                unit = units[first.trimEnd('.')]
                rest = words.drop(1).joinToString(" ")
            }
        }

        rest = rest.removePrefix("von ").removePrefix("an ").trim()
        rest = rest.replace(Regex("^(?:der|die|das|den|dem|etwas|bisschen|ein bisschen)\\s+", RegexOption.IGNORE_CASE), "")
        if (rest.isBlank()) return null
        if (rest.length > 60) rest = rest.take(60).trim()

        // A plain "1" without unit before a product ("ein Brot") is noise.
        val quantity = when {
            unit != null -> listOfNotNull(amount ?: "1", unit).joinToString(" ")
            amount == null -> null
            amount == "1" -> null
            else -> amount
        }
        return ParsedItem(name = capitalize(rest), quantity = quantity)
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
