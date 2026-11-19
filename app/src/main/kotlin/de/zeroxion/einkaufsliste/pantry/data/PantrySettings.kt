package de.zeroxion.einkaufsliste.pantry.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import de.zeroxion.einkaufsliste.pantry.logic.PriceRegion
import de.zeroxion.einkaufsliste.pantry.logic.safeImageUrl
import org.json.JSONObject

/** Reminder settings, price region and remembered barcodes. */
class PantrySettings private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("vorrat", Context.MODE_PRIVATE)

    var hour: Int
        get() = prefs.getInt("hour", 9).coerceIn(0, 23)
        set(value) = prefs.edit { putInt("hour", value.coerceIn(0, 23)) }

    var minute: Int
        get() = prefs.getInt("minute", 0).coerceIn(0, 59)
        set(value) = prefs.edit { putInt("minute", value.coerceIn(0, 59)) }

    /** Remind this many days before expiry (1 to [MAX_LEAD_DAYS]). */
    var leadDays: Int
        get() = prefs.getInt("leadDays", 1).coerceIn(1, MAX_LEAD_DAYS)
        set(value) = prefs.edit { putInt("leadDays", value.coerceIn(1, MAX_LEAD_DAYS)) }

    var region: PriceRegion
        get() = PriceRegion.fromName(prefs.getString("region", null))
        set(value) = prefs.edit { putString("region", value.name) }

    /** Keys "id@date" of products a reminder was already shown for. */
    var notified: Set<String>
        get() = prefs.getStringSet("notified", emptySet())?.toSet() ?: emptySet()
        set(value) = prefs.edit { putStringSet("notified", value.toSet()) }

    /** True once the app has asked for the notification permission. */
    var askedForNotifications: Boolean
        get() = prefs.getBoolean("askedNotifications", false)
        set(value) = prefs.edit { putBoolean("askedNotifications", value) }

    data class KnownProduct(val name: String, val image: String)

    /** Own names per barcode, so a product is recognised immediately the next time. */
    fun knownProduct(code: String): KnownProduct? {
        if (code.isBlank()) return null
        val entry = known().optJSONObject(code) ?: return null
        val name = entry.optString("name").ifBlank { return null }
        return KnownProduct(name, safeImageUrl(entry.optString("image")))
    }

    fun rememberProduct(code: String, name: String, image: String) {
        if (code.isBlank() || name.isBlank()) return
        val all = known()
        all.put(code, JSONObject().put("name", name).put("image", safeImageUrl(image)))
        prefs.edit { putString("known", all.toString()) }
    }

    private fun known(): JSONObject = try {
        JSONObject(prefs.getString("known", "{}") ?: "{}")
    } catch (e: Exception) {
        JSONObject()
    }

    companion object {
        const val MAX_LEAD_DAYS = 3

        @Volatile
        private var instance: PantrySettings? = null

        fun get(context: Context): PantrySettings =
            instance ?: synchronized(this) { instance ?: PantrySettings(context).also { instance = it } }
    }
}
