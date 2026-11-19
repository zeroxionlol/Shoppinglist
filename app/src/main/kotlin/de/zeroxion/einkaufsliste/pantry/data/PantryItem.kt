package de.zeroxion.einkaufsliste.pantry.data

import de.zeroxion.einkaufsliste.pantry.logic.safeImageUrl
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.UUID

/** A product in the pantry with its best-before date (MHD). */
data class PantryItem(
    val id: String,
    val name: String,
    val date: LocalDate,
    val barcode: String = "",
    val image: String = "",
    val added: LocalDate = LocalDate.now(),
) {
    fun daysLeft(today: LocalDate): Int = ChronoUnit.DAYS.between(today, date).toInt()

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("date", date.toString())
        put("barcode", barcode)
        put("image", image)
        put("added", added.toString())
    }
}

/**
 * Reading, cleaning and backups. The backup format is the one of the former MHD-Tracker app
 * (`{"app":"mhd-tracker","v":1,"items":[…]}`), so old backups can be imported directly.
 */
object PantryJson {
    const val BACKUP_SUBJECT = "Vorrat-Sicherung"

    fun newId(): String = UUID.randomUUID().toString()

    private fun isoDate(value: String?): LocalDate? = try {
        if (value != null && Regex("""^\d{4}-\d{2}-\d{2}$""").matches(value)) LocalDate.parse(value) else null
    } catch (e: DateTimeParseException) {
        null
    }

    /** Brings an entry (from storage or an import) into shape; null if unusable. */
    fun normalize(raw: JSONObject?): PantryItem? {
        if (raw == null) return null
        val date = isoDate(raw.optString("date")) ?: return null
        val id = raw.optString("id").trim().ifEmpty { newId() }
        return PantryItem(
            id = id.take(64),
            name = raw.optString("name").trim().ifEmpty { "Produkt" }.take(120),
            date = date,
            barcode = raw.optString("barcode").replace(Regex("[^0-9A-Za-z-]"), "").take(32),
            image = safeImageUrl(raw.optString("image")),
            added = isoDate(raw.optString("added")) ?: LocalDate.now(),
        )
    }

    fun parseList(text: String): List<PantryItem> {
        val array = JSONArray(text)
        return (0 until array.length()).mapNotNull { normalize(array.optJSONObject(it)) }
    }

    fun toJsonArray(items: List<PantryItem>): String {
        val array = JSONArray()
        items.forEach { array.put(it.toJson()) }
        return array.toString()
    }

    fun exportBackup(items: List<PantryItem>): String {
        val array = JSONArray()
        items.forEach { array.put(it.toJson()) }
        return JSONObject().put("app", "mhd-tracker").put("v", 1).put("items", array).toString()
    }

    /** Quick check whether shared text is a pantry backup and not a shopping list. */
    fun looksLikeBackup(text: String): Boolean {
        val t = text.trim()
        return (t.startsWith("{") && t.contains("\"mhd-tracker\"")) ||
            (t.startsWith("[") && t.endsWith("]") && t.contains("\"date\""))
    }

    /**
     * Reads a backup (export format or plain list) and returns the new entries.
     * IDs that already exist get a new one.
     * @throws IllegalArgumentException for invalid data
     */
    fun parseBackup(text: String, existingIds: Set<String>): List<PantryItem> {
        val t = text.trim()
        val array: JSONArray = try {
            if (t.startsWith("[")) JSONArray(t) else JSONObject(t).optJSONArray("items")
        } catch (e: Exception) {
            null
        } ?: throw IllegalArgumentException("Keine Produktliste gefunden")
        val ids = existingIds.toMutableSet()
        val out = mutableListOf<PantryItem>()
        for (i in 0 until array.length()) {
            var item = normalize(array.optJSONObject(i)) ?: continue
            if (item.id in ids) item = item.copy(id = newId())
            ids += item.id
            out += item
        }
        return out
    }
}
