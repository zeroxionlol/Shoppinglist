package de.zeroxion.einkaufsliste.data

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.round

/**
 * Amount + unit of a product, e.g. 2 × (no unit), 500 g, 1,5 l, 3 Packungen.
 * Units are stored in their singular form ("Packung") and pluralised for display.
 */
object Quantity {

    private val de = Locale.GERMAN

    /** Spoken/written unit -> canonical singular unit. */
    val units: Map<String, String> = mapOf(
        "kg" to "kg", "kilo" to "kg", "kilogramm" to "kg",
        "g" to "g", "gr" to "g", "gramm" to "g",
        "l" to "l", "liter" to "l", "ml" to "ml", "milliliter" to "ml",
        "stück" to "Stück", "stk" to "Stück",
        "packung" to "Packung", "packungen" to "Packung", "pckg" to "Packung", "pkg" to "Packung",
        "pack" to "Pack", "packs" to "Pack", "päckchen" to "Päckchen",
        "paket" to "Paket", "pakete" to "Paket",
        "flasche" to "Flasche", "flaschen" to "Flasche",
        "dose" to "Dose", "dosen" to "Dose", "becher" to "Becher",
        "glas" to "Glas", "gläser" to "Glas", "tüte" to "Tüte", "tüten" to "Tüte",
        "beutel" to "Beutel", "bund" to "Bund", "netz" to "Netz",
        "kiste" to "Kiste", "kisten" to "Kiste", "kasten" to "Kasten",
        "rolle" to "Rolle", "rollen" to "Rolle", "tafel" to "Tafel", "tafeln" to "Tafel",
        "scheibe" to "Scheibe", "scheiben" to "Scheibe", "stange" to "Stange", "stangen" to "Stange",
        "karton" to "Karton", "kartons" to "Karton", "schale" to "Schale", "schalen" to "Schale",
        "kopf" to "Kopf", "köpfe" to "Kopf", "laib" to "Laib", "dutzend" to "Dutzend",
    )

    private val plurals = mapOf(
        "Packung" to "Packungen", "Paket" to "Pakete", "Flasche" to "Flaschen", "Dose" to "Dosen",
        "Glas" to "Gläser", "Tüte" to "Tüten", "Kiste" to "Kisten", "Rolle" to "Rollen",
        "Tafel" to "Tafeln", "Scheibe" to "Scheiben", "Stange" to "Stangen", "Karton" to "Kartons",
        "Schale" to "Schalen", "Kopf" to "Köpfe",
    )

    fun canonicalUnit(word: String): String? = units[word.lowercase(de).trimEnd('.')]

    fun formatNumber(amount: Double): String =
        if (amount % 1.0 == 0.0) {
            amount.toLong().toString()
        } else {
            String.format(de, "%.2f", amount).trimEnd('0').trimEnd(',')
        }

    /** "500 g", "3 Packungen", "2" */
    fun format(amount: Double, unit: String?): String {
        val number = formatNumber(amount)
        if (unit.isNullOrBlank()) return number
        val shown = if (amount > 1.0) plurals[unit] ?: unit else unit
        return "$number $shown"
    }

    /** Parses "500 g", "2", "1,5 l", "3 Packungen". Returns null if no number was found. */
    fun parse(text: String): Pair<Double, String?>? {
        val t = text.trim()
        if (t.isEmpty()) return null
        val m = Regex("^(\\d+(?:[.,]\\d+)?|½)\\s*([\\p{L}.]+)?\\s*$").find(t) ?: return null
        val amount = parseNumber(m.groupValues[1]) ?: return null
        val unitWord = m.groupValues[2]
        val unit = if (unitWord.isBlank()) null else canonicalUnit(unitWord) ?: unitWord
        return amount to unit
    }

    fun parseNumber(text: String): Double? = when (text) {
        "½" -> 0.5
        "1½" -> 1.5
        else -> text.replace(',', '.').toDoubleOrNull()
    }

    private fun isFine(unit: String?) = unit == "g" || unit == "ml"
    private fun isBig(unit: String?) = unit == "kg" || unit == "l"

    fun increase(amount: Double, unit: String?): Double = when {
        isFine(unit) -> if (amount < 100) amount + 50 else floor(amount / 100) * 100 + 100
        isBig(unit) -> if (amount % 1.0 == 0.0) amount + 1 else ceil(amount * 2) / 2 + 0.5
        else -> floor(amount) + 1
    }.let(::clean)

    fun decrease(amount: Double, unit: String?): Double = when {
        isFine(unit) -> if (amount <= 100) max(50.0, amount - 50) else ceil(amount / 100) * 100 - 100
        isBig(unit) -> if (amount > 1 && amount % 1.0 == 0.0) amount - 1 else max(0.5, floor(amount * 2) / 2 - 0.5)
        else -> max(1.0, ceil(amount) - 1)
    }.let(::clean)

    fun canDecrease(amount: Double, unit: String?): Boolean = decrease(amount, unit) < amount

    private fun clean(value: Double): Double = round(value * 100) / 100
}
